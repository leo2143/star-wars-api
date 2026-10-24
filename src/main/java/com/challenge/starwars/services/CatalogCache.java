package com.challenge.starwars.services;

/**
 * Control operativo de los catálogos cacheados de swapi.tech.
 */
public interface CatalogCache {

    /**
     * Descarta todos los catálogos: el siguiente acceso a cada recurso vuelve a ser una carga en frío.
     */
    void invalidateAll();
}
