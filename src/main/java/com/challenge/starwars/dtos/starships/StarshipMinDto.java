package com.challenge.starwars.dtos.starships;

/**
 * Nave mínima, incrustada en el detalle de las personas y films con los que se relaciona.
 */
public record StarshipMinDto(String id, String name, String model, String starshipClass) {
}
