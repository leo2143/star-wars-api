package com.challenge.starwars.mappers;

import static org.assertj.core.api.Assertions.assertThat;

import com.challenge.starwars.dtos.films.FilmMinDto;
import com.challenge.starwars.dtos.swapi.SwapiItem;
import com.challenge.starwars.dtos.vehicles.VehicleDetailDto;
import com.challenge.starwars.dtos.vehicles.VehicleDto;
import com.challenge.starwars.dtos.vehicles.VehicleMinDto;
import com.challenge.starwars.support.Fixtures;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class VehiclesMapperTest {

    private final VehiclesMapper mapper = new VehiclesMapper();
    private final SwapiItem crawler = Fixtures.single("vehicle-4");

    @Test
    void mapsVehiclePreservingWhitespaceOfOriginalValues() {
        VehicleDto vehicle = mapper.map(crawler);

        assertThat(vehicle.id()).isEqualTo("4");
        assertThat(vehicle.name()).isEqualTo("Sand Crawler");
        assertThat(vehicle.vehicleClass()).isEqualTo("wheeled");
        assertThat(vehicle.length()).isEqualTo("36.8 ");
    }

    @Test
    void listDtoCarriesNeitherFilmIdsNorPilots() {
        assertThat(Arrays.stream(VehicleDto.class.getRecordComponents()).map(c -> c.getName()))
                .containsExactly("id", "name", "model", "vehicleClass", "manufacturer", "costInCredits", "length",
                        "crew", "passengers", "maxAtmospheringSpeed", "cargoCapacity", "consumables");
    }

    @Test
    void toMinKeepsNameAndModel() {
        assertThat(mapper.toMin(crawler)).isEqualTo(new VehicleMinDto("4", "Sand Crawler", "Digger Crawler"));
    }

    @Test
    void toDetailRepeatsEveryListFieldAndAttachesTheFilms() {
        List<FilmMinDto> films = List.of(new FilmMinDto("1", "A New Hope", 4, "1977-05-25"));

        VehicleDetailDto detail = mapper.toDetail(crawler, films);

        VehicleDto base = mapper.map(crawler);
        assertThat(detail.id()).isEqualTo(base.id());
        assertThat(detail.name()).isEqualTo(base.name());
        assertThat(detail.model()).isEqualTo(base.model());
        assertThat(detail.vehicleClass()).isEqualTo(base.vehicleClass());
        assertThat(detail.manufacturer()).isEqualTo(base.manufacturer());
        assertThat(detail.costInCredits()).isEqualTo(base.costInCredits());
        assertThat(detail.length()).isEqualTo(base.length());
        assertThat(detail.crew()).isEqualTo(base.crew());
        assertThat(detail.passengers()).isEqualTo(base.passengers());
        assertThat(detail.maxAtmospheringSpeed()).isEqualTo(base.maxAtmospheringSpeed());
        assertThat(detail.cargoCapacity()).isEqualTo(base.cargoCapacity());
        assertThat(detail.consumables()).isEqualTo(base.consumables());
        assertThat(detail.films()).isEqualTo(films);
    }
}
