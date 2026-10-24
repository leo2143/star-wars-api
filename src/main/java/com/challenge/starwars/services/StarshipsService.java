package com.challenge.starwars.services;

import com.challenge.starwars.clients.SortField;
import com.challenge.starwars.clients.SwapiResource;
import com.challenge.starwars.dtos.common.ListQuery;
import com.challenge.starwars.dtos.common.PageResponse;
import com.challenge.starwars.dtos.starships.StarshipDetailDto;
import com.challenge.starwars.dtos.starships.StarshipDto;
import com.challenge.starwars.dtos.swapi.SwapiItem;
import com.challenge.starwars.exceptions.ResourceNotFoundException;
import com.challenge.starwars.mappers.FilmsMapper;
import com.challenge.starwars.mappers.StarshipsMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.challenge.starwars.mappers.SwapiFields.relatedIds;

/**
 * Punto de entrada para el listado y detalle de starships. El listado delega en el {@link
 * SwapiQueryEngine} compartido; el detalle resuelve los films de la nave contra los catálogos cacheados.
 */
@Service
@RequiredArgsConstructor
public class StarshipsService {

    /**
     * Descriptor del origen de este recurso, incluidos los campos por los que su listado puede ordenarse.
     */
    public static final SwapiResource RESOURCE = new SwapiResource("starships", "name", true,
            List.of(SortField.text("name"), SortField.number("costInCredits"), SortField.number("length"),
                    SortField.number("crew"), SortField.number("passengers"), SortField.number("maxAtmospheringSpeed"),
                    SortField.number("hyperdriveRating"), SortField.number("mglt", "MGLT"), SortField.number("cargoCapacity")));

    private final SwapiQueryEngine engine;
    private final StarshipsMapper mapper;
    private final SwapiCatalog catalog;
    private final FilmsMapper filmsMapper;

    public PageResponse<StarshipDto> list(ListQuery query) {
        return engine.listPage(RESOURCE, query, mapper);
    }

    public StarshipDetailDto detail(long id) {
        SwapiItem starship = catalog.lookup(RESOURCE, String.valueOf(id))
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE.path(), id));
        return mapper.toDetail(starship,
                catalog.resolveRelated(FilmsService.RESOURCE, relatedIds(starship, "films"), filmsMapper::toMin));
    }
}
