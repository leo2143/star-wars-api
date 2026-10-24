package com.challenge.starwars.dtos.starships;

import com.challenge.starwars.dtos.films.FilmMinDto;

import java.util.List;

/**
 * Todos los campos de {@link StarshipDto} más los films en los que aparece la nave.
 */
public record StarshipDetailDto(
        String id,
        String name,
        String model,
        String starshipClass,
        String manufacturer,
        String costInCredits,
        String length,
        String crew,
        String passengers,
        String maxAtmospheringSpeed,
        String hyperdriveRating,
        String mglt,
        String cargoCapacity,
        String consumables,
        List<FilmMinDto> films) {
}
