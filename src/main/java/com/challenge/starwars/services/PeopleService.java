package com.challenge.starwars.services;

import com.challenge.starwars.clients.SortField;
import com.challenge.starwars.clients.SwapiResource;
import com.challenge.starwars.dtos.common.ListQuery;
import com.challenge.starwars.dtos.common.PageResponse;
import com.challenge.starwars.dtos.people.PersonDetailDto;
import com.challenge.starwars.dtos.people.PersonDto;
import com.challenge.starwars.dtos.swapi.SwapiItem;
import com.challenge.starwars.exceptions.ResourceNotFoundException;
import com.challenge.starwars.mappers.FilmsMapper;
import com.challenge.starwars.mappers.PeopleMapper;
import com.challenge.starwars.mappers.StarshipsMapper;
import com.challenge.starwars.mappers.VehiclesMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.challenge.starwars.mappers.SwapiFields.relatedIds;

/**
 * Punto de entrada para el listado y detalle de people. El listado delega en el {@link
 * SwapiQueryEngine} compartido; el detalle resuelve las relaciones de la persona contra los catálogos cacheados.
 */
@Service
@RequiredArgsConstructor
public class PeopleService {

    /**
     * Descriptor del origen de este recurso, incluidos los campos por los que su listado puede ordenarse.
     */
    public static final SwapiResource RESOURCE = new SwapiResource("people", "name", true,
            List.of(SortField.text("name"), SortField.number("height"), SortField.number("mass")));

    private final SwapiQueryEngine engine;
    private final PeopleMapper mapper;
    private final SwapiCatalog catalog;
    private final FilmsMapper filmsMapper;
    private final StarshipsMapper starshipsMapper;
    private final VehiclesMapper vehiclesMapper;

    public PageResponse<PersonDto> list(ListQuery query) {
        return engine.listPage(RESOURCE, query, mapper);
    }

    public PersonDetailDto detail(long id) {
        SwapiItem person = catalog.lookup(RESOURCE, String.valueOf(id))
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE.path(), id));
        return mapper.toDetail(person,
                catalog.resolveRelated(FilmsService.RESOURCE, relatedIds(person, "films"), filmsMapper::toMin),
                catalog.resolveRelated(StarshipsService.RESOURCE, relatedIds(person, "starships"), starshipsMapper::toMin),
                catalog.resolveRelated(VehiclesService.RESOURCE, relatedIds(person, "vehicles"), vehiclesMapper::toMin));
    }
}
