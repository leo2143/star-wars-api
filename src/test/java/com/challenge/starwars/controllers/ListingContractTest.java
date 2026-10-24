package com.challenge.starwars.controllers;

import static com.challenge.starwars.support.TestPaths.V1;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
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
import com.challenge.starwars.exceptions.SwapiInvalidResponseException;
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
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import com.challenge.starwars.services.CatalogCache;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@IntegrationTest
class ListingContractTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtProperties jwt;
    @Autowired
    private CatalogCache catalogCache;

    @MockitoBean
    private SwapiClient swapiClient;

    private String bearer;

    @BeforeEach
    void createToken() {
        // Los listados se sirven desde los catálogos cacheados: cada test arranca en frío.
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

    @Test
    void peopleListingExposesThePageContractAndCamelCaseDtos() throws Exception {
        stubCatalogs();

        mockMvc.perform(authed(V1 + "/people"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.count").value(4))
                .andExpect(jsonPath("$.currentPage").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.nextPage").doesNotExist())
                .andExpect(jsonPath("$.previousPage").doesNotExist())
                .andExpect(jsonPath("$.results.length()").value(4))
                .andExpect(jsonPath("$.results[0].id").value("1"))
                .andExpect(jsonPath("$.results[0].name").value("Luke Skywalker"))
                .andExpect(jsonPath("$.results[0].birthYear").value("19BBY"))
                .andExpect(jsonPath("$.results[0].homeworldId").doesNotExist())
                .andExpect(jsonPath("$.results[0].starshipIds").doesNotExist())
                .andExpect(jsonPath("$.results[0].filmIds").doesNotExist())
                .andExpect(jsonPath("$.results[0].vehicleIds").doesNotExist())
                .andExpect(content().string(not(containsString("swapi.tech"))));
    }

    @Test
    void nullNeighbourPagesAreSerializedAsExplicitNulls() throws Exception {
        when(swapiClient.fetchPage(Resources.PEOPLE, 1, 100)).thenReturn(new SwapiPage(List.of(), 0));

        mockMvc.perform(authed(V1 + "/people"))
                .andExpect(jsonPath("$.nextPage").value((Object) null))
                .andExpect(jsonPath("$.previousPage").value((Object) null))
                .andExpect(content().string(containsString("\"nextPage\":null")));
    }

    @Test
    void nameFilterIsAppliedAndTrimmed() throws Exception {
        stubCatalogs();

        mockMvc.perform(authed(V1 + "/people").param("page", "1").param("name", " luke "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.results[0].name").value("Luke Skywalker"));
    }

    @Test
    void blankNameIsTreatedAsAbsent() throws Exception {
        stubCatalogs();

        mockMvc.perform(authed(V1 + "/people").param("name", "   "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(4));
    }

    @Test
    void idFilterReturnsZeroOrOneResult() throws Exception {
        stubCatalogs();

        mockMvc.perform(authed(V1 + "/people?id=1"))
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.results[0].id").value("1"));
        mockMvc.perform(authed(V1 + "/people?id=9999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0))
                .andExpect(jsonPath("$.totalPages").value(0))
                .andExpect(jsonPath("$.results").isEmpty());
    }

    @Test
    void idAndNameMustBothMatch() throws Exception {
        stubCatalogs();

        mockMvc.perform(authed(V1 + "/people?id=1&name=Luke")).andExpect(jsonPath("$.count").value(1));
        mockMvc.perform(authed(V1 + "/people?id=1&name=Vader")).andExpect(jsonPath("$.count").value(0));
    }

    static Stream<Arguments> resourceCases() {
        return Stream.of(
                Arguments.of(V1 + "/films?name=hope", "title", "A New Hope"),
                Arguments.of(V1 + "/starships?name=falcon", "name", "Millennium Falcon"),
                Arguments.of(V1 + "/vehicles?name=speeder", "name", "Snowspeeder"),
                Arguments.of(V1 + "/people?name=luke", "name", "Luke Skywalker"));
    }

    @ParameterizedTest
    @MethodSource("resourceCases")
    void everyResourceFiltersItsCatalogByName(String url, String field, String expected) throws Exception {
        stubCatalogs();

        mockMvc.perform(authed(url))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.results[0]." + field).value(expected));
    }

    @Test
    void filmsAreServedFromTheCatalog() throws Exception {
        stubCatalogs();

        mockMvc.perform(authed(V1 + "/films"))
                .andExpect(jsonPath("$.count").value(6))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.results[0].title").value("A New Hope"))
                .andExpect(jsonPath("$.results[0].episodeId").value(4))
                .andExpect(jsonPath("$.results[0].openingCrawl").isNotEmpty())
                .andExpect(jsonPath("$.results[0].characterIds").doesNotExist())
                .andExpect(jsonPath("$.results[0].planetIds").doesNotExist())
                .andExpect(jsonPath("$.results[0].starshipIds").doesNotExist())
                .andExpect(jsonPath("$.results[0].vehicleIds").doesNotExist());
        verify(swapiClient, never()).fetchPage(any(), anyInt(), anyInt());
    }

    @Test
    void everyListingOfAResourceSharesOneCatalogLoad() throws Exception {
        stubCatalogs();

        mockMvc.perform(authed(V1 + "/people")).andExpect(status().isOk());
        mockMvc.perform(authed(V1 + "/people?name=luke&sort=-name")).andExpect(status().isOk());
        mockMvc.perform(authed(V1 + "/people?id=2")).andExpect(status().isOk());

        verify(swapiClient, times(1)).fetchPage(Resources.PEOPLE, 1, 100);
        verify(swapiClient, never()).fetchPage(eq(Resources.STARSHIPS), anyInt(), anyInt());
    }

    static Stream<Arguments> sortCases() {
        return Stream.of(
                Arguments.of(V1 + "/people?sort=name", "name", "C-3PO"),
                Arguments.of(V1 + "/people?sort=-name", "name", "R2-D2"),
                Arguments.of(V1 + "/films?sort=episodeId", "title", "The Phantom Menace"),
                Arguments.of(V1 + "/films?sort=-releaseDate", "title", "Revenge of the Sith"),
                Arguments.of(V1 + "/starships?sort=-name", "name", "X-wing"),
                Arguments.of(V1 + "/starships?sort=mglt", "name", "CR90 corvette"),
                Arguments.of(V1 + "/vehicles?sort=name", "name", "Sand Crawler"));
    }

    @ParameterizedTest
    @MethodSource("sortCases")
    void everyResourceAcceptsItsSortFields(String url, String field, String firstExpected) throws Exception {
        stubCatalogs();

        mockMvc.perform(authed(url))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0]." + field).value(firstExpected));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            V1 + "/people?sort=birthYear", V1 + "/people?sort=-nope", V1 + "/people?sort=Name",
            V1 + "/films?sort=name", V1 + "/starships?sort=model", V1 + "/vehicles?sort=hyperdriveRating"})
    void aSortFieldTheResourceDoesNotOfferIsABadRequestListingTheAllowedOnes(String url) throws Exception {
        String rejected = url.substring(url.indexOf('=') + 1);

        mockMvc.perform(authed(url))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors[0].field").value("sort"))
                .andExpect(jsonPath("$.errors[0].message").value(containsString("sort must be one of: ")))
                .andExpect(jsonPath("$.errors[0].message").value(not(containsString(rejected))));
        verifyNoInteractions(swapiClient);
    }

    @ParameterizedTest
    @ValueSource(strings = {"sort=", "sort=%20", "sort=--name", "sort=na%20me", "sort=na-me", "sort=name1", "sort=-",
            "sort=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"})
    void aMalformedOrBlankSortIsABadRequestOnTheSortField(String query) throws Exception {
        mockMvc.perform(authed(V1 + "/people?" + query))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("sort"));
        verifyNoInteractions(swapiClient);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "page=0", "page=-1", "page=abc", "page=1.5", "page=99999999999",
            "id=0", "id=-3", "id=abc", "id=99999999999999999999",
            "name=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"})
    void invalidParametersAreBadRequestsAndNeverReachSwapi(String query) throws Exception {
        mockMvc.perform(authed(V1 + "/people?" + query))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Bad Request"));
        verifyNoInteractions(swapiClient);
    }

    @Test
    void validationErrorsNameTheFieldButNeverEchoTheRejectedValue() throws Exception {
        mockMvc.perform(authed(V1 + "/people?page=0"))
                .andExpect(jsonPath("$.errors[0].field").value("page"))
                .andExpect(jsonPath("$.errors[0].message").value("page must be an integer greater than or equal to 1"));
    }

    @Test
    void swapiTimeoutIsAGatewayTimeout() throws Exception {
        when(swapiClient.fetchPage(any(), anyInt(), anyInt())).thenThrow(new SwapiTimeoutException("secret-host:443", null));

        mockMvc.perform(authed(V1 + "/people"))
                .andExpect(status().isGatewayTimeout())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(content().string(not(containsString("secret-host"))));
    }

    @Test
    void swapiFailuresAreBadGateway() throws Exception {
        when(swapiClient.fetchPage(any(), anyInt(), anyInt()))
                .thenThrow(new SwapiUnavailableException("http://internal-host/people 500", null));
        when(swapiClient.fetchAll(any())).thenThrow(new SwapiInvalidResponseException("bad body"));

        mockMvc.perform(authed(V1 + "/people"))
                .andExpect(status().isBadGateway())
                .andExpect(content().string(not(containsString("internal-host"))));
        mockMvc.perform(authed(V1 + "/films")).andExpect(status().isBadGateway());
    }

    @Test
    void swapiRateLimitIsServiceUnavailableWithRetryAfter() throws Exception {
        when(swapiClient.fetchPage(any(), anyInt(), anyInt()))
                .thenThrow(new SwapiRateLimitedException("limit", Duration.ofSeconds(42), null));

        mockMvc.perform(authed(V1 + "/people"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "42"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void swapiRateLimitWithoutHintHasNoRetryAfterHeader() throws Exception {
        when(swapiClient.fetchPage(any(), anyInt(), anyInt()))
                .thenThrow(new SwapiRateLimitedException("limit", null, null));

        mockMvc.perform(authed(V1 + "/people"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().doesNotExist(HttpHeaders.RETRY_AFTER));
    }

    @Test
    void unexpectedErrorsAreGenericInternalServerErrors() throws Exception {
        when(swapiClient.fetchPage(any(), anyInt(), anyInt()))
                .thenThrow(new IllegalStateException("db password is hunter2"));

        mockMvc.perform(authed(V1 + "/people"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(content().string(not(containsString("hunter2"))))
                .andExpect(content().string(not(containsString("IllegalStateException"))));
    }

    @Test
    void postToAListingIsMethodNotAllowed() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(V1 + "/people")
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isMethodNotAllowed());
    }
}
