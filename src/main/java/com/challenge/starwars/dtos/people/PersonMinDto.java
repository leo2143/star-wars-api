package com.challenge.starwars.dtos.people;

/**
 * Persona mínima, incrustada en el detalle de los films en los que aparece.
 */
public record PersonMinDto(String id, String name, String gender) {
}
