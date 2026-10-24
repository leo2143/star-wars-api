package com.challenge.starwars.controllers;

import static com.challenge.starwars.support.TestPaths.V1;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.challenge.starwars.clients.SwapiClient;
import com.challenge.starwars.security.AuthProperties;
import com.challenge.starwars.dtos.swapi.SwapiPage;
import com.challenge.starwars.entities.User;
import com.challenge.starwars.repositories.UserRepository;
import com.challenge.starwars.security.jwt.JwtProperties;
import com.challenge.starwars.support.IntegrationTest;
import com.challenge.starwars.support.MutableClock;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Rotación de refresh tokens contra la base de datos real, con un reloj que controla el test. */
@IntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class RefreshTokenFlowTest {

    private static final MutableClock CLOCK = new MutableClock();

    static Clock clock() {
        return CLOCK;
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JsonMapper json;
    @Autowired
    private AuthProperties credentials;
    @Autowired
    private JwtProperties jwtProperties;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private UserRepository users;

    @TestBean(name = "clock")
    private Clock clock;
    @MockitoBean
    private SwapiClient swapiClient;

    @BeforeEach
    void resetClock() {
        CLOCK.reset();
    }

    private JsonNode login() throws Exception {
        return loginAs(credentials.username(), credentials.password());
    }

    private JsonNode loginAs(String username, String password) throws Exception {
        String body = json.writeValueAsString(Map.of("username", username, "password", password));
        return json.readTree(mockMvc.perform(post(V1 + "/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(body)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private MvcResult refresh(String token) throws Exception {
        return mockMvc.perform(post(V1 + "/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("refreshToken", token)))).andReturn();
    }

    private MvcResult logout(String token) throws Exception {
        return mockMvc.perform(post(V1 + "/auth/logout").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("refreshToken", token)))).andReturn();
    }

    private JsonNode body(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }

    private static String sha256(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private UUID familyOf(String token) throws Exception {
        return jdbc.queryForObject("select family_id from refresh_tokens where token_hash = ?", UUID.class,
                sha256(token));
    }

    private int revokedInFamily(UUID family) {
        return jdbc.queryForObject("select count(*) from refresh_tokens where family_id = ? and revoked_at is not null",
                Integer.class, family);
    }

    private int sizeOfFamily(UUID family) {
        return jdbc.queryForObject("select count(*) from refresh_tokens where family_id = ?", Integer.class, family);
    }

    @Test
    void loginReturnsBothTokensAndStoresOnlyTheHash() throws Exception {
        JsonNode tokens = login();
        String refreshToken = tokens.get("refreshToken").asString();

        assertThat(tokens.get("accessToken").asString()).isNotBlank();
        assertThat(refreshToken).matches("[A-Za-z0-9_-]{43}");
        assertThat(tokens.get("refreshExpiresIn").asLong()).isEqualTo(jwtProperties.refreshTtl().toSeconds());
        assertThat(jdbc.queryForObject("select count(*) from refresh_tokens where token_hash = ?", Integer.class,
                refreshToken)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from refresh_tokens where token_hash = ?", Integer.class,
                sha256(refreshToken))).isEqualTo(1);
    }

    @Test
    void everyLoginStartsANewFamily() throws Exception {
        assertThat(familyOf(login().get("refreshToken").asString()))
                .isNotEqualTo(familyOf(login().get("refreshToken").asString()));
    }

    @Test
    void refreshRotatesTheTokenAndKeepsTheFamily() throws Exception {
        String first = login().get("refreshToken").asString();

        MvcResult rotated = refresh(first);
        String second = body(rotated).get("refreshToken").asString();

        assertThat(rotated.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(rotated).get("accessToken").asString()).isNotBlank();
        assertThat(body(rotated).get("tokenType").asString()).isEqualTo("Bearer");
        assertThat(second).isNotEqualTo(first);
        assertThat(familyOf(second)).isEqualTo(familyOf(first));
        assertThat(jdbc.queryForObject("select used_at is not null from refresh_tokens where token_hash = ?",
                Boolean.class, sha256(first))).isTrue();
        assertThat(refresh(second).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void reusingAUsedTokenRevokesTheWholeFamilyAndTheRevocationIsKept() throws Exception {
        String first = login().get("refreshToken").asString();
        String newest = body(refresh(first)).get("refreshToken").asString();

        MvcResult reuse = refresh(first);

        assertThat(reuse.getResponse().getStatus()).isEqualTo(401);
        assertThat(revokedInFamily(familyOf(first))).isEqualTo(2);
        assertThat(refresh(newest).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void expiredTokenIsRejected() throws Exception {
        String token = login().get("refreshToken").asString();

        CLOCK.advance(jwtProperties.refreshTtl().plusSeconds(1));

        assertThat(refresh(token).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void rotatedTokensInheritTheExpiryOfTheSession() throws Exception {
        JsonNode loginTokens = login();
        CLOCK.advance(Duration.ofHours(1));

        JsonNode refreshed = body(refresh(loginTokens.get("refreshToken").asString()));

        assertThat(refreshed.get("refreshExpiresIn").asLong())
                .isEqualTo(loginTokens.get("refreshExpiresIn").asLong() - 3600);
    }

    @Test
    void sessionEndsAtTheOriginalExpiryEvenIfItWasRefreshedOften() throws Exception {
        String token = login().get("refreshToken").asString();
        CLOCK.advance(jwtProperties.refreshTtl().minusMinutes(1));
        token = body(refresh(token)).get("refreshToken").asString();

        CLOCK.advance(Duration.ofMinutes(2));

        assertThat(refresh(token).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void disabledUserCannotRefresh() throws Exception {
        String username = "disabled-" + UUID.randomUUID().toString().substring(0, 8);
        String password = "a-long-enough-password-1";
        mockMvc.perform(post(V1 + "/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("username", username, "password", password))))
                .andExpect(status().isCreated());
        String token = loginAs(username, password).get("refreshToken").asString();
        User user = users.findByUsername(username).orElseThrow();
        user.setEnabled(false);
        users.save(user);

        assertThat(refresh(token).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void everyRejectionLooksTheSame() throws Exception {
        String used = login().get("refreshToken").asString();
        refresh(used);
        String expired = login().get("refreshToken").asString();
        String loggedOut = login().get("refreshToken").asString();
        logout(loggedOut);

        String unknown = refresh("not-a-token").getResponse().getContentAsString();
        String reused = refresh(used).getResponse().getContentAsString();
        String revoked = refresh(loggedOut).getResponse().getContentAsString();
        CLOCK.advance(jwtProperties.refreshTtl().plusSeconds(1));
        MvcResult expiredResult = refresh(expired);

        assertThat(expiredResult.getResponse().getStatus()).isEqualTo(401);
        assertThat(expiredResult.getResponse().getContentType()).contains("problem+json");
        assertThat(body(expiredResult).get("detail").asString()).isEqualTo("Invalid refresh token.");
        assertThat(List.of(reused, revoked, expiredResult.getResponse().getContentAsString()))
                .allSatisfy(response -> assertThat(response).isEqualTo(unknown));
    }

    @Test
    void logoutRevokesTheFamilyAndIsIdempotent() throws Exception {
        String first = login().get("refreshToken").asString();
        String newest = body(refresh(first)).get("refreshToken").asString();

        assertThat(logout(newest).getResponse().getStatus()).isEqualTo(204);
        assertThat(logout(newest).getResponse().getStatus()).isEqualTo(204);

        assertThat(revokedInFamily(familyOf(first))).isEqualTo(sizeOfFamily(familyOf(first)));
        assertThat(refresh(newest).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void logoutOfAnUnknownTokenAlsoReturnsNoContent() throws Exception {
        MvcResult result = logout("never-issued");

        assertThat(result.getResponse().getStatus()).isEqualTo(204);
        assertThat(result.getResponse().getContentAsString()).isEmpty();
    }

    @Test
    void parallelRefreshesOfTheSameTokenYieldOneSuccessAndEndTheSession() throws Exception {
        String token = login().get("refreshToken").asString();
        int threads = 4;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<MvcResult>> results = new java.util.ArrayList<>();
            for (int i = 0; i < threads; i++) {
                Callable<MvcResult> task = () -> {
                    start.await();
                    return refresh(token);
                };
                results.add(pool.submit(task));
            }
            start.countDown();
            List<Integer> statuses = new java.util.ArrayList<>();
            for (Future<MvcResult> result : results) {
                statuses.add(result.get().getResponse().getStatus());
            }

            assertThat(statuses).containsExactlyInAnyOrder(200, 401, 401, 401);
        } finally {
            pool.shutdownNow();
        }
        UUID family = familyOf(token);
        assertThat(sizeOfFamily(family)).isEqualTo(2);
        assertThat(revokedInFamily(family)).isEqualTo(2);
    }

    @Test
    void invalidBodiesAreBadRequests() throws Exception {
        for (String path : List.of(V1 + "/auth/refresh", V1 + "/auth/logout")) {
            for (String body : List.of("{}", "{\"refreshToken\":\"\"}", "{\"refreshToken\":\"   \"}",
                    "{\"refreshToken\":\"" + "a".repeat(201) + "\"}")) {
                mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body))
                        .andExpect(status().isBadRequest())
                        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
            }
        }
    }

    @Test
    void theAccessTokenFromARefreshOpensProtectedEndpoints() throws Exception {
        when(swapiClient.fetchPage(any(), anyInt(), anyInt())).thenReturn(new SwapiPage(List.of(), 0));
        String refreshed = body(refresh(login().get("refreshToken").asString())).get("accessToken").asString();

        mockMvc.perform(get(V1 + "/people").header(HttpHeaders.AUTHORIZATION, "Bearer " + refreshed))
                .andExpect(status().isOk());
    }

    @Test
    void refreshTokensAreNeverLogged(CapturedOutput output) throws Exception {
        JsonNode tokens = login();
        String first = tokens.get("refreshToken").asString();
        String second = body(refresh(first)).get("refreshToken").asString();
        refresh(first);
        logout(second);

        assertThat(output.getAll()).doesNotContain(first).doesNotContain(second);
    }
}
