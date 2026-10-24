package com.challenge.starwars.controllers;

import com.challenge.starwars.config.OpenApiConfig;
import com.challenge.starwars.controllers.support.ListingApi;
import com.challenge.starwars.controllers.support.ListingDocs;
import com.challenge.starwars.dtos.common.ListQuery;
import com.challenge.starwars.dtos.common.PageResponse;
import com.challenge.starwars.dtos.films.FilmDetailDto;
import com.challenge.starwars.dtos.films.FilmDto;
import com.challenge.starwars.services.FilmsService;
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
 * Listado y detalle de solo lectura de films.
 */
@Tag(name = "Films", description = "Star Wars films: paginated list with filters and sorting, and detail with related characters, starships and vehicles")
@ListingApi("/api/{version}/films")
@RequiredArgsConstructor
public class FilmsController {

    private final FilmsService service;

    @GetMapping
    @Operation(summary = "List films", description = "Films, filterable by id and/or title (sent as `name`)." + ListingDocs.FILTER_RULES + ListingDocs.SORT_RULES + "`title`, `episodeId`, `releaseDate`.")
    public PageResponse<FilmDto> films(@ParameterObject @Valid ListQuery query) {
        return service.list(query);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a film", description = "One film with its characters, starships and vehicles." + ListingDocs.DETAIL_NOTE)
    @ApiResponse(responseCode = "200", description = ListingDocs.FOUND)
    @ApiResponse(responseCode = "400", description = ListingDocs.INVALID_ID, content = @Content(mediaType = OpenApiConfig.PROBLEM_JSON, schema = @Schema(ref = OpenApiConfig.PROBLEM_SCHEMA_REF)))
    @ApiResponse(responseCode = "404", description = ListingDocs.NOT_FOUND, content = @Content(mediaType = OpenApiConfig.PROBLEM_JSON, schema = @Schema(ref = OpenApiConfig.PROBLEM_SCHEMA_REF)))
    public FilmDetailDto film(@Parameter(description = "SWAPI identifier (positive integer)", example = "1") @PathVariable @Positive(message = ListingDocs.ID_MESSAGE) long id) {
        return service.detail(id);
    }
}
