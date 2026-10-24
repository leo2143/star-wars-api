package com.challenge.starwars.mappers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.challenge.starwars.dtos.swapi.SwapiItem;
import com.challenge.starwars.exceptions.SwapiInvalidResponseException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class SwapiFieldsTest {

    @Test
    void usesUidAsPrimarySourceOfTheIdentifier() {
        SwapiItem item = new SwapiItem("7", null,
                Map.of("name", "X", "url", "https://www.swapi.tech/api/people/99"));

        assertThat(SwapiFields.idOf(item)).isEqualTo("7");
    }

    @Test
    void fallsBackToUrlWhenUidIsMissing() {
        SwapiItem item = new SwapiItem(null, null,
                Map.of("name", "X", "url", "https://www.swapi.tech/api/people/42"));

        assertThat(SwapiFields.idOf(item)).isEqualTo("42");
    }

    @Test
    void rejectsItemWithoutAnyIdentifier() {
        SwapiItem item = new SwapiItem(null, null, Map.of("name", "X"));

        assertThatThrownBy(() -> SwapiFields.idOf(item)).isInstanceOf(SwapiInvalidResponseException.class);
    }

    @Test
    void extractsTrailingNumericIdentifier() {
        assertThat(SwapiFields.extractId("https://www.swapi.tech/api/people/1")).isEqualTo("1");
        assertThat(SwapiFields.extractId("https://www.swapi.tech/api/starships/12")).isEqualTo("12");
    }

    @Test
    void extractIdToleratesTrailingSlash() {
        assertThat(SwapiFields.extractId("https://www.swapi.tech/api/films/4/")).isEqualTo("4");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"https://www.swapi.tech/api/people", "https://www.swapi.tech/api/people/abc", "not a url"})
    void extractIdReturnsNullWhenThereIsNoNumericIdentifier(String url) {
        assertThat(SwapiFields.extractId(url)).isNull();
    }
}
