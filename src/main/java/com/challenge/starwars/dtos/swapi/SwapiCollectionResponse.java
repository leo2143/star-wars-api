package com.challenge.starwars.dtos.swapi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * La colección {@code /films} sin paginar: un arreglo {@code result} simple.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SwapiCollectionResponse(List<SwapiItem> result) {
}
