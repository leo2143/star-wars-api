package com.challenge.starwars.support;

import com.challenge.starwars.dtos.swapi.SwapiItem;
import com.challenge.starwars.dtos.swapi.SwapiListResponse;
import com.challenge.starwars.dtos.swapi.SwapiCollectionResponse;
import com.challenge.starwars.mappers.SwapiFields;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.json.JsonMapper;

/** Carga respuestas reales de swapi.tech capturadas en {@code src/test/resources/swapi}. */
public final class Fixtures {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    /** El envelope de {@code GET /{resource}/{id}}, capturado como fixture para los tests de los mappers. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Single(SwapiItem result) {
    }

    private Fixtures() {
    }

    public static String raw(String name) {
        try (InputStream in = Fixtures.class.getResourceAsStream("/swapi/" + name + ".json")) {
            if (in == null) {
                throw new IllegalArgumentException("Missing fixture " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static SwapiListResponse list(String name) {
        return MAPPER.readValue(raw(name), SwapiListResponse.class);
    }

    public static List<SwapiItem> search(String name) {
        return MAPPER.readValue(raw(name), SwapiCollectionResponse.class).result();
    }

    public static SwapiItem single(String name) {
        return MAPPER.readValue(raw(name), Single.class).result();
    }

    /** Una colección completa indexada por id, en el orden del origen, tal como la produce el cargador del catálogo. */
    public static Map<String, SwapiItem> byId(List<SwapiItem> items) {
        Map<String, SwapiItem> itemsById = new LinkedHashMap<>();
        items.forEach(item -> itemsById.put(SwapiFields.idOf(item), item));
        return itemsById;
    }

    public static Map<String, SwapiItem> catalog(String listFixture) {
        return byId(list(listFixture).results());
    }

    public static Map<String, SwapiItem> filmsCatalog() {
        return byId(search("films-all"));
    }
}
