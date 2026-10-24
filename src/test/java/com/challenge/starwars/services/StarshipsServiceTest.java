package com.challenge.starwars.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.challenge.starwars.dtos.starships.StarshipDetailDto;
import com.challenge.starwars.exceptions.ResourceNotFoundException;
import com.challenge.starwars.mappers.FilmsMapper;
import com.challenge.starwars.mappers.StarshipsMapper;
import com.challenge.starwars.support.Fixtures;
import com.challenge.starwars.support.Resources;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StarshipsServiceTest {

    @Mock
    private SwapiQueryEngine engine;
    @Mock
    private SwapiCatalogLoader loader;

    private StarshipsService service;

    @BeforeEach
    void setUp() {
        service = new StarshipsService(engine, new StarshipsMapper(), new SwapiCatalog(loader), new FilmsMapper());
    }

    @Test
    void detailResolvesTheFilmsOfTheStarship() {
        when(loader.load(Resources.STARSHIPS)).thenReturn(Fixtures.catalog("starships-catalog"));
        when(loader.load(Resources.FILMS)).thenReturn(Fixtures.filmsCatalog());

        StarshipDetailDto falcon = service.detail(10);

        assertThat(falcon.name()).isEqualTo("Millennium Falcon");
        assertThat(falcon.films()).extracting("title")
                .containsExactly("A New Hope", "The Empire Strikes Back", "Return of the Jedi");
    }

    @Test
    void unknownStarshipIsNotFound() {
        when(loader.load(Resources.STARSHIPS)).thenReturn(Fixtures.catalog("starships-catalog"));

        assertThatThrownBy(() -> service.detail(9999)).isInstanceOf(ResourceNotFoundException.class);
    }
}
