package com.challenge.starwars.controllers;

import static com.challenge.starwars.support.TestPaths.V1;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.challenge.starwars.clients.SwapiClient;
import com.challenge.starwars.security.AuthProperties;
import com.challenge.starwars.security.jwt.JwtProperties;
import com.challenge.starwars.support.IntegrationTest;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.json.JsonMapper;

@IntegrationTest
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtDecoder jwtDecoder;
    @Autowired
    private JwtProperties jwtProperties;
    @Autowired
    private AuthProperties credentials;
    @Autowired
    private JsonMapper json;

    @MockitoBean
    private SwapiClient swapiClient;

    private String loginBody(String username, String password) {
        return json.writeValueAsString(java.util.Map.of("username", username, "password", password));
    }

    @Test
    void loginWithValidCredentialsReturnsABearerToken() throws Exception {
        mockMvc.perform(post(V1 + "/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(credentials.username(), credentials.password())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(jwtProperties.ttlSeconds()))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshExpiresIn").value(jwtProperties.refreshTtl().toSeconds()));
    }

    @Test
    void issuedTokenIsSignedAndCarriesTheExpectedClaims() throws Exception {
        MvcResult result = mockMvc.perform(post(V1 + "/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(credentials.username(), credentials.password())))
                .andReturn();
        String token = json.readTree(result.getResponse().getContentAsString()).get("accessToken").asString();

        Jwt jwt = jwtDecoder.decode(token);

        assertThat(jwt.getHeaders()).containsEntry("alg", "HS256");
        assertThat(jwt.getIssuer()).hasToString(jwtProperties.issuer());
        assertThat(jwt.getSubject()).isEqualTo(credentials.username());
        assertThat(jwt.getAudience()).containsExactly(jwtProperties.audience());
        assertThat(jwt.getId()).isNotBlank();
        assertThat(jwt.getIssuedAt()).isBetween(Instant.now().minusSeconds(30), Instant.now().plusSeconds(30));
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt()))
                .isEqualTo(Duration.ofSeconds(jwtProperties.ttlSeconds()));
    }

    @Test
    void everyLoginGetsADifferentJti() throws Exception {
        String body = loginBody(credentials.username(), credentials.password());
        String first = mockMvc.perform(post(V1 + "/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse().getContentAsString();
        String second = mockMvc.perform(post(V1 + "/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse().getContentAsString();

        String jtiOne = jwtDecoder.decode(json.readTree(first).get("accessToken").asString()).getId();
        String jtiTwo = jwtDecoder.decode(json.readTree(second).get("accessToken").asString()).getId();

        assertThat(jtiOne).isNotEqualTo(jtiTwo);
    }

    @Test
    void wrongPasswordAndUnknownUserGetTheSameGenericUnauthorized() throws Exception {
        String wrongPassword = mockMvc.perform(post(V1 + "/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(credentials.username(), "definitely-wrong")))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andReturn().getResponse().getContentAsString();
        String unknownUser = mockMvc.perform(post(V1 + "/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("nobody", "definitely-wrong")))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertThat(wrongPassword).isEqualTo(unknownUser);
        assertThat(wrongPassword).doesNotContain("accessToken").doesNotContain(credentials.username());
    }

    @Test
    void blankCredentialsAreABadRequest() throws Exception {
        mockMvc.perform(post(V1 + "/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("", "")))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void malformedJsonIsABadRequest() throws Exception {
        mockMvc.perform(post(V1 + "/auth/login").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void onlyPostIsPublicOnTheLoginPath() throws Exception {
        mockMvc.perform(get(V1 + "/auth/login")).andExpect(status().isUnauthorized());
    }
}
