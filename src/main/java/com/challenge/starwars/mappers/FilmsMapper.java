package com.challenge.starwars.mappers;

import com.challenge.starwars.dtos.films.FilmDetailDto;
import com.challenge.starwars.dtos.films.FilmDto;
import com.challenge.starwars.dtos.films.FilmMinDto;
import com.challenge.starwars.dtos.people.PersonMinDto;
import com.challenge.starwars.dtos.starships.StarshipMinDto;
import com.challenge.starwars.dtos.swapi.SwapiItem;
import com.challenge.starwars.dtos.vehicles.VehicleMinDto;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.challenge.starwars.mappers.SwapiFields.idOf;
import static com.challenge.starwars.mappers.SwapiFields.parseIntOrNull;

/**
 * Mapea un elemento film de swapi.tech a sus DTOs. Es puro: los elementos relacionados llegan ya mapeados, por lo que este mapper
 * nunca depende de otro.
 */
@Component
public class FilmsMapper implements SwapiItemMapper<FilmDto> {

    @Override
    public FilmDto map(SwapiItem item) {
        return new FilmDto(idOf(item), item.text("title"), parseIntOrNull(item.text("episode_id")), item.text("director"), item.text("producer"), item.text("release_date"), item.text("opening_crawl"));
    }


    public FilmMinDto toMin(SwapiItem item) {
        return new FilmMinDto(idOf(item), item.text("title"), parseIntOrNull(item.text("episode_id")), item.text("release_date"));
    }

    public FilmDetailDto toDetail(SwapiItem item, List<PersonMinDto> characters, List<StarshipMinDto> starships, List<VehicleMinDto> vehicles) {
        FilmDto film = map(item);
        return new FilmDetailDto(film.id(), film.title(), film.episodeId(), film.director(), film.producer(), film.releaseDate(), film.openingCrawl(), characters, starships, vehicles);
    }
}
