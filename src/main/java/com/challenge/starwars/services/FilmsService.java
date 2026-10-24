package com.challenge.starwars.services;

import com.challenge.starwars.clients.SortField;
import com.challenge.starwars.clients.SwapiResource;
import com.challenge.starwars.dtos.common.ListQuery;
import com.challenge.starwars.dtos.common.PageResponse;
import com.challenge.starwars.dtos.films.FilmDetailDto;
import com.challenge.starwars.dtos.films.FilmDto;
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
 * Punto de entrada para el listado y detalle de films. El listado delega en el {@link
 * SwapiQueryEngine} compartido; el detalle resuelve las relaciones del film contra los catálogos cacheados.
 */
@Service
@RequiredArgsConstructor
public class FilmsService {

    /**
     * Descriptor del origen de este recurso, incluidos los campos por los que su listado puede ordenarse.
     */
    public static final SwapiResource RESOURCE = new SwapiResource("films", "title", false,
            List.of(SortField.text("title"), SortField.number("episodeId"), SortField.text("releaseDate")));

    private final SwapiQueryEngine engine;
    private final FilmsMapper mapper;
    private final SwapiCatalog catalog;
    private final PeopleMapper peopleMapper;
    private final StarshipsMapper starshipsMapper;
    private final VehiclesMapper vehiclesMapper;

    public PageResponse<FilmDto> list(ListQuery query) {
        return engine.listPage(RESOURCE, query, mapper);
    }

    public FilmDetailDto detail(long id) {
        SwapiItem film = catalog.lookup(RESOURCE, String.valueOf(id))
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE.path(), id));
        return mapper.toDetail(film,
                catalog.resolveRelated(PeopleService.RESOURCE, relatedIds(film, "characters"), peopleMapper::toMin),
                catalog.resolveRelated(StarshipsService.RESOURCE, relatedIds(film, "starships"), starshipsMapper::toMin),
                catalog.resolveRelated(VehiclesService.RESOURCE, relatedIds(film, "vehicles"), vehiclesMapper::toMin));
    }
}
