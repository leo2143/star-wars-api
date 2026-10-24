package com.challenge.starwars.dtos.people;

import com.challenge.starwars.dtos.films.FilmMinDto;
import com.challenge.starwars.dtos.starships.StarshipMinDto;
import com.challenge.starwars.dtos.vehicles.VehicleMinDto;

import java.util.List;

/**
 * Todos los campos de {@link PersonDto} más los films, naves y vehículos con los que se relaciona la persona.
 */
public record PersonDetailDto(
        String id,
        String name,
        String height,
        String mass,
        String hairColor,
        String skinColor,
        String eyeColor,
        String birthYear,
        String gender,
        List<FilmMinDto> films,
        List<StarshipMinDto> starships,
        List<VehicleMinDto> vehicles) {
}
