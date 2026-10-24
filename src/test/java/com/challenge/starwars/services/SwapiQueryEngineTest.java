package com.challenge.starwars.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.challenge.starwars.config.SwapiProperties;
import com.challenge.starwars.dtos.common.ListQuery;
import com.challenge.starwars.dtos.common.PageResponse;
import com.challenge.starwars.dtos.films.FilmDto;
import com.challenge.starwars.dtos.people.PersonDto;
import com.challenge.starwars.dtos.starships.StarshipDto;
import com.challenge.starwars.dtos.swapi.SwapiItem;
import com.challenge.starwars.exceptions.InvalidSortException;
import com.challenge.starwars.exceptions.SwapiUnavailableException;
import com.challenge.starwars.mappers.FilmsMapper;
import com.challenge.starwars.mappers.PeopleMapper;
import com.challenge.starwars.mappers.StarshipsMapper;
import com.challenge.starwars.support.Fixtures;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
/** Filtrado, ordenamiento y paginación del motor compartido sobre un catálogo cargado, con los descriptores y mappers reales. */
class SwapiQueryEngineTest {

    @Mock
    private SwapiCatalogLoader loader;

    private SwapiQueryEngine engine;

    @BeforeEach
    void setUp() {
        engine = new SwapiQueryEngine(loader, new SwapiProperties("https://swapi.test/api", 10, Duration.ofHours(1), new SwapiProperties.Retry(0, Duration.ZERO), new SwapiProperties.CircuitBreaker(50, 10, 5, Duration.ofSeconds(30), 1)));
    }

    private static ListQuery query(Integer page, Long id, String name) {
        return new ListQuery(page, id, name, null);
    }

    private static ListQuery sorted(String sort) {
        return new ListQuery(1, null, null, sort);
    }

    private PageResponse<PersonDto> people(ListQuery query) {
        return engine.listPage(PeopleService.RESOURCE, query, new PeopleMapper());
    }

    private PageResponse<FilmDto> films(ListQuery query) {
        return engine.listPage(FilmsService.RESOURCE, query, new FilmsMapper());
    }

    private PageResponse<StarshipDto> starships(ListQuery query) {
        return engine.listPage(StarshipsService.RESOURCE, query, new StarshipsMapper());
    }

