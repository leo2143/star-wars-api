package com.challenge.starwars.support;

import static com.challenge.starwars.support.TestPaths.V1;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.challenge.starwars.security.AuthProperties;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

/** Inicia sesión a través del endpoint real, como lo haría un cliente. */
public final class TestAuth {

    private TestAuth() {
    }

    public static String bearer(MockMvc mockMvc, AuthProperties credentials, JsonMapper json) throws Exception {
        String body = json.writeValueAsString(
                Map.of("username", credentials.username(), "password", credentials.password()));
        String response = mockMvc.perform(post(V1 + "/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + json.readTree(response).get("accessToken").asString();
    }
}
