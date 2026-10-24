package com.challenge.starwars.integration;

import com.challenge.starwars.security.AuthProperties;
import com.challenge.starwars.support.FakeSwapiServer;
import com.challenge.starwars.support.FakeSwapiServer.Reply;
import com.challenge.starwars.support.IntegrationTest;
import com.challenge.starwars.support.TestAuth;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import static com.challenge.starwars.support.TestPaths.V1;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Con SWAPI caído de forma sostenida, el breaker se abre y la API responde 503 sin volver a llamarla.
 */
@IntegrationTest
class SwapiCircuitBreakerEndToEndTest {

    private static final FakeSwapiServer BROKEN_SWAPI = new FakeSwapiServer(target -> Reply.status(500, "{}"));

    @DynamicPropertySource
    static void swapi(DynamicPropertyRegistry registry) {
        registry.add("app.swapi.base-url", BROKEN_SWAPI::baseUrl);
        registry.add("app.swapi.retry.max-retries", () -> "0");
        registry.add("app.swapi.circuit-breaker.failure-rate-threshold", () -> "50");
        registry.add("app.swapi.circuit-breaker.sliding-window-size", () -> "2");
        registry.add("app.swapi.circuit-breaker.minimum-number-of-calls", () -> "2");
        registry.add("app.swapi.circuit-breaker.wait-duration-in-open-state", () -> "10m");
    }

    @AfterAll
    static void stop() {
        BROKEN_SWAPI.close();
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AuthProperties credentials;
    @Autowired
    private JsonMapper json;

    @Test
    void openBreakerAnswersServiceUnavailableWithoutCallingSwapi() throws Exception {
        String bearer = TestAuth.bearer(mockMvc, credentials, json);
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(get(V1 + "/people").header(HttpHeaders.AUTHORIZATION, bearer)).andExpect(status().isBadGateway());
        }
        int callsBeforeOpen = BROKEN_SWAPI.requests().size();

        mockMvc.perform(get(V1 + "/people").header(HttpHeaders.AUTHORIZATION, bearer)).andExpect(status().isServiceUnavailable()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)).andExpect(header().string(HttpHeaders.RETRY_AFTER, "600"));

        assertThat(BROKEN_SWAPI.requests()).hasSize(callsBeforeOpen);
    }
}