    private static SwapiItem person(String id, String name, String height, String mass) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("name", name);
        properties.put("height", height);
        properties.put("mass", mass);
        return new SwapiItem(id, null, properties);
    }

    private static Map<String, SwapiItem> catalogOf(SwapiItem... items) {
        return Fixtures.byId(List.of(items));
    }

    private void stubPeople(SwapiItem... items) {
        when(loader.load(PeopleService.RESOURCE)).thenReturn(catalogOf(items));
    }

    private static List<SwapiItem> namedItems(int count) {
        return IntStream.rangeClosed(1, count).mapToObj(i -> person(String.valueOf(i), "Person " + i, null, null)).toList();
    }

    @Test
    void withoutFiltersTheCatalogIsPaginatedLocallyInSwapiOrder() {
        when(loader.load(PeopleService.RESOURCE)).thenReturn(Fixtures.byId(namedItems(25)));

        PageResponse<PersonDto> second = people(query(2, null, null));

        assertThat(second.count()).isEqualTo(25);
        assertThat(second.totalPages()).isEqualTo(3);
        assertThat(second.currentPage()).isEqualTo(2);
        assertThat(second.nextPage()).isEqualTo(3);
        assertThat(second.previousPage()).isEqualTo(1);
        assertThat(second.results()).extracting(PersonDto::id)
                .containsExactly("11", "12", "13", "14", "15", "16", "17", "18", "19", "20");
    }

    @Test
    void pageBeyondTheLastIsEmptyWithCorrectMetadata() {
        when(loader.load(PeopleService.RESOURCE)).thenReturn(Fixtures.byId(namedItems(25)));

        PageResponse<PersonDto> page = people(query(50, null, null));

        assertThat(page.results()).isEmpty();
        assertThat(page.count()).isEqualTo(25);
        assertThat(page.totalPages()).isEqualTo(3);
        assertThat(page.currentPage()).isEqualTo(50);
        assertThat(page.nextPage()).isNull();
        assertThat(page.previousPage()).isEqualTo(3);
    }

    @Test
    void nameFilterIsCaseInsensitiveContainsAndPaginated() {
        List<SwapiItem> items = new java.util.ArrayList<>(namedItems(25));
        items.add(person("26", "Darth Vader", null, null));
        when(loader.load(PeopleService.RESOURCE)).thenReturn(Fixtures.byId(items));

        PageResponse<PersonDto> third = people(query(3, null, "pERSON"));

        assertThat(third.count()).isEqualTo(25);
        assertThat(third.totalPages()).isEqualTo(3);
        assertThat(third.results()).extracting(PersonDto::id).containsExactly("21", "22", "23", "24", "25");
        assertThat(people(query(1, null, "vader")).results()).extracting(PersonDto::name).containsExactly("Darth Vader");
    }

    @Test
    void nameFilterWithoutMatchesIsASuccessfulEmptyPage() {
        when(loader.load(StarshipsService.RESOURCE)).thenReturn(Fixtures.catalog("starships-catalog"));

        PageResponse<StarshipDto> page = starships(query(1, null, "zzz"));

        assertThat(page.results()).isEmpty();
        assertThat(page.count()).isZero();
        assertThat(page.totalPages()).isZero();
        assertThat(page.nextPage()).isNull();
        assertThat(page.previousPage()).isNull();
    }

    @Test
    void filmsAreFilteredByTitle() {
        when(loader.load(FilmsService.RESOURCE)).thenReturn(Fixtures.filmsCatalog());

        assertThat(films(query(1, null, null)).count()).isEqualTo(6);
        assertThat(films(query(1, null, "hope")).results()).extracting(FilmDto::title).containsExactly("A New Hope");
    }

    @Test
    void filmsSecondPageIsEmptyBecauseThereAreOnlySixFilms() {
        when(loader.load(FilmsService.RESOURCE)).thenReturn(Fixtures.filmsCatalog());

        PageResponse<FilmDto> page = films(query(2, null, null));

        assertThat(page.results()).isEmpty();
        assertThat(page.totalPages()).isEqualTo(1);
        assertThat(page.previousPage()).isEqualTo(1);
    }

    @Test
    void idFilterReturnsExactlyOneResult() {
        when(loader.load(PeopleService.RESOURCE)).thenReturn(Fixtures.catalog("people-catalog"));

        PageResponse<PersonDto> page = people(query(1, 1L, null));

        assertThat(page.count()).isEqualTo(1);
        assertThat(page.totalPages()).isEqualTo(1);
        assertThat(page.nextPage()).isNull();
        assertThat(page.previousPage()).isNull();
        assertThat(page.results()).extracting(PersonDto::name).containsExactly("Luke Skywalker");
    }

    @Test
    void unknownIdIsAnEmptySuccessfulPage() {
        when(loader.load(PeopleService.RESOURCE)).thenReturn(Fixtures.catalog("people-catalog"));

        PageResponse<PersonDto> page = people(query(1, 9999L, null));

        assertThat(page.results()).isEmpty();
        assertThat(page.count()).isZero();
        assertThat(page.totalPages()).isZero();
        assertThat(page.currentPage()).isEqualTo(1);
    }

    @Test
    void idWithPageGreaterThanOneIsEmpty() {
        when(loader.load(PeopleService.RESOURCE)).thenReturn(Fixtures.catalog("people-catalog"));

        PageResponse<PersonDto> page = people(query(2, 1L, null));

        assertThat(page.results()).isEmpty();
        assertThat(page.totalPages()).isEqualTo(1);
        assertThat(page.previousPage()).isEqualTo(1);
        assertThat(page.nextPage()).isNull();
    }

    @Test
    void idAndNameMustBothMatch() {
        when(loader.load(PeopleService.RESOURCE)).thenReturn(Fixtures.catalog("people-catalog"));

        assertThat(people(query(1, 1L, "LUKE")).results()).hasSize(1);
        assertThat(people(query(1, 1L, "ywalk")).results()).hasSize(1);
        PageResponse<PersonDto> mismatch = people(query(1, 1L, "Vader"));
        assertThat(mismatch.results()).isEmpty();
        assertThat(mismatch.count()).isZero();
    }

    @Test
    void idAndNameCombinationUsesTheTitleForFilms() {
        when(loader.load(FilmsService.RESOURCE)).thenReturn(Fixtures.filmsCatalog());

        assertThat(films(query(1, 1L, "new hope")).results()).hasSize(1);
        assertThat(films(query(1, 1L, "empire")).results()).isEmpty();
    }

    @Test
    void upstreamFailuresPropagateInsteadOfBecomingEmptyResults() {
        when(loader.load(PeopleService.RESOURCE)).thenThrow(new SwapiUnavailableException("down", null));

        assertThatThrownBy(() -> people(query(1, null, null))).isInstanceOf(SwapiUnavailableException.class);
    }

    // --- ordenamiento ---

    @Test
    void textSortIsCaseInsensitiveAscendingAndDescending() {
        stubPeople(person("1", "bravo", null, null), person("2", "Alpha", null, null), person("3", "charlie", null, null));

        assertThat(people(sorted("name")).results()).extracting(PersonDto::name)
                .containsExactly("Alpha", "bravo", "charlie");
        assertThat(people(sorted("-name")).results()).extracting(PersonDto::name)
                .containsExactly("charlie", "bravo", "Alpha");
    }

    @Test
    void numericSortComparesNumbersNotText() {
        stubPeople(person("1", "a", "100", null), person("2", "b", "9", null), person("3", "c", "1,000", null));

        assertThat(people(sorted("height")).results()).extracting(PersonDto::height)
                .containsExactly("9", "100", "1,000");
        assertThat(people(sorted("-height")).results()).extracting(PersonDto::height)
                .containsExactly("1,000", "100", "9");
    }

    @Test
    void valuesThatAreNotNumbersAreLastInBothDirections() {
        stubPeople(person("1", "a", "unknown", null), person("2", "b", "50", null), person("3", "c", null, null),
                person("4", "d", "n/a", null), person("5", "e", "", null), person("6", "f", "10", null));

        assertThat(people(sorted("height")).results()).extracting(PersonDto::id)
                .containsExactly("6", "2", "1", "3", "4", "5");
        assertThat(people(sorted("-height")).results()).extracting(PersonDto::id)
                .containsExactly("2", "6", "1", "3", "4", "5");
    }

    @Test
    void equalValuesTieBreakByNumericIdAscendingInBothDirections() {
        stubPeople(person("10", "a", "5", null), person("9", "b", "5", null), person("2", "c", "5", null),
                person("3", "d", "7", null));

        assertThat(people(sorted("height")).results()).extracting(PersonDto::id).containsExactly("2", "9", "10", "3");
        assertThat(people(sorted("-height")).results()).extracting(PersonDto::id).containsExactly("3", "2", "9", "10");
    }

    @Test
    void sortIsAppliedBeforePaginationAndAfterTheNameFilter() {
        List<SwapiItem> items = IntStream.rangeClosed(1, 12)
                .mapToObj(i -> person(String.valueOf(i), "Person " + i, String.valueOf(i), null)).toList();
        when(loader.load(PeopleService.RESOURCE)).thenReturn(Fixtures.byId(items));

        PageResponse<PersonDto> page = people(new ListQuery(2, null, "person", "-height"));

        assertThat(page.results()).extracting(PersonDto::id).containsExactly("2", "1");
        assertThat(page.count()).isEqualTo(12);
    }

    @Test
    void withoutSortTheCatalogOrderIsKept() {
        stubPeople(person("3", "c", null, null), person("1", "a", null, null), person("2", "b", null, null));

        assertThat(people(query(1, null, null)).results()).extracting(PersonDto::id).containsExactly("3", "1", "2");
    }

    @Test
    void filmsSortByEpisodeAndReleaseDate() {
        when(loader.load(FilmsService.RESOURCE)).thenReturn(Fixtures.filmsCatalog());

        assertThat(films(new ListQuery(1, null, null, "episodeId")).results()).extracting(FilmDto::episodeId)
                .containsExactly(1, 2, 3, 4, 5, 6);
        assertThat(films(new ListQuery(1, null, null, "-releaseDate")).results()).extracting(FilmDto::releaseDate)
                .isSortedAccordingTo(java.util.Comparator.reverseOrder());
        assertThat(films(new ListQuery(1, null, null, "title")).results()).extracting(FilmDto::title).first()
                .isEqualTo("A New Hope");
    }

    @Test
    void starshipsSortByCostTreatingUnknownAsLast() {
        when(loader.load(StarshipsService.RESOURCE)).thenReturn(Fixtures.byId(List.of(
                ship("1", "100000"), ship("2", "unknown"), ship("3", "1,000,000"), ship("4", "9"))));

        assertThat(starships(new ListQuery(1, null, null, "costInCredits")).results())
                .extracting(StarshipDto::id).containsExactly("4", "1", "3", "2");
        assertThat(starships(new ListQuery(1, null, null, "-costInCredits")).results())
                .extracting(StarshipDto::id).containsExactly("3", "1", "4", "2");
    }

    private static SwapiItem ship(String id, String cost) {
        return new SwapiItem(id, null, Map.of("name", "Ship " + id, "cost_in_credits", cost));
    }

    @Test
    void starshipsSortByAttributesWithIrregularPropertyNames() {
        when(loader.load(StarshipsService.RESOURCE)).thenReturn(Fixtures.byId(List.of(
                new SwapiItem("1", null, Map.of("name", "A", "MGLT", "75", "hyperdrive_rating", "2.0")),
                new SwapiItem("2", null, Map.of("name", "B", "MGLT", "10", "hyperdrive_rating", "0.5")))));

        assertThat(starships(new ListQuery(1, null, null, "mglt")).results()).extracting(StarshipDto::id)
                .containsExactly("2", "1");
        assertThat(starships(new ListQuery(1, null, null, "hyperdriveRating")).results()).extracting(StarshipDto::id)
                .containsExactly("2", "1");
    }

    @Test
    void unknownSortFieldIsRejectedListingTheAllowedOnes() {
        assertThatThrownBy(() -> people(sorted("birthYear")))
                .isInstanceOf(InvalidSortException.class)
                .hasMessageContaining("sort")
                .hasMessageContaining("name, height, mass")
                .hasMessageNotContaining("birthYear");
        assertThatThrownBy(() -> people(sorted("-nope"))).isInstanceOf(InvalidSortException.class);
        verify(loader, never()).load(PeopleService.RESOURCE);
    }

    @Test
    void sortFieldsAreCaseSensitive() {
        assertThatThrownBy(() -> people(sorted("Name"))).isInstanceOf(InvalidSortException.class);
    }

    @Test
    void everyResourceDeclaresItsNameFieldAsSortable() {
        assertThat(PeopleService.RESOURCE.sortFields()).extracting(f -> f.name()).contains("name");
        assertThat(StarshipsService.RESOURCE.sortFields()).extracting(f -> f.name()).contains("name");
        assertThat(VehiclesService.RESOURCE.sortFields()).extracting(f -> f.name()).contains("name");
        assertThat(FilmsService.RESOURCE.sortFields()).extracting(f -> f.name()).contains("title");
    }
}
