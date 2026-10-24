package com.challenge.starwars.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.challenge.starwars.dtos.people.PersonDetailDto;
import com.challenge.starwars.dtos.swapi.SwapiItem;
import com.challenge.starwars.exceptions.ResourceNotFoundException;
import com.challenge.starwars.mappers.FilmsMapper;
import com.challenge.starwars.mappers.PeopleMapper;
import com.challenge.starwars.mappers.StarshipsMapper;
import com.challenge.starwars.mappers.VehiclesMapper;
import com.challenge.starwars.support.Fixtures;
import com.challenge.starwars.support.Resources;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PeopleServiceTest {

    @Mock
    private SwapiQueryEngine engine;
    @Mock
    private SwapiCatalogLoader loader;

    private PeopleService service;

    @BeforeEach
    void setUp() {
        service = new PeopleService(engine, new PeopleMapper(), new SwapiCatalog(loader), new FilmsMapper(),
                new StarshipsMapper(), new VehiclesMapper());
    }

    private void stubCatalogs() {
        when(loader.load(Resources.PEOPLE)).thenReturn(Fixtures.catalog("people-catalog"));
        when(loader.load(Resources.FILMS)).thenReturn(Fixtures.filmsCatalog());
        when(loader.load(Resources.STARSHIPS)).thenReturn(Fixtures.catalog("starships-catalog"));
        when(loader.load(Resources.VEHICLES)).thenReturn(Fixtures.catalog("vehicles-catalog"));
    }

    @Test
    void detailResolvesFilmsStarshipsAndVehiclesAgainstTheCatalogs() {
        stubCatalogs();

        PersonDetailDto luke = service.detail(1);

        assertThat(luke.name()).isEqualTo("Luke Skywalker");
        assertThat(luke.films()).extracting("id").containsExactly("1", "2", "3", "6");
        assertThat(luke.films().getFirst().title()).isEqualTo("A New Hope");
        assertThat(luke.starships()).extracting("name").containsExactly("X-wing");
        assertThat(luke.vehicles()).extracting("name").containsExactly("Snowspeeder");
    }

    @Test
    void relatedIdsMissingFromTheCatalogsAreSkipped() {
        stubCatalogs();

        PersonDetailDto luke = service.detail(1);

        // la nave 22 y el vehículo 30 están listados por swapi pero ausentes de los catálogos
        assertThat(luke.starships()).hasSize(1);
        assertThat(luke.vehicles()).hasSize(1);
    }

    @Test
    void aPersonWithoutVehiclesOrStarshipsNeverLoadsThoseCatalogs() {
        when(loader.load(Resources.PEOPLE)).thenReturn(Fixtures.catalog("people-catalog"));
        when(loader.load(Resources.FILMS)).thenReturn(Fixtures.filmsCatalog());

        PersonDetailDto threepio = service.detail(2);

        assertThat(threepio.films()).hasSize(6);
        assertThat(threepio.starships()).isEmpty();
        assertThat(threepio.vehicles()).isEmpty();
        verify(loader).load(Resources.PEOPLE);
        verify(loader).load(Resources.FILMS);
        verifyNoMoreInteractions(loader);
    }

    @Test
    void unknownIdIsNotFound() {
        when(loader.load(Resources.PEOPLE)).thenReturn(Map.<String, SwapiItem>of());

        assertThatThrownBy(() -> service.detail(9999)).isInstanceOf(ResourceNotFoundException.class);
        verifyNoMoreInteractions(loader);
    }
}
