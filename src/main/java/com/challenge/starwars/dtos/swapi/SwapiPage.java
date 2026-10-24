package com.challenge.starwars.dtos.swapi;

import java.util.List;

/**
 * Una página paginada en origen, tal como la normaliza el cliente.
 */
public record SwapiPage(List<SwapiItem> items, int totalPages) {
}
