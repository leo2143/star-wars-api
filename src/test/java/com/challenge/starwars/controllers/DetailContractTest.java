package com.challenge.starwars.controllers;

import static com.challenge.starwars.support.TestPaths.V1;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.challenge.starwars.clients.SwapiClient;
import com.challenge.starwars.dtos.swapi.SwapiPage;
import com.challenge.starwars.exceptions.SwapiRateLimitedException;
import com.challenge.starwars.exceptions.SwapiTimeoutException;
import com.challenge.starwars.exceptions.SwapiUnavailableException;
import com.challenge.starwars.security.jwt.JwtProperties;
import com.challenge.starwars.support.Fixtures;
import com.challenge.starwars.support.IntegrationTest;
import com.challenge.starwars.support.Resources;
import com.challenge.starwars.support.TestTokens;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import com.challenge.starwars.services.CatalogCache;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@IntegrationTest
class DetailContractTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtProperties jwt;
    @Autowired
    private CatalogCache catalogCache;
    @Autowired
    private JsonMapper json;

    @MockitoBean
    private SwapiClient swapiClient;

    private String bearer;

    @BeforeEach
    void setUp() {
        catalogCache.invalidateAll();
        Instant now = Instant.now();
        bearer = "Bearer " + TestTokens.signed(jwt.secretBase64(), MacAlgorithm.HS256, jwt.issuer(), jwt.audience(),
                now, now.plusSeconds(300));
    }

    private MockHttpServletRequestBuilder authed(String url) {
        return get(url).header(HttpHeaders.AUTHORIZATION, bearer);
    }

    private void stubCatalogs() {
        when(swapiClient.fetchPage(Resources.PEOPLE, 1, 100))
                .thenReturn(new SwapiPage(Fixtures.list("people-catalog").results(), 1));
        when(swapiClient.fetchPage(Resources.STARSHIPS, 1, 100))
                .thenReturn(new SwapiPage(Fixtures.list("starships-catalog").results(), 1));
        when(swapiClient.fetchPage(Resources.VEHICLES, 1, 100))
                .thenReturn(new SwapiPage(Fixtures.list("vehicles-catalog").results(), 1));
        when(swapiClient.fetchAll(Resources.FILMS)).thenReturn(Fixtures.search("films-all"));
    }

    private List<String> fieldNames(String body) {
        JsonNode tree = json.readTree(body);
        return new ArrayList<>(tree.propertyNames());
    }

    private String body(String url) throws Exception {
        return mockMvc.perform(authed(url)).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void personDetailHasTheListFieldsPlusFilmsStarshipsAndVehicles() throws Exception {
        stubCatalogs();

        String body = body(V1 + "/people/1");

        assertThat(fieldNames(body)).containsExactly("id", "name", "height", "mass", "hairColor", "skinColor",
                "eyeColor", "birthYear", "gender", "films", "starships", "vehicles");
        assertThat(fieldNames(json.readTree(body).get("films").get(0).toString()))
                .containsExactly("id", "title", "episodeId", "releaseDate");
        assertThat(fieldNames(json.readTree(body).get("starships").get(0).toString()))
                .containsExactly("id", "name", "model", "starshipClass");
        assertThat(fieldNames(json.readTree(body).get("vehicles").get(0).toString()))
                .containsExactly("id", "name", "model");
        assertThat(body).doesNotContain("swapi.tech");
        mockMvc.perform(authed(V1 + "/people/1"))
                .andExpect(jsonPath("$.name").value("Luke Skywalker"))
                .andExpect(jsonPath("$.homeworldId").doesNotExist())
                .andExpect(jsonPath("$.films.length()").value(4))
                .andExpect(jsonPath("$.films[0].title").value("A New Hope"))
                .andExpect(jsonPath("$.films[0].episodeId").value(4))
                .andExpect(jsonPath("$.starships[0].name").value("X-wing"))
                .andExpect(jsonPath("$.vehicles[0].name").value("Snowspeeder"));
    }

    @Test
    void filmDetailHasTheListFieldsPlusCharactersStarshipsAndVehicles() throws Exception {
        stubCatalogs();

        String body = body(V1 + "/films/1");

        assertThat(fieldNames(body)).containsExactly("id", "title", "episodeId", "director", "producer",
                "releaseDate", "openingCrawl", "characters", "starships", "vehicles");
        assertThat(fieldNames(json.readTree(body).get("characters").get(0).toString()))
                .containsExactly("id", "name", "gender");
        mockMvc.perform(authed(V1 + "/films/1"))
                .andExpect(jsonPath("$.title").value("A New Hope"))
                .andExpect(jsonPath("$.characters[0].name").value("Luke Skywalker"))
                .andExpect(jsonPath("$.starships.length()").value(4))
                .andExpect(jsonPath("$.vehicles.length()").value(2));
    }

    @Test
    void starshipDetailHasTheListFieldsPlusFilms() throws Exception {
        stubCatalogs();

        String body = body(V1 + "/starships/9");

        assertThat(fieldNames(body)).containsExactly("id", "name", "model", "starshipClass", "manufacturer",
                "costInCredits", "length", "crew", "passengers", "maxAtmospheringSpeed", "hyperdriveRating", "mglt",
                "cargoCapacity", "consumables", "films");
        mockMvc.perform(authed(V1 + "/starships/9"))
                .andExpect(jsonPath("$.name").value("Death Star"))
                .andExpect(jsonPath("$.films[0].title").value("A New Hope"));
    }

    @Test
    void vehicleDetailHasTheListFieldsPlusFilms() throws Exception {
        stubCatalogs();

        String body = body(V1 + "/vehicles/4");

        assertThat(fieldNames(body)).containsExactly("id", "name", "model", "vehicleClass", "manufacturer",
                "costInCredits", "length", "crew", "passengers", "maxAtmospheringSpeed", "cargoCapacity",
                "consumables", "films");
        mockMvc.perform(authed(V1 + "/vehicles/4"))
                .andExpect(jsonPath("$.films.length()").value(2));
    }

    @Test
    void aSecondDetailRequestIsServedFromTheCatalogCache() throws Exception {
        stubCatalogs();

        body(V1 + "/people/1");
        body(V1 + "/people/2");
        body(V1 + "/films/1");

        verify(swapiClient, times(1)).fetchPage(Resources.PEOPLE, 1, 100);
        verify(swapiClient, times(1)).fetchPage(Resources.STARSHIPS, 1, 100);
        verify(swapiClient, times(1)).fetchPage(Resources.VEHICLES, 1, 100);
        verify(swapiClient, times(1)).fetchAll(Resources.FILMS);
    }

    @ParameterizedTest
    @ValueSource(strings = {"people", "films", "starships", "vehicles"})
    void unknownIdIsANotFoundProblemWithoutInternalDetails(String resource) throws Exception {
        stubCatalogs();

        mockMvc.perform(authed(V1 + "/" + resource + "/99999"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("Not Found"))
                .andExpect(content().string(not(containsString("swapi"))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "0", "-1", "1.5", "99999999999999999999"})
    void nonPositiveIntegerIdsAreBadRequestsAndNeverReachSwapi(String id) throws Exception {
        for (String resource : List.of("people", "films", "starships", "vehicles")) {
            mockMvc.perform(authed(V1 + "/" + resource + "/" + id))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.title").value("Bad Request"))
                    .andExpect(jsonPath("$.detail").value("One or more request parameters are invalid."))
                    .andExpect(jsonPath("$.errors[0].field").value("id"));
        }
        verifyNoInteractions(swapiClient);
    }

    @Test
    void invalidIdMessageIsFixed() throws Exception {
        mockMvc.perform(authed(V1 + "/people/0"))
                .andExpect(jsonPath("$.errors[0].message").value("id must be a positive integer"));
        mockMvc.perform(authed(V1 + "/people/abc"))
                .andExpect(jsonPath("$.errors[0].message").value("invalid value"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"people", "films", "starships", "vehicles"})
    void detailRequiresAToken(String resource) throws Exception {
        mockMvc.perform(get(V1 + "/" + resource + "/1"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(V1 + "/" + resource + "/1").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(swapiClient);
    }

    @Test
    void upstreamTimeoutIsAGatewayTimeoutAndNotCached() throws Exception {
        stubCatalogs();
        when(swapiClient.fetchAll(Resources.FILMS))
                .thenThrow(new SwapiTimeoutException("secret-host:443", null))
                .thenReturn(Fixtures.search("films-all"));

        mockMvc.perform(authed(V1 + "/films/1"))
                .andExpect(status().isGatewayTimeout())
                .andExpect(content().string(not(containsString("secret-host"))));
        mockMvc.perform(authed(V1 + "/films/1")).andExpect(status().isOk());

        verify(swapiClient, times(2)).fetchAll(Resources.FILMS);
    }

    @Test
    void upstreamFailureIsABadGateway() throws Exception {
        when(swapiClient.fetchPage(any(), anyInt(), anyInt()))
                .thenThrow(new SwapiUnavailableException("http://internal-host/people 500", null));

        mockMvc.perform(authed(V1 + "/people/1"))
                .andExpect(status().isBadGateway())
                .andExpect(content().string(not(containsString("internal-host"))));
    }

    @Test
    void upstreamRateLimitIsServiceUnavailableWithRetryAfter() throws Exception {
        when(swapiClient.fetchPage(any(), anyInt(), anyInt()))
                .thenThrow(new SwapiRateLimitedException("limit", Duration.ofSeconds(42), null));

        mockMvc.perform(authed(V1 + "/people/1"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "42"));
    }
}
