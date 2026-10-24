package com.challenge.starwars.dtos.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class PageResponseTest {

    private static final List<Integer> TWENTY_FIVE = IntStream.rangeClosed(1, 25).boxed().toList();

    @Test
    void localFirstPageHasNextButNoPrevious() {
        PageResponse<Integer> page = PageResponse.of(TWENTY_FIVE, 1, 10);

        assertThat(page.count()).isEqualTo(25);
        assertThat(page.totalPages()).isEqualTo(3);
        assertThat(page.currentPage()).isEqualTo(1);
        assertThat(page.nextPage()).isEqualTo(2);
        assertThat(page.previousPage()).isNull();
        assertThat(page.results()).containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
    }

    @Test
    void localLastPageIsPartialAndHasNoNext() {
        PageResponse<Integer> page = PageResponse.of(TWENTY_FIVE, 3, 10);

        assertThat(page.results()).containsExactly(21, 22, 23, 24, 25);
        assertThat(page.nextPage()).isNull();
        assertThat(page.previousPage()).isEqualTo(2);
    }

    @Test
    void localPageBeyondRangeIsEmptyWithCorrectMetadata() {
        PageResponse<Integer> page = PageResponse.of(TWENTY_FIVE, 9, 10);

        assertThat(page.results()).isEmpty();
        assertThat(page.count()).isEqualTo(25);
        assertThat(page.totalPages()).isEqualTo(3);
        assertThat(page.currentPage()).isEqualTo(9);
        assertThat(page.nextPage()).isNull();
        assertThat(page.previousPage()).isEqualTo(3);
    }

    @Test
    void localEmptyListHasZeroPagesAndNoNeighbours() {
        PageResponse<Integer> page = PageResponse.of(List.of(), 1, 10);

        assertThat(page.count()).isZero();
        assertThat(page.totalPages()).isZero();
        assertThat(page.nextPage()).isNull();
        assertThat(page.previousPage()).isNull();
        assertThat(page.results()).isEmpty();
    }

    @Test
    void localExactMultipleDoesNotCreateAnExtraPage() {
        PageResponse<Integer> page = PageResponse.of(TWENTY_FIVE.subList(0, 20), 2, 10);

        assertThat(page.totalPages()).isEqualTo(2);
        assertThat(page.nextPage()).isNull();
    }

    @Test
    void hugePageNumberDoesNotOverflow() {
        PageResponse<Integer> page = PageResponse.of(TWENTY_FIVE, Integer.MAX_VALUE, 10);

        assertThat(page.results()).isEmpty();
        assertThat(page.previousPage()).isEqualTo(3);
    }

    @Test
    void mapKeepsMetadataAndTransformsResults() {
        PageResponse<String> page = PageResponse.of(TWENTY_FIVE, 2, 10).map(n -> "n" + n);

        assertThat(page.currentPage()).isEqualTo(2);
        assertThat(page.count()).isEqualTo(25);
        assertThat(page.results()).startsWith("n11").hasSize(10);
    }
}
