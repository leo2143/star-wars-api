package com.challenge.starwars.mappers;

import com.challenge.starwars.dtos.swapi.SwapiItem;

/**
 * Contrato entre el motor de consultas compartido y un recurso: convierte un elemento normalizado de swapi.tech en
 * el DTO que esta API expone para ese recurso.
 *
 * @param <T> el tipo de DTO público del recurso
 */
@FunctionalInterface
public interface SwapiItemMapper<T> {

    T map(SwapiItem item);
}
