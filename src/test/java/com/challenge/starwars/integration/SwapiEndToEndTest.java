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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static com.challenge.starwars.support.TestPaths.V1;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Login -> listado protegido -> RestClient real -> swapi.tech falso sobre HTTP. Solo se reemplaza el origen;
 * seguridad, controladores, servicio, cliente, mapper y JSON son los de producción. Los listados se sirven
 * desde los catálogos cacheados, así que el registro de peticiones al origen también demuestra cuántas llamadas cuesta cada uno.
 */
@IntegrationTest
class SwapiEndToEndTest {

    private static final String PEOPLE_CATALOG = "/api/people?page=1&limit=100&expanded=true";
    private static final String STARSHIPS_CATALOG = "/api/starships?page=1&limit=100&expanded=true";
    private static final String VEHICLES_CATALOG = "/api/vehicles?page=1&limit=100&expanded=true";

    /**
     * Lo que responde swapi.tech para el catálogo de people; los tests lo cambian para simular fallos del origen.
     */
    private static final AtomicReference<Reply> PEOPLE_REPLY = new AtomicReference<>();

    /**
     * Cuántas respuestas 503 llegan antes de que el catálogo de people se sirva con normalidad.
     */
    private static final AtomicInteger PEOPLE_TRANSIENT_FAILURES = new AtomicInteger();

    private static final FakeSwapiServer SWAPI = new FakeSwapiServer(SwapiEndToEndTest::route);

