package com.challenge.starwars.controllers;

import com.challenge.starwars.config.OpenApiConfig;
import com.challenge.starwars.controllers.support.ListingApi;
import com.challenge.starwars.controllers.support.ListingDocs;
import com.challenge.starwars.dtos.common.ListQuery;
import com.challenge.starwars.dtos.common.PageResponse;
import com.challenge.starwars.dtos.vehicles.VehicleDetailDto;
import com.challenge.starwars.dtos.vehicles.VehicleDto;
import com.challenge.starwars.services.VehiclesService;
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
 * Listado y detalle de solo lectura de vehicles.
 */
@Tag(name = "Vehicles", description = "Star Wars vehicles: paginated list with filters and sorting, and detail with related films")
@ListingApi("/api/{version}/vehicles")
@RequiredArgsConstructor
public class VehiclesController {

    private final VehiclesService service;

    @GetMapping
    @Operation(summary = "List vehicles", description = "Vehicles, filterable by id and/or name." + ListingDocs.FILTER_RULES + ListingDocs.SORT_RULES + "`name`, `costInCredits`, `length`, `crew`, `passengers`, `maxAtmospheringSpeed`, `cargoCapacity`.")
    public PageResponse<VehicleDto> vehicles(@ParameterObject @Valid ListQuery query) {
        return service.list(query);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a vehicle", description = "One vehicle with the films it appears in." + ListingDocs.DETAIL_NOTE)
    @ApiResponse(responseCode = "200", description = ListingDocs.FOUND)
    @ApiResponse(responseCode = "400", description = ListingDocs.INVALID_ID, content = @Content(mediaType = OpenApiConfig.PROBLEM_JSON, schema = @Schema(ref = OpenApiConfig.PROBLEM_SCHEMA_REF)))
    @ApiResponse(responseCode = "404", description = ListingDocs.NOT_FOUND, content = @Content(mediaType = OpenApiConfig.PROBLEM_JSON, schema = @Schema(ref = OpenApiConfig.PROBLEM_SCHEMA_REF)))
    public VehicleDetailDto vehicle(@Parameter(description = "SWAPI identifier (positive integer)", example = "4") @PathVariable @Positive(message = ListingDocs.ID_MESSAGE) long id) {
        return service.detail(id);
    }
}
