package com.challenge.starwars.dtos.swapi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Listado paginado: {@code GET /{resource}?page=N&limit=M&expanded=true}. Atención a la clave {@code results}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SwapiListResponse(
        @JsonProperty("total_pages") Integer totalPages,
        List<SwapiItem> results) {
}
