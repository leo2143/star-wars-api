package com.challenge.starwars.mappers;

import static org.assertj.core.api.Assertions.assertThat;

import com.challenge.starwars.dtos.films.FilmMinDto;
import com.challenge.starwars.dtos.people.PersonDetailDto;
import com.challenge.starwars.dtos.people.PersonDto;
import com.challenge.starwars.dtos.people.PersonMinDto;
import com.challenge.starwars.dtos.starships.StarshipMinDto;
import com.challenge.starwars.dtos.swapi.SwapiItem;
import com.challenge.starwars.dtos.vehicles.VehicleMinDto;
import com.challenge.starwars.support.Fixtures;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PeopleMapperTest {

    private final PeopleMapper mapper = new PeopleMapper();

    @Test
    void mapsPersonFromExpandedListItemWithoutRelationIds() {
        SwapiItem luke = Fixtures.list("people-list-expanded").results().getFirst();

        PersonDto person = mapper.map(luke);

        assertThat(person.id()).isEqualTo("1");
        assertThat(person.name()).isEqualTo("Luke Skywalker");
        assertThat(person.birthYear()).isEqualTo("19BBY");
        assertThat(person.toString()).doesNotContain("swapi.tech");
    }

    @Test
    void listDtoCarriesNoRelationIds() {
        assertThat(Arrays.stream(PersonDto.class.getRecordComponents()).map(c -> c.getName()))
                .containsExactly("id", "name", "height", "mass", "hairColor", "skinColor", "eyeColor", "birthYear",
                        "gender");
    }

    @Test
    void mapsPersonFromSingleResourceResponse() {
        PersonDto person = mapper.map(Fixtures.single("person-1"));

        assertThat(person.id()).isEqualTo("1");
        assertThat(person.name()).isEqualTo("Luke Skywalker");
    }

    @Test
    void keepsUnknownLiteralsAndTurnsMissingAttributesIntoNull() {
        SwapiItem item = new SwapiItem("3", null, Map.of("name", "R2-D2", "mass", "unknown"));

        PersonDto person = mapper.map(item);

        assertThat(person.mass()).isEqualTo("unknown");
        assertThat(person.height()).isNull();
    }

    @Test
    void toMinKeepsOnlyIdentityAndGender() {
        PersonMinDto min = mapper.toMin(Fixtures.single("person-1"));

        assertThat(min).isEqualTo(new PersonMinDto("1", "Luke Skywalker", "male"));
    }

    @Test
    void toDetailRepeatsEveryListFieldAndAttachesTheResolvedRelations() {
        SwapiItem luke = Fixtures.single("person-1");
        List<FilmMinDto> films = List.of(new FilmMinDto("1", "A New Hope", 4, "1977-05-25"));
        List<StarshipMinDto> starships = List.of(new StarshipMinDto("12", "X-wing", "T-65 X-wing", "Starfighter"));
        List<VehicleMinDto> vehicles = List.of(new VehicleMinDto("14", "Snowspeeder", "t-47 airspeeder"));

        PersonDetailDto detail = mapper.toDetail(luke, films, starships, vehicles);

        PersonDto base = mapper.map(luke);
        assertThat(detail.id()).isEqualTo(base.id());
        assertThat(detail.name()).isEqualTo(base.name());
        assertThat(detail.height()).isEqualTo(base.height());
        assertThat(detail.mass()).isEqualTo(base.mass());
        assertThat(detail.hairColor()).isEqualTo(base.hairColor());
        assertThat(detail.skinColor()).isEqualTo(base.skinColor());
        assertThat(detail.eyeColor()).isEqualTo(base.eyeColor());
        assertThat(detail.birthYear()).isEqualTo(base.birthYear());
        assertThat(detail.gender()).isEqualTo(base.gender());
        assertThat(detail.films()).isEqualTo(films);
        assertThat(detail.starships()).isEqualTo(starships);
        assertThat(detail.vehicles()).isEqualTo(vehicles);
    }
}
