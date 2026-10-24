package com.challenge.starwars.security.ratelimit;

import static com.challenge.starwars.support.TestPaths.V1;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.challenge.starwars.clients.SwapiClient;
import com.challenge.starwars.support.IntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.json.JsonMapper;

/** Integración de {@link RateLimitFilter} en la cadena de seguridad real. Cada test usa su propia IP de cliente. */
@IntegrationTest
@TestPropertySource(properties = {"app.rate-limit.login.capacity=3", "app.rate-limit.login.period=1h"})
class RateLimitIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JsonMapper json;

    @MockitoBean
    private SwapiClient swapiClient;

    private static RequestPostProcessor from(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }

    private String badLogin() {
        return json.writeValueAsString(Map.of("username", "nobody", "password", "wrong-password"));
    }

    @Test
    void loginIsLimitedPerIpAfterTheConfiguredAttempts() throws Exception {
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post(V1 + "/auth/login").with(from("192.0.2.1"))
                            .contentType(MediaType.APPLICATION_JSON).content(badLogin()))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post(V1 + "/auth/login").with(from("192.0.2.1"))
                        .contentType(MediaType.APPLICATION_JSON).content(badLogin()))
                .andExpect(status().isTooManyRequests())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(result -> assertThat(Long.parseLong(result.getResponse().getHeader("Retry-After")))
                        .isPositive());

        mockMvc.perform(post(V1 + "/auth/login").with(from("192.0.2.2"))
                        .contentType(MediaType.APPLICATION_JSON).content(badLogin()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void theLimitedResponseStillCarriesCorsHeadersForAllowedOrigins() throws Exception {
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post(V1 + "/auth/login").with(from("192.0.2.3")).header("Origin", "http://localhost:4200")
                    .contentType(MediaType.APPLICATION_JSON).content(badLogin()));
        }

        mockMvc.perform(post(V1 + "/auth/login").with(from("192.0.2.3")).header("Origin", "http://localhost:4200")
                        .contentType(MediaType.APPLICATION_JSON).content(badLogin()))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4200"))
                .andExpect(header().string("Access-Control-Expose-Headers", "Retry-After"));
    }

    @Test
    void otherEndpointsAreNeverLimited() throws Exception {
        for (int i = 0; i < 8; i++) {
            mockMvc.perform(get("/actuator/health").with(from("192.0.2.4"))).andExpect(status().isOk());
            mockMvc.perform(get(V1 + "/people").with(from("192.0.2.4"))).andExpect(status().isUnauthorized());
            mockMvc.perform(get(V1 + "/auth/login").with(from("192.0.2.4"))).andExpect(status().isUnauthorized());
        }
    }
}
