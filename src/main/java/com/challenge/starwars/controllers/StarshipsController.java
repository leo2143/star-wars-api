package com.challenge.starwars.controllers;

import com.challenge.starwars.config.OpenApiConfig;
import com.challenge.starwars.controllers.support.ListingApi;
import com.challenge.starwars.controllers.support.ListingDocs;
import com.challenge.starwars.dtos.common.ListQuery;
import com.challenge.starwars.dtos.common.PageResponse;
import com.challenge.starwars.dtos.starships.StarshipDetailDto;
import com.challenge.starwars.dtos.starships.StarshipDto;
import com.challenge.starwars.services.StarshipsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Listado y detalle de solo lectura de starships.
 */
@Tag(name = "Starships", description = "Star Wars starships: paginated list with filters and sorting, and detail with related films")
@ListingApi("/api/{version}/starships")
@RequiredArgsConstructor
public class StarshipsController {

    private final StarshipsService service;

    @GetMapping
    @Operation(summary = "List starships", description = "Starships, filterable by id and/or name." + ListingDocs.FILTER_RULES + ListingDocs.SORT_RULES + "`name`, `costInCredits`, `length`, `crew`, `passengers`, `maxAtmospheringSpeed`, `hyperdriveRating`, `mglt`, `cargoCapacity`.")
    public PageResponse<StarshipDto> starships(@ParameterObject @Valid ListQuery query) {
        return service.list(query);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a starship", description = "One starship with the films it appears in." + ListingDocs.DETAIL_NOTE)
    @ApiResponse(responseCode = "200", description = ListingDocs.FOUND)
    @ApiResponse(responseCode = "400", description = ListingDocs.INVALID_ID, content = @Content(mediaType = OpenApiConfig.PROBLEM_JSON, schema = @Schema(ref = OpenApiConfig.PROBLEM_SCHEMA_REF)))
    @ApiResponse(responseCode = "404", description = ListingDocs.NOT_FOUND, content = @Content(mediaType = OpenApiConfig.PROBLEM_JSON, schema = @Schema(ref = OpenApiConfig.PROBLEM_SCHEMA_REF)))
    public StarshipDetailDto starship(@Parameter(description = "SWAPI identifier (positive integer)", example = "9") @PathVariable @Positive(message = ListingDocs.ID_MESSAGE) long id) {
        return service.detail(id);
    }
}
