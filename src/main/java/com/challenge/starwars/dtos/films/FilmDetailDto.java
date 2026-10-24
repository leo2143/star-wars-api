package com.challenge.starwars.dtos.films;

import com.challenge.starwars.dtos.people.PersonMinDto;
import com.challenge.starwars.dtos.starships.StarshipMinDto;
import com.challenge.starwars.dtos.vehicles.VehicleMinDto;

import java.util.List;

/**
 * Todos los campos de {@link FilmDto} más los personajes, naves y vehículos que aparecen en el film.
 */
public record FilmDetailDto(
        String id,
        String title,
        Integer episodeId,
        String director,
        String producer,
        String releaseDate,
        String openingCrawl,
        List<PersonMinDto> characters,
        List<StarshipMinDto> starships,
        List<VehicleMinDto> vehicles) {
}
