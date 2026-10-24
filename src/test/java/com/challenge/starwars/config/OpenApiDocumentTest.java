package com.challenge.starwars.config;

import static com.challenge.starwars.support.TestPaths.V1;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.challenge.starwars.clients.SwapiResource;
import com.challenge.starwars.services.FilmsService;
import com.challenge.starwars.services.PeopleService;
import com.challenge.starwars.services.StarshipsService;
import com.challenge.starwars.services.VehiclesService;
import com.challenge.starwars.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/**
 * El documento OpenAPI publicado debe describir el contrato real, incluida la autenticación Bearer.
 */
@IntegrationTest
class OpenApiDocumentTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void declaresTheBearerJwtSchemeAndAppliesItGlobally() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.bearerFormat").value("JWT"))
                .andExpect(jsonPath("$.security[0].bearerAuth").isArray());
    }

    @Test
    void documentsOnlyTheRealEndpoints() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.paths['" + V1 + "/auth/login'].post").exists())
                .andExpect(jsonPath("$.paths['" + V1 + "/people'].get").exists())
                .andExpect(jsonPath("$.paths['" + V1 + "/films'].get").exists())
                .andExpect(jsonPath("$.paths['" + V1 + "/starships'].get").exists())
                .andExpect(jsonPath("$.paths['" + V1 + "/vehicles'].get").exists())
                .andExpect(jsonPath("$.paths['" + V1 + "/people/{id}'].get").exists())
                .andExpect(jsonPath("$.paths['" + V1 + "/films/{id}'].get").exists())
                .andExpect(jsonPath("$.paths['" + V1 + "/starships/{id}'].get").exists())
                .andExpect(jsonPath("$.paths['" + V1 + "/vehicles/{id}'].get").exists())
                .andExpect(jsonPath("$.paths['" + V1 + "/auth/register'].post").exists())
                .andExpect(jsonPath("$.paths['" + V1 + "/auth/refresh'].post").exists())
                .andExpect(jsonPath("$.paths['" + V1 + "/auth/logout'].post").exists())
                .andExpect(jsonPath("$.paths.length()").value(12));
    }

    @Test
    void loginAndRegisterAreMarkedAsNotRequiringAuthentication() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.paths['" + V1 + "/auth/refresh'].post.security").isEmpty())
                .andExpect(jsonPath("$.paths['" + V1 + "/auth/logout'].post.security").isEmpty())
                .andExpect(jsonPath("$.paths['" + V1 + "/auth/login'].post.security").isEmpty())
                .andExpect(jsonPath("$.paths['" + V1 + "/auth/register'].post.security").isEmpty());
    }

    @Test
    void registerDocumentsItsResponses() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.paths['" + V1 + "/auth/register'].post.responses.keys()")
                        .value(hasItems("201", "400", "409", "429")));
    }

    @Test
    void refreshAndLogoutDocumentTheirResponses() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.paths['" + V1 + "/auth/refresh'].post.responses.keys()")
                        .value(hasItems("200", "400", "401", "429")))
                .andExpect(jsonPath("$.paths['" + V1 + "/auth/logout'].post.responses.keys()")
                        .value(hasItems("204", "400")))
                .andExpect(jsonPath("$.components.schemas.TokenResponse.properties.refreshToken").exists())
                .andExpect(jsonPath("$.components.schemas.TokenResponse.properties.refreshExpiresIn").exists());
    }

    @ParameterizedTest
    @ValueSource(strings = {"people", "films", "starships", "vehicles"})
    void listingsDocumentFiltersAndErrors(String resource) throws Exception {
        String operation = "$.paths['" + V1 + "/" + resource + "'].get";
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath(operation + ".parameters[*].name").value(hasItems("page", "id", "name", "sort")))
                .andExpect(jsonPath(operation + ".responses['200']").exists())
                .andExpect(jsonPath(operation + ".responses['400']").exists())
                .andExpect(jsonPath(operation + ".responses['401']").exists())
                .andExpect(jsonPath(operation + ".responses['502']").exists())
                .andExpect(jsonPath(operation + ".responses['503']").exists())
                .andExpect(jsonPath(operation + ".responses['504']").exists());
    }

    @ParameterizedTest
    @CsvSource({"people, People", "films, Films", "starships, Starships", "vehicles, Vehicles"})
    void everyListingOperationCarriesItsOwnResourceTag(String resource, String tag) throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.paths['" + V1 + "/" + resource + "'].get.tags[0]").value(tag))
                .andExpect(jsonPath("$.paths['" + V1 + "/" + resource + "/{id}'].get.tags[0]").value(tag))
                .andExpect(jsonPath("$.paths['" + V1 + "/" + resource + "'].get.tags.length()").value(1));
    }

    @Test
    void publishesExactlyTheAuthenticationTagAndOneTagPerResource() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.tags.length()").value(5))
                .andExpect(jsonPath("$.tags[*].name")
                        .value(hasItems("Authentication", "People", "Films", "Starships", "Vehicles")))
                .andExpect(jsonPath("$.tags[*].description").isNotEmpty());
    }

    static Stream<Arguments> sortableFields() {
        return Stream.of(
                Arguments.of("people", PeopleService.RESOURCE),
                Arguments.of("films", FilmsService.RESOURCE),
                Arguments.of("starships", StarshipsService.RESOURCE),
                Arguments.of("vehicles", VehiclesService.RESOURCE));
    }

    @ParameterizedTest
    @MethodSource("sortableFields")
    void listingsDocumentEverySortableFieldOfTheirResource(String resource, SwapiResource descriptor) throws Exception {
        String description = JsonPath.read(
                mockMvc.perform(get("/v3/api-docs")).andReturn().getResponse().getContentAsString(),
                "$.paths['" + V1 + "/" + resource + "'].get.description");

        assertThat(description).contains("sort=-field");
        descriptor.sortFields().forEach(field -> assertThat(description).contains("`" + field.name() + "`"));
    }

    @ParameterizedTest
    @CsvSource({
            "people, PersonDetailDto, films",
            "films, FilmDetailDto, characters",
            "starships, StarshipDetailDto, films",
            "vehicles, VehicleDetailDto, films"})
    void detailsDocumentTheIdParameterTheSchemaAndEveryErrorResponse(String resource, String schema, String relation)
            throws Exception {
        String operation = "$.paths['" + V1 + "/" + resource + "/{id}'].get";
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath(operation + ".parameters[?(@.name=='id')].in").value(hasItems("path")))
                .andExpect(jsonPath(operation + ".parameters[?(@.name=='id')].required").value(hasItems(true)))
                .andExpect(jsonPath(operation + ".responses['200'].content['*/*'].schema['$ref']")
                        .value("#/components/schemas/" + schema))
                .andExpect(jsonPath(operation + ".responses['400']").exists())
                .andExpect(jsonPath(operation + ".responses['401']").exists())
                .andExpect(jsonPath(operation + ".responses['404']").exists())
                .andExpect(jsonPath(operation + ".responses['502']").exists())
                .andExpect(jsonPath(operation + ".responses['503']").exists())
                .andExpect(jsonPath(operation + ".responses['504']").exists())
                .andExpect(jsonPath("$.components.schemas." + schema + ".properties." + relation).exists());
    }

    @Test
    void detailSchemasExposeTheMinimalRelatedItems() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.components.schemas.FilmMinDto.properties.title").exists())
                .andExpect(jsonPath("$.components.schemas.PersonMinDto.properties.gender").exists())
                .andExpect(jsonPath("$.components.schemas.PersonMinDto.properties.homeworldId").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.PersonDto.properties.homeworldId").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.PersonDetailDto.properties.homeworldId").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.StarshipMinDto.properties.starshipClass").exists())
                .andExpect(jsonPath("$.components.schemas.VehicleMinDto.properties.model").exists())
                .andExpect(jsonPath("$.components.schemas.FilmDetailDto.properties.characters").exists());
    }

    @Test
    void describesThePageResponseAndProblemDetailSchemas() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.components.schemas.ProblemDetail.properties.status").exists())
                .andExpect(jsonPath("$.components.schemas.LoginRequest.properties.username").exists())
                .andExpect(jsonPath("$.components.schemas.TokenResponse.properties.accessToken").exists());
    }
}