    private static Reply route(String target) {
        return switch (target) {
            case PEOPLE_CATALOG ->
                    PEOPLE_TRANSIENT_FAILURES.getAndUpdate(n -> Math.max(0, n - 1)) > 0 ? Reply.status(503, "{}") : PEOPLE_REPLY.get();
            case STARSHIPS_CATALOG -> Reply.json(Fixtures.raw("starships-catalog"));
            case VEHICLES_CATALOG -> Reply.json(Fixtures.raw("vehicles-catalog"));
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
        PEOPLE_TRANSIENT_FAILURES.set(0);
        PEOPLE_REPLY.set(Reply.json(Fixtures.raw("people-catalog")));
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
    void aColdListingCostsOneCatalogCallAndAWarmOneCostsNone() throws Exception {
        mockMvc.perform(authed(V1 + "/people?page=1")).andExpect(status().isOk()).andExpect(jsonPath("$.count").value(4)).andExpect(jsonPath("$.totalPages").value(1)).andExpect(jsonPath("$.results[0].name").value("Luke Skywalker")).andExpect(jsonPath("$.results[0].homeworldId").doesNotExist());
        assertThat(upstreamCalls()).containsExactly(PEOPLE_CATALOG);

        mockMvc.perform(authed(V1 + "/people?page=1")).andExpect(status().isOk());
        mockMvc.perform(authed(V1 + "/people?name=vader&sort=-name")).andExpect(status().isOk());
        mockMvc.perform(authed(V1 + "/people?id=2")).andExpect(status().isOk());

        assertThat(upstreamCalls()).containsExactly(PEOPLE_CATALOG);
    }

    @Test
    void aColdFilmsListingIsOneCallAndAWarmOneIsFree() throws Exception {
        mockMvc.perform(authed(V1 + "/films")).andExpect(jsonPath("$.count").value(6)).andExpect(jsonPath("$.totalPages").value(1));
        assertThat(upstreamCalls()).containsExactly("/api/films");

        mockMvc.perform(authed(V1 + "/films?name=hope")).andExpect(jsonPath("$.count").value(1));

        assertThat(upstreamCalls()).hasSize(1);
    }

    @Test
    void listingAndDetailShareTheSameCatalog() throws Exception {
        mockMvc.perform(authed(V1 + "/starships")).andExpect(status().isOk());
        mockMvc.perform(authed(V1 + "/starships/9")).andExpect(status().isOk());

        // El detalle solo agrega el catálogo de films; el de starships no se carga dos veces.
        assertThat(upstreamCalls()).containsExactlyInAnyOrder(STARSHIPS_CATALOG, "/api/films");
    }

    @Test
    void starshipsAndVehiclesListingsMapTheirOwnAttributes() throws Exception {
        mockMvc.perform(authed(V1 + "/starships")).andExpect(jsonPath("$.count").value(4)).andExpect(jsonPath("$.results[0].starshipClass").isNotEmpty());
        mockMvc.perform(authed(V1 + "/vehicles")).andExpect(jsonPath("$.count").value(3)).andExpect(jsonPath("$.results[0].vehicleClass").isNotEmpty());
    }

    @Test
    void pageBeyondTheLastIsEmpty() throws Exception {
        mockMvc.perform(authed(V1 + "/people?page=50")).andExpect(status().isOk()).andExpect(jsonPath("$.currentPage").value(50)).andExpect(jsonPath("$.totalPages").value(1)).andExpect(jsonPath("$.previousPage").value(1)).andExpect(jsonPath("$.results").isEmpty());
    }

    @Test
    void nameFilterIsAppliedLocallyAndNeverSentToSwapi() throws Exception {
        mockMvc.perform(authed(V1 + "/people?name=luke&page=1")).andExpect(jsonPath("$.count").value(1)).andExpect(jsonPath("$.results[0].id").value("1"));
        mockMvc.perform(authed(V1 + "/films?name=hope")).andExpect(jsonPath("$.results[0].title").value("A New Hope"));

        assertThat(upstreamCalls()).containsExactlyInAnyOrder(PEOPLE_CATALOG, "/api/films");
    }

    @Test
    void sortOrdersTheWholeCatalogBeforePaginating() throws Exception {
        mockMvc.perform(authed(V1 + "/people?sort=-name&page=2")).andExpect(jsonPath("$.currentPage").value(2)).andExpect(jsonPath("$.results").isEmpty());
        mockMvc.perform(authed(V1 + "/people?sort=-name")).andExpect(jsonPath("$.results[0].name").value("R2-D2")).andExpect(jsonPath("$.results[3].name").value("C-3PO"));
        mockMvc.perform(authed(V1 + "/people?sort=nope")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("sort"));

        assertThat(upstreamCalls()).containsExactly(PEOPLE_CATALOG);
    }

    @Test
    void idFilterIsAnsweredFromTheCatalog() throws Exception {
        mockMvc.perform(authed(V1 + "/starships?id=9")).andExpect(jsonPath("$.count").value(1)).andExpect(jsonPath("$.results[0].name").value("Death Star"));
        mockMvc.perform(authed(V1 + "/vehicles?id=4&name=crawler")).andExpect(jsonPath("$.results[0].name").value("Sand Crawler"));
        mockMvc.perform(authed(V1 + "/vehicles?id=4&name=speeder")).andExpect(jsonPath("$.results").isEmpty());

        assertThat(upstreamCalls()).containsExactlyInAnyOrder(STARSHIPS_CATALOG, VEHICLES_CATALOG);
    }

    @Test
    void unknownIdIsASuccessfulEmptyResultNotAnError() throws Exception {
        mockMvc.perform(authed(V1 + "/people?id=9999")).andExpect(status().isOk()).andExpect(jsonPath("$.count").value(0)).andExpect(jsonPath("$.results").isEmpty());
    }

    @Test
    void reservedCharactersInNameNeverReachSwapi() throws Exception {
        mockMvc.perform(authed(V1 + "/starships").param("name", "a b&c=d")).andExpect(status().isOk()).andExpect(jsonPath("$.results").isEmpty());

        assertThat(upstreamCalls()).containsExactly(STARSHIPS_CATALOG);
    }

    @Test
    void upstreamRateLimitBecomesServiceUnavailableWithRetryAfter() throws Exception {
        PEOPLE_REPLY.set(Reply.status(429, "{\"message\":\"rate limited\"}").withHeader("Retry-After", "7"));

        mockMvc.perform(authed(V1 + "/people")).andExpect(status().isServiceUnavailable()).andExpect(header().string(HttpHeaders.RETRY_AFTER, "7"));
    }

    @Test
    void upstreamServerErrorBecomesBadGatewayWithoutLeakingItsBody() throws Exception {
        PEOPLE_REPLY.set(Reply.status(500, "{\"message\":\"internal swapi detail\"}"));

        mockMvc.perform(authed(V1 + "/people")).andExpect(status().isBadGateway()).andExpect(content().string(not(containsString("internal swapi detail"))));
    }

    @Test
    void aTransientUpstreamFailureIsRetriedAndTheListingSucceeds() throws Exception {
        PEOPLE_TRANSIENT_FAILURES.set(1);

        mockMvc.perform(authed(V1 + "/people")).andExpect(status().isOk()).andExpect(jsonPath("$.count").value(4));

        assertThat(upstreamCalls()).containsExactly(PEOPLE_CATALOG, PEOPLE_CATALOG);
    }

    @Test
    void upstreamRateLimitIsNotRetried() throws Exception {
        PEOPLE_REPLY.set(Reply.status(429, "{}"));

        mockMvc.perform(authed(V1 + "/people")).andExpect(status().isServiceUnavailable());

        assertThat(upstreamCalls()).containsExactly(PEOPLE_CATALOG);
    }

    @Test
    void upstreamGarbageBecomesBadGateway() throws Exception {
        PEOPLE_REPLY.set(Reply.json("<html>maintenance</html>"));

        mockMvc.perform(authed(V1 + "/people")).andExpect(status().isBadGateway());
    }

    @Test
    void aFailedCatalogLoadIsNotCachedSoTheNextListingRetriesUpstream() throws Exception {
        PEOPLE_REPLY.set(Reply.status(500, "{}"));
        mockMvc.perform(authed(V1 + "/people")).andExpect(status().isBadGateway());

        PEOPLE_REPLY.set(Reply.json(Fixtures.raw("people-catalog")));
        mockMvc.perform(authed(V1 + "/people")).andExpect(status().isOk()).andExpect(jsonPath("$.count").value(4));

        assertThat(upstreamCalls()).containsExactly(PEOPLE_CATALOG, PEOPLE_CATALOG, PEOPLE_CATALOG, PEOPLE_CATALOG);
    }
}
