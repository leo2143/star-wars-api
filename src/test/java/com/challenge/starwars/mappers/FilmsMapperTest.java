package com.challenge.starwars.mappers;

import static org.assertj.core.api.Assertions.assertThat;

import com.challenge.starwars.dtos.films.FilmDetailDto;
import com.challenge.starwars.dtos.films.FilmDto;
import com.challenge.starwars.dtos.films.FilmMinDto;
import com.challenge.starwars.dtos.people.PersonMinDto;
import com.challenge.starwars.dtos.starships.StarshipMinDto;
import com.challenge.starwars.dtos.swapi.SwapiItem;
import com.challenge.starwars.dtos.vehicles.VehicleMinDto;
import com.challenge.starwars.support.Fixtures;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class FilmsMapperTest {

    private final FilmsMapper mapper = new FilmsMapper();
    private final SwapiItem hope = Fixtures.search("films-search").getFirst();

    @Test
    void mapsFilmUsingTitleAndNumericEpisode() {
        FilmDto film = mapper.map(hope);

        assertThat(film.id()).isEqualTo("1");
        assertThat(film.title()).isEqualTo("A New Hope");
        assertThat(film.episodeId()).isEqualTo(4);
        assertThat(film.director()).isEqualTo("George Lucas");
        assertThat(film.releaseDate()).isEqualTo("1977-05-25");
        assertThat(film.openingCrawl()).startsWith("It is a period of civil war.");
    }

    @Test
    void listDtoKeepsTheCrawlButCarriesNoRelationIds() {
        assertThat(Arrays.stream(FilmDto.class.getRecordComponents()).map(c -> c.getName()))
                .containsExactly("id", "title", "episodeId", "director", "producer", "releaseDate", "openingCrawl");
    }

    @Test
    void toMinKeepsTitleEpisodeAndReleaseDate() {
        assertThat(mapper.toMin(hope)).isEqualTo(new FilmMinDto("1", "A New Hope", 4, "1977-05-25"));
    }

    @Test
    void toDetailRepeatsEveryListFieldAndAttachesTheResolvedRelations() {
        List<PersonMinDto> characters = List.of(new PersonMinDto("1", "Luke Skywalker", "male"));
        List<StarshipMinDto> starships = List.of(new StarshipMinDto("2", "CR90 corvette", "CR90 corvette", "corvette"));
        List<VehicleMinDto> vehicles = List.of(new VehicleMinDto("4", "Sand Crawler", "Digger Crawler"));

        FilmDetailDto detail = mapper.toDetail(hope, characters, starships, vehicles);

        FilmDto base = mapper.map(hope);
        assertThat(detail.id()).isEqualTo(base.id());
        assertThat(detail.title()).isEqualTo(base.title());
        assertThat(detail.episodeId()).isEqualTo(base.episodeId());
        assertThat(detail.director()).isEqualTo(base.director());
        assertThat(detail.producer()).isEqualTo(base.producer());
        assertThat(detail.releaseDate()).isEqualTo(base.releaseDate());
        assertThat(detail.openingCrawl()).isEqualTo(base.openingCrawl());
        assertThat(detail.characters()).isEqualTo(characters);
        assertThat(detail.starships()).isEqualTo(starships);
        assertThat(detail.vehicles()).isEqualTo(vehicles);
    }
}
