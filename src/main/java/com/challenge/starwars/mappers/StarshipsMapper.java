package com.challenge.starwars.mappers;

import com.challenge.starwars.dtos.films.FilmMinDto;
import com.challenge.starwars.dtos.starships.StarshipDetailDto;
import com.challenge.starwars.dtos.starships.StarshipDto;
import com.challenge.starwars.dtos.starships.StarshipMinDto;
import com.challenge.starwars.dtos.swapi.SwapiItem;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.challenge.starwars.mappers.SwapiFields.idOf;

/**
 * Mapea un elemento starship de swapi.tech a sus DTOs. Es puro: los elementos relacionados llegan ya mapeados, por lo que este mapper
 * nunca depende de otro.
 */
@Component
public class StarshipsMapper implements SwapiItemMapper<StarshipDto> {

    @Override
    public StarshipDto map(SwapiItem item) {
        return new StarshipDto(idOf(item), item.text("name"), item.text("model"), item.text("starship_class"), item.text("manufacturer"), item.text("cost_in_credits"), item.text("length"), item.text("crew"), item.text("passengers"), item.text("max_atmosphering_speed"), item.text("hyperdrive_rating"), item.text("MGLT"), item.text("cargo_capacity"), item.text("consumables"));
    }

    public StarshipMinDto toMin(SwapiItem item) {
        return new StarshipMinDto(idOf(item), item.text("name"), item.text("model"), item.text("starship_class"));
    }

    public StarshipDetailDto toDetail(SwapiItem item, List<FilmMinDto> films) {
        StarshipDto ship = map(item);
        return new StarshipDetailDto(ship.id(), ship.name(), ship.model(), ship.starshipClass(), ship.manufacturer(), ship.costInCredits(), ship.length(), ship.crew(), ship.passengers(), ship.maxAtmospheringSpeed(), ship.hyperdriveRating(), ship.mglt(), ship.cargoCapacity(), ship.consumables(), films);
    }
}
