package com.challenge.starwars.controllers;

import static com.challenge.starwars.support.TestPaths.V1;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.challenge.starwars.clients.SwapiClient;
import com.challenge.starwars.entities.User;
import com.challenge.starwars.repositories.UserRepository;
import com.challenge.starwars.support.IntegrationTest;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;

@IntegrationTest
class RegisterControllerTest {

    private static final String PASSWORD = "a-correct-horse-battery";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JsonMapper json;
    @Autowired
    private UserRepository users;
    @Autowired
    private PasswordEncoder encoder;

    @MockitoBean
    private SwapiClient swapiClient;

    private ResultActions send(String path, String username, String password) throws Exception {
        return mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("username", username, "password", password))));
    }

    private ResultActions register(String username, String password) throws Exception {
        return send(V1 + "/auth/register", username, password);
    }

    @Test
    void registersWithoutAuthenticationAndReturnsOnlyIdAndUsername() throws Exception {
        String body = register("reg-basic", PASSWORD)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.username").value("reg-basic"))
                .andReturn().getResponse().getContentAsString();

        assertThat(json.readTree(body).size()).isEqualTo(2);
        assertThat(body).doesNotContain(PASSWORD).doesNotContain("$2");
    }

    @Test
    void normalizesTheUsernameAndStoresOnlyABcryptHash() throws Exception {
        register("Reg-Mixed.Case_1", PASSWORD)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("reg-mixed.case_1"));

        User stored = users.findByUsername("reg-mixed.case_1").orElseThrow();
        assertThat(stored.isEnabled()).isTrue();
        assertThat(stored.getPasswordHash()).startsWith("$2").isNotEqualTo(PASSWORD);
        assertThat(encoder.matches(PASSWORD, stored.getPasswordHash())).isTrue();
    }

    @Test
    void aCaseVariantOfAnExistingUsernameIsAConflict() throws Exception {
        register("reg-Luke", PASSWORD).andExpect(status().isCreated());

        String body = register("REG-luke", PASSWORD)
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Username is not available."))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain(PASSWORD);
    }

    @Test
    void concurrentRegistrationsOfTheSameUsernameYieldOneCreatedAndTheRestConflict() throws Exception {
        int attempts = 6;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        try {
            List<Future<Integer>> results = new java.util.ArrayList<>();
            for (int i = 0; i < attempts; i++) {
                Callable<Integer> attempt = () -> {
                    start.await();
                    return register("reg-race", PASSWORD).andReturn().getResponse().getStatus();
                };
                results.add(pool.submit(attempt));
            }
            start.countDown();
            List<Integer> statuses = new java.util.ArrayList<>();
            for (Future<Integer> result : results) {
                statuses.add(result.get());
            }

            assertThat(statuses).filteredOn(code -> code == 201).hasSize(1);
            assertThat(statuses).filteredOn(code -> code == 409).hasSize(attempts - 1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void passwordsShorterThanFifteenCharactersAreRejected() throws Exception {
        String shortPassword = "x".repeat(14);

        String body = register("reg-short", shortPassword)
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors[0].field").value("password"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain(shortPassword);
        assertThat(users.findByUsername("reg-short")).isEmpty();
    }

    @Test
    void passwordsLongerThanSixtyFourCharactersAreRejected() throws Exception {
        register("reg-long", "x".repeat(65)).andExpect(status().isBadRequest());
        register("reg-max", "x".repeat(64)).andExpect(status().isCreated());
    }

    @Test
    void aSixtyFourCharacterPasswordOverBcryptsSeventyTwoByteLimitIsRejected() throws Exception {
        String multiByte = "é".repeat(64);

        String body = register("reg-bytes", multiByte)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("password"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain(multiByte);
        register("reg-bytes-ok", "é".repeat(36)).andExpect(status().isCreated());
    }

    @Test
    void thePasswordCannotEqualTheUsernameIgnoringCase() throws Exception {
        String name = "reg-same-value-as-password";

        String body = register(name, name.toUpperCase())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("password"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain(name.toUpperCase());
    }

    @Test
    void invalidUsernamesAreRejected() throws Exception {
        register("ab", PASSWORD).andExpect(status().isBadRequest());
        register("a".repeat(51), PASSWORD).andExpect(status().isBadRequest());
        register("has space", PASSWORD).andExpect(status().isBadRequest());
        register("not/allowed", PASSWORD).andExpect(status().isBadRequest());
        register("", PASSWORD).andExpect(status().isBadRequest());
    }

    @Test
    void aRegisteredUserCanLogInWithAnyCaseOfTheUsername() throws Exception {
        register("Reg-Login", PASSWORD).andExpect(status().isCreated());

        send(V1 + "/auth/login", "reg-login", PASSWORD).andExpect(status().isOk());
        send(V1 + "/auth/login", "  REG-LOGIN ", PASSWORD).andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
        send(V1 + "/auth/login", "reg-login", "wrong-password-value").andExpect(status().isUnauthorized());
    }

    @Test
    void aDisabledUserCannotLogIn() throws Exception {
        User user = new User("reg-disabled", encoder.encode(PASSWORD));
        user.setEnabled(false);
        users.saveAndFlush(user);

        send(V1 + "/auth/login", "reg-disabled", PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid credentials."));
    }

    @Test
    void onlyPostIsPublicOnTheRegisterPath() throws Exception {
        mockMvc.perform(get(V1 + "/auth/register")).andExpect(status().isUnauthorized());
    }
}
