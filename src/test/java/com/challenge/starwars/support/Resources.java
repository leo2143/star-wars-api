package com.challenge.starwars.support;

import com.challenge.starwars.clients.SwapiResource;
import com.challenge.starwars.services.FilmsService;
import com.challenge.starwars.services.PeopleService;
import com.challenge.starwars.services.StarshipsService;
import com.challenge.starwars.services.VehiclesService;

/** Los descriptores de recursos de producción, con nombres cortos para los tests que no tratan de una funcionalidad concreta. */
public final class Resources {

    public static final SwapiResource PEOPLE = PeopleService.RESOURCE;
    public static final SwapiResource FILMS = FilmsService.RESOURCE;
    public static final SwapiResource STARSHIPS = StarshipsService.RESOURCE;
    public static final SwapiResource VEHICLES = VehiclesService.RESOURCE;

    private Resources() {
    }
}
