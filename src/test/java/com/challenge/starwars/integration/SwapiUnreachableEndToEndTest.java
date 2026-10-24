package com.challenge.starwars.integration;

import com.challenge.starwars.security.AuthProperties;
import com.challenge.starwars.support.FakeSwapiServer;
import com.challenge.starwars.support.FakeSwapiServer.Reply;
import com.challenge.starwars.support.IntegrationTest;
import com.challenge.starwars.support.TestAuth;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import static com.challenge.starwars.support.TestPaths.V1;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Conexión rechazada (nadie escucha) es un fallo del origen: 502, no 500 ni un cuelgue.
 */
@IntegrationTest
class SwapiUnreachableEndToEndTest {

    private static final String CLOSED_PORT_URL = closedPortUrl();
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AuthProperties credentials;
    @Autowired
    private JsonMapper json;

    private static String closedPortUrl() {
        try (FakeSwapiServer server = new FakeSwapiServer(target -> Reply.json("{}"))) {
            return server.baseUrl();
        }
    }

    @DynamicPropertySource
    static void swapi(DynamicPropertyRegistry registry) {
        registry.add("app.swapi.base-url", () -> CLOSED_PORT_URL);
    }

    @Test
    void connectionRefusedIsBadGateway() throws Exception {
        String bearer = TestAuth.bearer(mockMvc, credentials, json);

        mockMvc.perform(get(V1 + "/people").header(HttpHeaders.AUTHORIZATION, bearer)).andExpect(status().isBadGateway());
    }
}
