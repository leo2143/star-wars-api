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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;

import static com.challenge.starwars.support.TestPaths.V1;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Un origen en silencio debe convertirse en un 504 tras el timeout de lectura configurado, no colgar la petición.
 */
@IntegrationTest
class SwapiTimeoutEndToEndTest {

    private static final FakeSwapiServer SLOW_SWAPI = new FakeSwapiServer(target -> Reply.slow(4_000));
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AuthProperties credentials;
    @Autowired
    private JsonMapper json;

    @DynamicPropertySource
    static void swapi(DynamicPropertyRegistry registry) {
        registry.add("app.swapi.base-url", SLOW_SWAPI::baseUrl);
        registry.add("spring.http.clients.read-timeout", () -> "300ms");
        registry.add("spring.http.clients.connect-timeout", () -> "300ms");
    }

    @AfterAll
    static void stop() {
        SLOW_SWAPI.close();
    }

    @Test
    void readTimeoutProducesGatewayTimeoutQuickly() throws Exception {
        String bearer = TestAuth.bearer(mockMvc, credentials, json);
        long start = System.nanoTime();

        mockMvc.perform(get(V1 + "/people").header(HttpHeaders.AUTHORIZATION, bearer)).andExpect(status().isGatewayTimeout());

        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(3));
    }
}
