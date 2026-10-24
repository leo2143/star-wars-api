package com.challenge.starwars.dtos.films;

/**
 * Film mínimo, incrustado en el detalle de las personas, naves y vehículos que aparecen en él.
 */
public record FilmMinDto(String id, String title, Integer episodeId, String releaseDate) {
}
