package com.challenge.starwars.mappers;

import com.challenge.starwars.dtos.films.FilmMinDto;
import com.challenge.starwars.dtos.people.PersonDetailDto;
import com.challenge.starwars.dtos.people.PersonDto;
import com.challenge.starwars.dtos.people.PersonMinDto;
import com.challenge.starwars.dtos.starships.StarshipMinDto;
import com.challenge.starwars.dtos.swapi.SwapiItem;
import com.challenge.starwars.dtos.vehicles.VehicleMinDto;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.challenge.starwars.mappers.SwapiFields.idOf;

/**
 * Mapea un elemento person de swapi.tech a sus DTOs. Es puro: los elementos relacionados llegan ya mapeados, por lo que este mapper
 * nunca depende de otro.
 */
@Component
public class PeopleMapper implements SwapiItemMapper<PersonDto> {

    @Override
    public PersonDto map(SwapiItem item) {
        return new PersonDto(idOf(item), item.text("name"), item.text("height"), item.text("mass"), item.text("hair_color"), item.text("skin_color"), item.text("eye_color"), item.text("birth_year"), item.text("gender"));
    }

    public PersonMinDto toMin(SwapiItem item) {
        return new PersonMinDto(idOf(item), item.text("name"), item.text("gender"));
    }

    public PersonDetailDto toDetail(SwapiItem item, List<FilmMinDto> films, List<StarshipMinDto> starships, List<VehicleMinDto> vehicles) {
        PersonDto person = map(item);
        return new PersonDetailDto(person.id(), person.name(), person.height(), person.mass(), person.hairColor(), person.skinColor(), person.eyeColor(), person.birthYear(), person.gender(), films, starships, vehicles);
    }
}
