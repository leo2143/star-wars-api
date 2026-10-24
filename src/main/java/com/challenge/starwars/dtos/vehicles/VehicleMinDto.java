package com.challenge.starwars.dtos.vehicles;

/**
 * Vehículo mínimo, incrustado en el detalle de las personas y films con los que se relaciona.
 */
public record VehicleMinDto(String id, String name, String model) {
}
