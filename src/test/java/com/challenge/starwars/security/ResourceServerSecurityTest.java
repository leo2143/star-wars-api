package com.challenge.starwars.security;

import static com.challenge.starwars.support.TestPaths.V1;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.challenge.starwars.clients.SwapiClient;
import com.challenge.starwars.dtos.swapi.SwapiPage;
import com.challenge.starwars.security.jwt.JwtProperties;
import com.challenge.starwars.support.IntegrationTest;
import com.challenge.starwars.support.TestTokens;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Pasa por la cadena de filtros real con tokens reales. Cada denegación también verifica que el efecto
 * protegido (una llamada a SWAPI) nunca ocurrió.
 */
@IntegrationTest
class ResourceServerSecurityTest {

    private static final String[] PROTECTED = {V1 + "/people", V1 + "/films", V1 + "/starships", V1 + "/vehicles"};

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtProperties jwt;

    @MockitoBean
    private SwapiClient swapiClient;

    @BeforeEach
    void stubUpstream() {
        when(swapiClient.fetchPage(any(), anyInt(), anyInt())).thenReturn(new SwapiPage(List.of(), 0));
        when(swapiClient.fetchAll(any())).thenReturn(List.of());
    }

    private String token(String secret, MacAlgorithm alg, String issuer, String audience, Instant iat, Instant exp) {
        return TestTokens.signed(secret, alg, issuer, audience, iat, exp);
    }

    private String validToken() {
        Instant now = Instant.now();
        return token(jwt.secretBase64(), MacAlgorithm.HS256, jwt.issuer(), jwt.audience(), now, now.plusSeconds(300));
    }

    private void assertRejected(String bearer) throws Exception {
        mockMvc.perform(get(V1 + "/people").header(HttpHeaders.AUTHORIZATION, "Bearer " + bearer))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, org.hamcrest.Matchers.startsWith("Bearer")))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        verifyNoInteractions(swapiClient);
    }

    @ParameterizedTest
    @ValueSource(strings = {V1 + "/people", V1 + "/films", V1 + "/starships", V1 + "/vehicles"})
    void listingsRejectRequestsWithoutToken(String path) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists(HttpHeaders.WWW_AUTHENTICATE))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401));
        verifyNoInteractions(swapiClient);
    }

    @ParameterizedTest
    @ValueSource(strings = {V1 + "/people", V1 + "/films", V1 + "/starships", V1 + "/vehicles"})
    void listingsAcceptAValidToken(String path) throws Exception {
        mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken()))
                .andExpect(status().isOk());
    }

    @Test
    void garbageTokenIsRejected() throws Exception {
        assertRejected("not-a-jwt");
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() throws Exception {
        Instant now = Instant.now();
        assertRejected(token(TestTokens.randomSecretBase64(64), MacAlgorithm.HS256, jwt.issuer(), jwt.audience(), now,
                now.plusSeconds(300)));
    }

    @Test
    void tokenSignedWithAnotherAlgorithmIsRejectedEvenWithTheRightSecret() throws Exception {
        Instant now = Instant.now();
        assertRejected(token(jwt.secretBase64(), MacAlgorithm.HS512, jwt.issuer(), jwt.audience(), now,
                now.plusSeconds(300)));
    }

    @Test
    void unsignedTokenIsRejected() throws Exception {
        assertRejected(TestTokens.unsigned());
    }

    @Test
    void expiredTokenIsRejected() throws Exception {
        Instant now = Instant.now();
        assertRejected(token(jwt.secretBase64(), MacAlgorithm.HS256, jwt.issuer(), jwt.audience(),
                now.minusSeconds(3600), now.minusSeconds(1800)));
    }

    @Test
    void tokenFromAnotherIssuerIsRejected() throws Exception {
        Instant now = Instant.now();
        assertRejected(token(jwt.secretBase64(), MacAlgorithm.HS256, "https://evil.example", jwt.audience(), now,
                now.plusSeconds(300)));
    }

    @Test
    void tokenForAnotherAudienceIsRejected() throws Exception {
        Instant now = Instant.now();
        assertRejected(token(jwt.secretBase64(), MacAlgorithm.HS256, jwt.issuer(), "someone-else", now,
                now.plusSeconds(300)));
    }

    @Test
    void tokenWithoutAudienceIsRejected() throws Exception {
        Instant now = Instant.now();
        assertRejected(token(jwt.secretBase64(), MacAlgorithm.HS256, jwt.issuer(), null, now, now.plusSeconds(300)));
    }

    @Test
    void basicAuthIsNotAnAlternative() throws Exception {
        mockMvc.perform(get(V1 + "/people").header(HttpHeaders.AUTHORIZATION, "Basic ZGVtbzpkZW1v"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(swapiClient);
    }

    @Test
    void unknownPathsRequireAuthenticationToo() throws Exception {
        mockMvc.perform(get(V1 + "/planets")).andExpect(status().isUnauthorized());
    }

    @Test
    void unknownPathWithValidTokenIsAProblemDetailNotFound() throws Exception {
        mockMvc.perform(get(V1 + "/planets").header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/people", "/api/people/1", "/api/films", "/api/starships", "/api/vehicles", "/api/auth/login"})
    void unversionedPathsAreNotFoundAndNeverReachSwapi(String path) throws Exception {
        mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        verifyNoInteractions(swapiClient);
    }

    @Test
    void unsupportedVersionIsBadRequestAndNeverReachesSwapi() throws Exception {
        mockMvc.perform(get("/api/v2/people").header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken()))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(swapiClient);
    }

    @Test
    void tokenIsNotAcceptedAsQueryParameter() throws Exception {
        mockMvc.perform(get(V1 + "/people").param("access_token", validToken()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void openApiDocumentAndSwaggerUiArePublic() throws Exception {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
        mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }

    @Test
    void healthIsPublicButOtherActuatorEndpointsAreNot() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
        mockMvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/beans")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/health").header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/actuator/env").header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken()))
                .andExpect(status().isNotFound());
    }

    @Test
    void loggersEndpointIsDeniedEvenWithAValidToken() throws Exception {
        mockMvc.perform(post("/actuator/loggers/com.challenge.starwars")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken())
                        .contentType("application/json")
                        .content("{\"configuredLevel\":\"DEBUG\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/actuator/loggers")).andExpect(status().isUnauthorized());
    }

    @Test
    void healthDoesNotExposeDetails() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(jsonPath("$.components").doesNotExist());
    }

    @Test
    void corsPreflightFromAnAllowedOriginIsAccepted() throws Exception {
        mockMvc.perform(options(V1 + "/people")
                        .header(HttpHeaders.ORIGIN, "http://localhost:4200")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:4200"));
    }

    @Test
    void crossOriginResponsesExposeRetryAfterToTheBrowser() throws Exception {
        mockMvc.perform(get(V1 + "/people")
                        .header(HttpHeaders.ORIGIN, "http://localhost:4200")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:4200"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, "Retry-After"));
    }

    @Test
    void corsPreflightFromAnotherOriginIsRejected() throws Exception {
        mockMvc.perform(options(V1 + "/people")
                        .header(HttpHeaders.ORIGIN, "https://evil.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }
}
