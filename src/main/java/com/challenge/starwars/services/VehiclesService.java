package com.challenge.starwars.services;

import com.challenge.starwars.clients.SortField;
import com.challenge.starwars.clients.SwapiResource;
import com.challenge.starwars.dtos.common.ListQuery;
import com.challenge.starwars.dtos.common.PageResponse;
import com.challenge.starwars.dtos.swapi.SwapiItem;
import com.challenge.starwars.dtos.vehicles.VehicleDetailDto;
import com.challenge.starwars.dtos.vehicles.VehicleDto;
import com.challenge.starwars.exceptions.ResourceNotFoundException;
import com.challenge.starwars.mappers.FilmsMapper;
import com.challenge.starwars.mappers.VehiclesMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.challenge.starwars.mappers.SwapiFields.relatedIds;

/**
 * Punto de entrada para el listado y detalle de vehicles. El listado delega en el {@link
 * SwapiQueryEngine} compartido; el detalle resuelve los films del vehículo contra los catálogos cacheados.
 */
@Service
@RequiredArgsConstructor
public class VehiclesService {

    /**
     * Descriptor del origen de este recurso, incluidos los campos por los que su listado puede ordenarse.
     */
    public static final SwapiResource RESOURCE = new SwapiResource("vehicles", "name", true,
            List.of(SortField.text("name"), SortField.number("costInCredits"), SortField.number("length"),
                    SortField.number("crew"), SortField.number("passengers"), SortField.number("maxAtmospheringSpeed"),
                    SortField.number("cargoCapacity")));

    private final SwapiQueryEngine engine;
    private final VehiclesMapper mapper;
    private final SwapiCatalog catalog;
    private final FilmsMapper filmsMapper;

    public PageResponse<VehicleDto> list(ListQuery query) {
        return engine.listPage(RESOURCE, query, mapper);
    }

    public VehicleDetailDto detail(long id) {
        SwapiItem vehicle = catalog.lookup(RESOURCE, String.valueOf(id))
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE.path(), id));
        return mapper.toDetail(vehicle,
                catalog.resolveRelated(FilmsService.RESOURCE, relatedIds(vehicle, "films"), filmsMapper::toMin));
    }
}
