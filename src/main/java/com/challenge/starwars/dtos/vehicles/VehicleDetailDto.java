package com.challenge.starwars.dtos.vehicles;

import com.challenge.starwars.dtos.films.FilmMinDto;

import java.util.List;

/**
 * Todos los campos de {@link VehicleDto} más los films en los que aparece el vehículo.
 */
public record VehicleDetailDto(
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
        String consumables,
        List<FilmMinDto> films) {
}
