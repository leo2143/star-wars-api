package com.challenge.starwars.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.challenge.starwars.dtos.swapi.SwapiItem;
import com.challenge.starwars.support.Fixtures;
import com.challenge.starwars.support.Resources;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SwapiCatalogTest {

    @Mock
    private SwapiCatalogLoader loader;
    @InjectMocks
    private SwapiCatalog catalog;

    @Test
    void findsAnItemByIdInTheLoadedCollection() {
        when(loader.load(Resources.STARSHIPS)).thenReturn(Fixtures.catalog("starships-catalog"));

        assertThat(catalog.lookup(Resources.STARSHIPS, "9")).map(item -> item.text("name")).contains("Death Star");
        assertThat(catalog.lookup(Resources.STARSHIPS, "999")).isEmpty();
    }

    @Test
    void resolvesIdsInTheGivenOrderAndSkipsTheUnknownOnes() {
        when(loader.load(Resources.STARSHIPS)).thenReturn(Fixtures.catalog("starships-catalog"));

        List<String> names = catalog.resolveRelated(Resources.STARSHIPS, List.of("12", "22", "2"),
                item -> item.text("name"));

        assertThat(names).containsExactly("X-wing", "CR90 corvette");
    }

    @Test
    void anEmptyRelationNeverLoadsTheCatalog() {
        List<SwapiItem> none = catalog.resolveRelated(Resources.VEHICLES, List.of(), item -> item);

        assertThat(none).isEmpty();
        verifyNoInteractions(loader);
    }

    @Test
    void theCatalogIsLoadedOncePerResolveNotOncePerId() {
        when(loader.load(Resources.FILMS)).thenReturn(Fixtures.filmsCatalog());

        catalog.resolveRelated(Resources.FILMS, List.of("1", "2", "3"), item -> item);

        verify(loader).load(Resources.FILMS);
    }
}
