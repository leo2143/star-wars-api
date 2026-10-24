package com.challenge.starwars.integration;

import com.challenge.starwars.security.AuthProperties;
import com.challenge.starwars.support.FakeSwapiServer;
import com.challenge.starwars.support.FakeSwapiServer.Reply;
import com.challenge.starwars.support.Fixtures;
import com.challenge.starwars.support.IntegrationTest;
import com.challenge.starwars.support.TestAuth;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.challenge.starwars.services.CatalogCache;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static com.challenge.starwars.support.TestPaths.V1;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Endpoints de detalle sobre HTTP real contra un swapi.tech falso: cuántas llamadas al origen cuesta una petición de detalle
 * en frío y en caliente (el límite de peticiones es la razón de que existan los catálogos).
 */
@IntegrationTest
class DetailEndToEndTest {

    private static final FakeSwapiServer SWAPI = new FakeSwapiServer(DetailEndToEndTest::route);

    private static Reply route(String target) {
        return switch (target) {
            case "/api/people?page=1&limit=100&expanded=true" -> Reply.json(Fixtures.raw("people-catalog"));
            case "/api/starships?page=1&limit=100&expanded=true" -> Reply.json(Fixtures.raw("starships-catalog"));
            case "/api/vehicles?page=1&limit=100&expanded=true" -> Reply.json(Fixtures.raw("vehicles-catalog"));
            case "/api/films" -> Reply.json(Fixtures.raw("films-all"));
            default -> Reply.status(404, "{\"message\":\"not found\"}");
        };
    }

    @DynamicPropertySource
    static void swapi(DynamicPropertyRegistry registry) {
        registry.add("app.swapi.base-url", SWAPI::baseUrl);
    }

    @AfterAll
    static void stop() {
        SWAPI.close();
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AuthProperties credentials;
    @Autowired
    private JsonMapper json;
    @Autowired
    private CatalogCache catalogCache;

    private String bearer;
    private int requestsBefore;

    @BeforeEach
    void login() throws Exception {
        catalogCache.invalidateAll();
        bearer = TestAuth.bearer(mockMvc, credentials, json);
        requestsBefore = SWAPI.requests().size();
    }

    private MockHttpServletRequestBuilder authed(String url) {
        return get(url).header(HttpHeaders.AUTHORIZATION, bearer);
    }

    private List<String> upstreamCalls() {
        return SWAPI.requests().subList(requestsBefore, SWAPI.requests().size());
    }

    @Test
    void aColdPersonDetailCostsAtMostFourUpstreamCallsAndAWarmOneCostsNone() throws Exception {
        mockMvc.perform(authed(V1 + "/people/1")).andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Luke Skywalker")).andExpect(jsonPath("$.films.length()").value(4));
        assertThat(upstreamCalls()).containsExactlyInAnyOrder("/api/people?page=1&limit=100&expanded=true", "/api/films", "/api/starships?page=1&limit=100&expanded=true", "/api/vehicles?page=1&limit=100&expanded=true");

        mockMvc.perform(authed(V1 + "/people/1")).andExpect(status().isOk());
        mockMvc.perform(authed(V1 + "/people/4")).andExpect(status().isOk());
        mockMvc.perform(authed(V1 + "/films/1")).andExpect(status().isOk());
        mockMvc.perform(authed(V1 + "/starships/10")).andExpect(status().isOk());
        mockMvc.perform(authed(V1 + "/vehicles/4")).andExpect(status().isOk());

        assertThat(upstreamCalls()).hasSize(4);
    }

    @Test
    void aColdFilmDetailAlsoCostsFourCallsAndFilmsShareTheCatalogWithPeopleDetails() throws Exception {
        mockMvc.perform(authed(V1 + "/films/1")).andExpect(status().isOk()).andExpect(jsonPath("$.characters[0].name").value("Luke Skywalker"));

        assertThat(upstreamCalls()).hasSize(4);
    }

    @Test
    void aColdStarshipDetailNeedsOnlyItsOwnCatalogAndTheFilms() throws Exception {
        mockMvc.perform(authed(V1 + "/starships/9")).andExpect(status().isOk());

        assertThat(upstreamCalls()).containsExactlyInAnyOrder("/api/starships?page=1&limit=100&expanded=true", "/api/films");
    }

    @Test
    void anUnknownIdIsNotFoundAfterLoadingOnlyItsOwnCatalog() throws Exception {
        mockMvc.perform(authed(V1 + "/vehicles/12345")).andExpect(status().isNotFound());

        assertThat(upstreamCalls()).containsExactly("/api/vehicles?page=1&limit=100&expanded=true");
    }
}
