package com.challenge.starwars.dtos.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ListQueryTest {

    @Test
    void pageDefaultsToOne() {
        assertThat(new ListQuery(null, null, null, null).page()).isEqualTo(1);
    }

    @Test
    void blankNameIsTreatedAsAbsent() {
        assertThat(new ListQuery(1, null, "   ", null).name()).isNull();
        assertThat(new ListQuery(1, null, "", null).name()).isNull();
    }

    @Test
    void nameIsTrimmed() {
        assertThat(new ListQuery(1, null, "  Luke  ", null).name()).isEqualTo("Luke");
    }

    @Test
    void explicitValuesAreKept() {
        ListQuery query = new ListQuery(3, 7L, "x", null);

        assertThat(query.page()).isEqualTo(3);
        assertThat(query.id()).isEqualTo(7L);
    }

    @Test
    void sortIsKeptAsGiven() {
        assertThat(new ListQuery(1, null, null, "-height").sort()).isEqualTo("-height");
        assertThat(new ListQuery(1, null, null, null).sort()).isNull();
    }
}
