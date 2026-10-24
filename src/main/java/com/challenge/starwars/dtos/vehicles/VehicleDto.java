package com.challenge.starwars.dtos.vehicles;

/**
 * Vehículo tal como lo expone el listado. Los atributos de apariencia numérica siguen siendo texto, como los envía SWAPI. Sus films
 * solo están en {@link VehicleDetailDto}.
 */
public record VehicleDto(
        String id,
        String name,
        String model,
        String vehicleClass,
        String manufacturer,
        String costInCredits,
        String length,
        String crew,
        String passengers,
        String maxAtmospheringSpeed,
        String cargoCapacity,
        String consumables) {
}
