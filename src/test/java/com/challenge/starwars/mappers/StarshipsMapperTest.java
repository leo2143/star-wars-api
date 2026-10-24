package com.challenge.starwars.mappers;

import static org.assertj.core.api.Assertions.assertThat;

import com.challenge.starwars.dtos.films.FilmMinDto;
import com.challenge.starwars.dtos.starships.StarshipDetailDto;
import com.challenge.starwars.dtos.starships.StarshipDto;
import com.challenge.starwars.dtos.starships.StarshipMinDto;
import com.challenge.starwars.dtos.swapi.SwapiItem;
import com.challenge.starwars.support.Fixtures;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class StarshipsMapperTest {

    private final StarshipsMapper mapper = new StarshipsMapper();
    private final SwapiItem deathStar = Fixtures.single("starship-9");

    @Test
    void mapsStarshipPreservingOriginalTextValues() {
        StarshipDto starship = mapper.map(deathStar);

        assertThat(starship.id()).isEqualTo("9");
        assertThat(starship.name()).isEqualTo("Death Star");
        assertThat(starship.starshipClass()).isEqualTo("Deep Space Mobile Battlestation");
        assertThat(starship.hyperdriveRating()).isEqualTo("4.0");
        assertThat(starship.mglt()).isEqualTo("10");
        assertThat(starship.maxAtmospheringSpeed()).isEqualTo("n/a");
    }

    @Test
    void listDtoCarriesNeitherFilmIdsNorPilots() {
        assertThat(Arrays.stream(StarshipDto.class.getRecordComponents()).map(c -> c.getName()))
                .containsExactly("id", "name", "model", "starshipClass", "manufacturer", "costInCredits", "length",
                        "crew", "passengers", "maxAtmospheringSpeed", "hyperdriveRating", "mglt", "cargoCapacity",
                        "consumables");
    }

    @Test
    void toMinKeepsNameModelAndClass() {
        assertThat(mapper.toMin(deathStar)).isEqualTo(new StarshipMinDto("9", "Death Star",
                "DS-1 Orbital Battle Station", "Deep Space Mobile Battlestation"));
    }

    @Test
    void toDetailRepeatsEveryListFieldAndAttachesTheFilms() {
        List<FilmMinDto> films = List.of(new FilmMinDto("1", "A New Hope", 4, "1977-05-25"));

        StarshipDetailDto detail = mapper.toDetail(deathStar, films);

        StarshipDto base = mapper.map(deathStar);
        assertThat(detail.id()).isEqualTo(base.id());
        assertThat(detail.name()).isEqualTo(base.name());
        assertThat(detail.model()).isEqualTo(base.model());
        assertThat(detail.starshipClass()).isEqualTo(base.starshipClass());
        assertThat(detail.manufacturer()).isEqualTo(base.manufacturer());
        assertThat(detail.costInCredits()).isEqualTo(base.costInCredits());
        assertThat(detail.length()).isEqualTo(base.length());
        assertThat(detail.crew()).isEqualTo(base.crew());
        assertThat(detail.passengers()).isEqualTo(base.passengers());
        assertThat(detail.maxAtmospheringSpeed()).isEqualTo(base.maxAtmospheringSpeed());
        assertThat(detail.hyperdriveRating()).isEqualTo(base.hyperdriveRating());
        assertThat(detail.mglt()).isEqualTo(base.mglt());
        assertThat(detail.cargoCapacity()).isEqualTo(base.cargoCapacity());
        assertThat(detail.consumables()).isEqualTo(base.consumables());
        assertThat(detail.films()).isEqualTo(films);
    }
}
