package com.challenge.starwars.controllers.support;

/**
 * Textos compartidos por todos los endpoints de listado y detalle (descripciones OpenAPI y mensajes de validación).
 */
public final class ListingDocs {

    public static final String FILTER_RULES = " If `id` is sent the result has zero or one item; if `name` is also " + "sent, that item must match it (case-insensitive 'contains'). Pages beyond the last one return an " + "empty `results` array with correct metadata.";

    /**
     * Nota de ordenamiento compartida por los listados; cada listado agrega sus propios campos permitidos (se mantiene sincronizada con el
     * descriptor del recurso mediante un test).
     */
    public static final String SORT_RULES = " Sort with `sort=field` (ascending) or `sort=-field` (descending), one " + "field at a time; values that are not comparable (unknown, n/a, blank) come last in either direction " + "and ties keep the id order. Without `sort` the order is the one of swapi.tech. Sortable fields: ";

    /**
     * Mensaje de validación para la variable de ruta {@code {id}} de los endpoints de detalle.
     */
    public static final String ID_MESSAGE = "id must be a positive integer";

    public static final String DETAIL_NOTE = " Related items are returned in their minimal form and resolved from " + "cached catalogs, so a warm request costs no upstream call. Related ids unknown to swapi.tech are " + "omitted.";

    public static final String INVALID_ID = "The id is not a positive integer";

    public static final String FOUND = "The item with its related items";

    public static final String NOT_FOUND = "No item with that id";

    private ListingDocs() {
    }
}
