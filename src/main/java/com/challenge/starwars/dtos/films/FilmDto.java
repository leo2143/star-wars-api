package com.challenge.starwars.dtos.films;

/**
 * Film tal como lo expone el listado. Los personajes, naves y vehículos relacionados solo están en {@link FilmDetailDto}.
 */
public record FilmDto(
        String id,
        String title,
        Integer episodeId,
        String director,
        String producer,
        String releaseDate,
        String openingCrawl) {
}
