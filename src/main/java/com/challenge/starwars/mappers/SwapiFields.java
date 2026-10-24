package com.challenge.starwars.mappers;

import com.challenge.starwars.dtos.swapi.SwapiItem;
import com.challenge.starwars.exceptions.SwapiInvalidResponseException;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utilidades de parseo de campos compartidas por todos los mappers de recursos: identificadores, URLs de recursos, relaciones y números.
 * Se preservan los valores originales; los atributos ausentes pasan a ser {@code null} (escalares) o vacíos (relaciones).
 */
public final class SwapiFields {

    private static final Pattern TRAILING_ID = Pattern.compile("/(\\d+)/?$");

    private SwapiFields() {
    }

    /**
     * Devuelve el identificador numérico al final de la URL de un recurso, o {@code null} si no hay ninguno.
     */
    public static String extractId(String url) {
        if (url == null) {
            return null;
        }
        Matcher matcher = TRAILING_ID.matcher(url);
        return matcher.find() ? matcher.group(1) : null;
    }

    /**
     * {@code uid} es la fuente principal; el último segmento de {@code url} es solo un respaldo.
     */
    public static String idOf(SwapiItem item) {
        if (item.uid() != null && !item.uid().isBlank()) {
            return item.uid();
        }
        String fromUrl = extractId(item.text("url"));
        if (fromUrl == null) {
            throw new SwapiInvalidResponseException("SWAPI item without a usable identifier");
        }
        return fromUrl;
    }

    /**
     * Identificadores extraídos de las URLs de relación almacenadas bajo {@code key}; vacío si está ausente.
     */
    public static List<String> relatedIds(SwapiItem item, String key) {
        if (!(item.properties().get(key) instanceof List<?> urls)) {
            return List.of();
        }
        return urls.stream().map(url -> extractId(String.valueOf(url))).filter(id -> id != null).toList();
    }

    /**
     * Parseo de enteros tolerante: {@code null} cuando el valor está ausente o no es un número.
     */
    public static Integer parseIntOrNull(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
