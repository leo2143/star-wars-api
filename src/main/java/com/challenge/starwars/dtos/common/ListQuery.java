package com.challenge.starwars.dtos.common;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Parámetros de consulta compartidos por todos los endpoints.
 *
 * <p>La normalización ocurre en la construcción: {@code page} vale 1 por defecto, y un {@code name} que está
 * en blanco se trata como ausente, mientras que cualquier otro valor se recorta.
 */
public record ListQuery(
        @Schema(description = "Page number, 1-based. Page size is fixed by the server.", defaultValue = "1", minimum = "1", example = "1") @Min(value = 1, message = "page must be an integer greater than or equal to 1") Integer page,
        @Schema(description = "Filter by SWAPI identifier (positive integer). Returns zero or one result.", minimum = "1", example = "1") @Positive(message = "id must be a positive integer") Long id,
        @Schema(description = "Case-insensitive 'contains' filter on the name (the title for films). " + "Blank values are ignored. If id is also sent, the item with that id must match.", maxLength = 100, example = "Luke") @Size(max = 100, message = "name must be at most 100 characters") String name,
        @Schema(description = "Sort by one field: 'field' ascending, '-field' descending. The allowed fields " + "depend on the resource (see each endpoint). Values that are not comparable (unknown, n/a, " + "blank) come last in either direction; ties keep the id order. Without it, SWAPI order.", maxLength = 50, example = "-height") @Size(max = 50, message = "sort must be at most 50 characters") @Pattern(regexp = "^-?[a-zA-Z]+$", message = "sort must be a field name, optionally prefixed with -") String sort) {

    public ListQuery {
        page = page == null ? 1 : page;
        name = name == null || name.isBlank() ? null : name.trim();
    }
}
