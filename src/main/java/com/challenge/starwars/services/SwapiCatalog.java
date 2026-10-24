package com.challenge.starwars.services;

import com.challenge.starwars.clients.SwapiResource;
import com.challenge.starwars.dtos.swapi.SwapiItem;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Búsqueda de elementos de swapi.tech por id sobre los catálogos cacheados de colección completa.
 *
 * <p>swapi.tech limita a los llamadores a 100 peticiones por ventana, por lo que resolver las relaciones de un elemento
 * (un film por sí solo lista 18 personajes) con una petición a la vez no es una opción. En cambio, cada recurso se
 * carga en frío una vez y luego se mantiene cacheado con refresco periódico en segundo plano (ver
 * {@link SwapiCatalogLoader}), y cada búsqueda se responde desde memoria: un detalle en frío
 * cuesta como máximo cuatro llamadas al origen y uno en caliente no cuesta ninguna.
 */
@Component
@RequiredArgsConstructor
class SwapiCatalog {

    private static final Logger log = LoggerFactory.getLogger(SwapiCatalog.class);

    private final SwapiCatalogLoader loader;

    public Optional<SwapiItem> lookup(SwapiResource resource, String id) {
        log.debug("Detail lookup: resource={} id={}", resource.path(), id);
        return Optional.ofNullable(loader.load(resource).get(id));
    }

    /**
     * Mapea los elementos con los ids dados, en el orden dado (el orden de swapi). Los ids ausentes del catálogo
     * se omiten. Una lista de ids vacía nunca carga el catálogo.
     */
    public <T> List<T> resolveRelated(SwapiResource resource, List<String> ids, Function<SwapiItem, T> mapper) {
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<String, SwapiItem> itemsById = loader.load(resource);
        return ids.stream()
                .map(id -> {
                    SwapiItem item = itemsById.get(id);
                    if (item == null) {
                        log.debug("Skipping {} {}: not in the catalog", resource.path(), id);
                    }
                    return item;
                })
                .filter(item -> item != null)
                .map(mapper)
                .toList();
    }
}
