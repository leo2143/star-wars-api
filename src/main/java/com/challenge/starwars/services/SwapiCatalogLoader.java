package com.challenge.starwars.services;

import com.challenge.starwars.clients.SwapiClient;
import com.challenge.starwars.clients.SwapiResource;
import com.challenge.starwars.config.SwapiProperties;
import com.challenge.starwars.dtos.swapi.SwapiItem;
import com.challenge.starwars.dtos.swapi.SwapiPage;
import com.challenge.starwars.mappers.SwapiFields;
import com.github.benmanes.caffeine.cache.CacheLoader;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.github.benmanes.caffeine.cache.Ticker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Carga y cachea la colección completa de un recurso de swapi.tech, indexada por id.
 *
 * <p>La caché es una {@link LoadingCache} de Caffeine propia de esta clase, con política stale-while-revalidate:
 * <ul>
 *   <li>Carga en frío: las peticiones concurrentes comparten una sola carga, y una carga que lanza una excepción
 *   no se cachea, por lo que los errores del origen se propagan tal cual y se reintentan en la siguiente petición.</li>
 *   <li>Pasado {@code app.swapi.catalog-ttl} el catálogo no expira: el siguiente acceso sigue recibiendo el catálogo
 *   cacheado y dispara un refresco en segundo plano ({@code refreshAfterWrite}).</li>
 *   <li>Si el refresco falla se registra un único aviso y se conserva el catálogo anterior como si acabara de
 *   refrescarse: el siguiente intento ocurre pasado otro {@code catalog-ttl}, no en el siguiente acceso. Como máximo
 *   hay un intento de refresco por recurso e intervalo.</li>
 *   <li>Los refrescos pasan por el circuit breaker de {@link SwapiClient}, como cualquier otra llamada saliente, y
 *   corren en un executor propio de hilos virtuales para no ocupar el pool común con E/S bloqueante.</li>
 * </ul>
 * No hay expiración dura; el tamaño está acotado.
 */
@Component
class SwapiCatalogLoader implements CatalogCache {

    /**
     * Tamaño máximo de página de swapi.tech
     */
    static final int PAGE_LIMIT = 100;

    /**
     * Una entrada por recurso; el límite solo protege contra una futura explosión de claves.
     */
    static final int MAX_ENTRIES = 16;

    private static final Logger log = LoggerFactory.getLogger(SwapiCatalogLoader.class);

    private final SwapiClient swapiClient;
    private final LoadingCache<SwapiResource, Map<String, SwapiItem>> catalogsByResource;

    /**
     * Executor creado por esta clase y que ella cierra; es {@code null} cuando el executor viene inyectado.
     */
    private final ExecutorService ownedRefreshExecutor;

    @Autowired
    SwapiCatalogLoader(SwapiClient swapiClient, SwapiProperties properties) {
        this(swapiClient, properties.catalogTtl(), Executors.newVirtualThreadPerTaskExecutor(), Ticker.systemTicker(),
                true);
    }

    /**
     * Constructor con executor y reloj inyectables para pruebas deterministas del refresco.
     */
    SwapiCatalogLoader(SwapiClient swapiClient, Duration refreshInterval, Executor refreshExecutor, Ticker ticker) {
        this(swapiClient, refreshInterval, refreshExecutor, ticker, false);
    }

    private SwapiCatalogLoader(SwapiClient swapiClient, Duration refreshInterval, Executor refreshExecutor,
                               Ticker ticker, boolean ownsRefreshExecutor) {
        this.ownedRefreshExecutor = ownsRefreshExecutor ? (ExecutorService) refreshExecutor : null;
        this.swapiClient = swapiClient;
        this.catalogsByResource = Caffeine.newBuilder()
                .maximumSize(MAX_ENTRIES)
                .refreshAfterWrite(refreshInterval)
                .executor(refreshExecutor)
                .ticker(ticker)
                .build(new CatalogCacheLoader());
    }

    private static void logLoaded(SwapiResource resource, int items, long startNanos) {
        log.debug("Catalog for {} loaded: {} items in {} ms", resource.path(), items,
                (System.nanoTime() - startNanos) / 1_000_000);
    }

    /**
     * Todos los elementos del recurso en el orden del origen. Los films vienen de su única llamada sin paginar.
     */
    Map<String, SwapiItem> load(SwapiResource resource) {
        return catalogsByResource.get(resource);
    }

    @PreDestroy
    void shutdownRefreshExecutor() {
        if (ownedRefreshExecutor != null) {
            ownedRefreshExecutor.shutdownNow();
        }
    }

    @Override
    public void invalidateAll() {
        catalogsByResource.invalidateAll();
    }

    private Map<String, SwapiItem> fetchCatalog(SwapiResource resource) {
        long start = System.nanoTime();
        Map<String, SwapiItem> itemsById = new LinkedHashMap<>();
        if (!resource.remotePaginated()) {
            swapiClient.fetchAll(resource).forEach(item -> itemsById.put(SwapiFields.idOf(item), item));
            logLoaded(resource, itemsById.size(), start);
            return Collections.unmodifiableMap(itemsById);
        }
        int page = 1;
        int totalPages;
        do {
            SwapiPage fetched = swapiClient.fetchPage(resource, page, PAGE_LIMIT);
            fetched.items().forEach(item -> itemsById.put(SwapiFields.idOf(item), item));
            totalPages = fetched.totalPages();
            page++;
        } while (page <= totalPages);
        logLoaded(resource, itemsById.size(), start);
        return Collections.unmodifiableMap(itemsById);
    }

    private final class CatalogCacheLoader implements CacheLoader<SwapiResource, Map<String, SwapiItem>> {

        @Override
        public Map<String, SwapiItem> load(SwapiResource resource) {
            log.debug("Catalog cache miss for {}: loading from SWAPI", resource.path());
            return fetchCatalog(resource);
        }

        /**
         * Refresco en segundo plano. Si falla, devuelve el catálogo anterior en lugar de propagar la excepción: Caffeine
         * lo trata como un refresco exitoso y reinicia la marca de tiempo, por lo que el siguiente intento ocurre
         * pasado otro intervalo. Propagarla dejaría la marca original y cada acceso dispararía un refresco nuevo
         * mientras SWAPI está caído. Al no lanzar, Caffeine tampoco registra el fallo por su cuenta.
         */
        @Override
        public Map<String, SwapiItem> reload(SwapiResource resource, Map<String, SwapiItem> staleCatalog) {
            log.debug("Catalog for {} is past its refresh interval: reloading from SWAPI", resource.path());
            try {
                return fetchCatalog(resource);
            } catch (RuntimeException failure) {
                log.warn("Catalog refresh for {} failed, keeping the stale catalog until the next refresh interval: {}",
                        resource.path(), failure.toString());
                return staleCatalog;
            }
        }
    }
}
