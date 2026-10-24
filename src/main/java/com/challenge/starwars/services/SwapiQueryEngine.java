package com.challenge.starwars.services;

import com.challenge.starwars.clients.SortField;
import com.challenge.starwars.clients.SwapiResource;
import com.challenge.starwars.config.SwapiProperties;
import com.challenge.starwars.dtos.common.ListQuery;
import com.challenge.starwars.dtos.common.PageResponse;
import com.challenge.starwars.dtos.swapi.SwapiItem;
import com.challenge.starwars.exceptions.InvalidSortException;
import com.challenge.starwars.mappers.SwapiFields;
import com.challenge.starwars.mappers.SwapiItemMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Resuelve un {@link ListQuery} para cualquier recurso y devuelve el contrato único {@link PageResponse}.
 * Se escribe una sola vez; los recursos solo aportan su {@link SwapiResource} y su mapper.
 *
 * <p>Cada listado se sirve desde el catálogo cacheado del recurso (ver {@link SwapiCatalogLoader}):
 * filtra por {@code id} y {@code name}, ordena y luego recorta la página solicitada. Un listado en caliente no cuesta ninguna
 * llamada al origen y uno en frío cuesta la carga del catálogo.
 */
@Component
class SwapiQueryEngine {

    private static final Logger log = LoggerFactory.getLogger(SwapiQueryEngine.class);

    private final SwapiCatalogLoader loader;
    private final int pageSize;

    SwapiQueryEngine(SwapiCatalogLoader loader, SwapiProperties properties) {
        this.loader = loader;
        this.pageSize = properties.pageSize();
    }

    /**
     * El filtro lo escribe el cliente: se neutralizan los caracteres de control para que no falsifique líneas de log.
     */
    private static String loggable(String value) {
        return value == null ? null : value.replaceAll("\\p{Cntrl}", "_");
    }

    /**
     * "Contiene" sin distinguir mayúsculas sobre el nombre visible; sin filtro de nombre coincide todo.
     */
    private static boolean matchesName(SwapiResource resource, SwapiItem item, String name) {
        if (name == null) {
            return true;
        }
        String displayName = item.text(resource.nameProperty());
        return displayName != null && displayName.toLowerCase(Locale.ROOT).contains(name.toLowerCase(Locale.ROOT));
    }

    /**
     * {@code field} ascendente o {@code -field} descendente; los valores iguales mantienen un orden estable por id.
     */
    private static Comparator<SwapiItem> comparatorFor(SwapiResource resource, String sort) {
        if (sort == null) {
            return null;
        }
        boolean descending = sort.startsWith("-");
        String name = descending ? sort.substring(1) : sort;
        SortField field = resource.sortFields().stream().filter(candidate -> candidate.name().equals(name)).findFirst().orElseThrow(() -> new InvalidSortException("sort must be one of: " + allowedSortFields(resource) + " (prefix with - for descending)"));
        return field.comparator(descending).thenComparingLong(SwapiQueryEngine::numericId);
    }

    private static String allowedSortFields(SwapiResource resource) {
        return resource.sortFields().stream().map(SortField::name).collect(Collectors.joining(", "));
    }

    private static long numericId(SwapiItem item) {
        try {
            return Long.parseLong(SwapiFields.idOf(item));
        } catch (NumberFormatException e) {
            return Long.MAX_VALUE;
        }
    }

    public <T> PageResponse<T> listPage(SwapiResource resource, ListQuery query, SwapiItemMapper<T> mapper) {
        log.debug("Listing: resource={} page={} id={} name={} sort={}", resource.path(), query.page(), query.id(), loggable(query.name()), query.sort());
        Comparator<SwapiItem> comparator = comparatorFor(resource, query.sort());
        Map<String, SwapiItem> itemsById = loader.load(resource);
        Stream<SwapiItem> items = query.id() == null ? itemsById.values().stream() : Stream.ofNullable(itemsById.get(String.valueOf(query.id())));
        items = items.filter(item -> matchesName(resource, item, query.name()));
        if (comparator != null) {
            items = items.sorted(comparator);
        }
        return PageResponse.of(items.toList(), query.page(), pageSize).map(mapper::map);
    }
}
