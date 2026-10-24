package com.challenge.starwars.dtos.swapi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Recurso normalizado de swapi.tech: {@code uid}, {@code description} y el conjunto libre de {@code
 * properties}. Todas las variantes de respuesta (lista expandida, búsqueda, individual) traen elementos con esta forma.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SwapiItem(String uid, String description, Map<String, Object> properties) {

    public SwapiItem {
        properties = properties == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(properties));
    }

    /**
     * Devuelve la propiedad como texto, o {@code null} si está ausente.
     */
    public String text(String key) {
        Object value = properties.get(key);
        return value == null ? null : value.toString();
    }
}
