package com.challenge.starwars.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.challenge.starwars.dtos.films.FilmDetailDto;
import com.challenge.starwars.exceptions.ResourceNotFoundException;
import com.challenge.starwars.mappers.FilmsMapper;
import com.challenge.starwars.mappers.PeopleMapper;
import com.challenge.starwars.mappers.StarshipsMapper;
import com.challenge.starwars.mappers.VehiclesMapper;
import com.challenge.starwars.support.Fixtures;
import com.challenge.starwars.support.Resources;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FilmsServiceTest {

    @Mock
    private SwapiQueryEngine engine;
    @Mock
    private SwapiCatalogLoader loader;

    private FilmsService service;

    @BeforeEach
    void setUp() {
        service = new FilmsService(engine, new FilmsMapper(), new SwapiCatalog(loader), new PeopleMapper(),
                new StarshipsMapper(), new VehiclesMapper());
    }

    @Test
    void detailResolvesCharactersStarshipsAndVehiclesInSwapiOrder() {
        when(loader.load(Resources.FILMS)).thenReturn(Fixtures.filmsCatalog());
        when(loader.load(Resources.PEOPLE)).thenReturn(Fixtures.catalog("people-catalog"));
        when(loader.load(Resources.STARSHIPS)).thenReturn(Fixtures.catalog("starships-catalog"));
        when(loader.load(Resources.VEHICLES)).thenReturn(Fixtures.catalog("vehicles-catalog"));

        FilmDetailDto hope = service.detail(1);

        assertThat(hope.title()).isEqualTo("A New Hope");
        assertThat(hope.openingCrawl()).startsWith("It is a period of civil war.");
        // El film lista muchos más personajes (hasta 81); solo se devuelven los que están en el catálogo.
        assertThat(hope.characters()).extracting("id").containsExactly("1", "2", "3", "4");
        assertThat(hope.characters().getFirst().gender()).isEqualTo("male");
        assertThat(hope.starships()).extracting("id").containsExactly("2", "9", "10", "12");
        assertThat(hope.vehicles()).extracting("id").containsExactly("4", "6");
    }

    @Test
    void unknownFilmIsNotFound() {
        when(loader.load(Resources.FILMS)).thenReturn(Fixtures.filmsCatalog());

        assertThatThrownBy(() -> service.detail(99)).isInstanceOf(ResourceNotFoundException.class);
    }
}
