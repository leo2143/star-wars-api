package com.challenge.starwars.dtos.people;

/**
 * Persona tal como la expone el listado. Los films, naves y vehículos relacionados solo están en {@link PersonDetailDto}.
 */
public record PersonDto(
        String id,
        String name,
        String height,
        String mass,
        String hairColor,
        String skinColor,
        String eyeColor,
        String birthYear,
        String gender) {
}
