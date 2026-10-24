package com.challenge.starwars.dtos.starships;

/**
 * Nave tal como la expone el listado. Los atributos de apariencia numérica siguen siendo texto, como los envía SWAPI. Sus films
 * solo están en {@link StarshipDetailDto}.
 */
public record StarshipDto(
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
        String consumables) {
}
