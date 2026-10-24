package com.challenge.starwars.clients;

import java.util.List;

/**
 * Descriptor de un recurso de swapi.tech. Cada paquete de funcionalidad declara su propia constante, de modo que agregar un
 * recurso nunca requiere editar una lista compartida.
 *
 * <p>{@code path} es la ruta de la colección en el origen. {@code nameProperty} es la propiedad que contiene el
 * nombre visible del elemento, el que filtra el parámetro público {@code name} (el título en el caso de films).
 * {@code remotePaginated} indica si swapi.tech pagina la colección (films no).
 * {@code sortFields} son los campos por los que se puede ordenar el listado, en el orden en que se documentan.
 */
public record SwapiResource(String path, String nameProperty, boolean remotePaginated, List<SortField> sortFields) {

    public SwapiResource {
        sortFields = List.copyOf(sortFields);
    }
}
