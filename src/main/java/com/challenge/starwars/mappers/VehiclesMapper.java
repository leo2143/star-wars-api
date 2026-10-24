package com.challenge.starwars.mappers;

import com.challenge.starwars.dtos.films.FilmMinDto;
import com.challenge.starwars.dtos.swapi.SwapiItem;
import com.challenge.starwars.dtos.vehicles.VehicleDetailDto;
import com.challenge.starwars.dtos.vehicles.VehicleDto;
import com.challenge.starwars.dtos.vehicles.VehicleMinDto;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.challenge.starwars.mappers.SwapiFields.idOf;

/**
 * Mapea un elemento vehicle de swapi.tech a sus DTOs. Es puro: los elementos relacionados llegan ya mapeados, por lo que este mapper
 * nunca depende de otro.
 */
@Component
public class VehiclesMapper implements SwapiItemMapper<VehicleDto> {

    @Override
    public VehicleDto map(SwapiItem item) {
        return new VehicleDto(idOf(item), item.text("name"), item.text("model"), item.text("vehicle_class"), item.text("manufacturer"), item.text("cost_in_credits"), item.text("length"), item.text("crew"), item.text("passengers"), item.text("max_atmosphering_speed"), item.text("cargo_capacity"), item.text("consumables"));
    }

    public VehicleMinDto toMin(SwapiItem item) {
        return new VehicleMinDto(idOf(item), item.text("name"), item.text("model"));
    }

    public VehicleDetailDto toDetail(SwapiItem item, List<FilmMinDto> films) {
        VehicleDto vehicle = map(item);
        return new VehicleDetailDto(vehicle.id(), vehicle.name(), vehicle.model(), vehicle.vehicleClass(), vehicle.manufacturer(), vehicle.costInCredits(), vehicle.length(), vehicle.crew(), vehicle.passengers(), vehicle.maxAtmospheringSpeed(), vehicle.cargoCapacity(), vehicle.consumables(), films);
    }
}
