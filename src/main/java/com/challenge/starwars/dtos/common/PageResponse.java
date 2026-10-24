package com.challenge.starwars.dtos.common;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.function.Function;

/**
 * Contrato único de paginación compartido por todos los endpoints de listado.
 *
 * <p>Los números de página empiezan en 1. {@code nextPage} y {@code previousPage} son números de página (nunca
 * URLs del origen) y valen {@code null} cuando no existe esa página.
 */
public record PageResponse<T>(
        @Schema(description = "Total number of records matching the filters", example = "82") long count,
        @Schema(description = "Requested page (1-based)", example = "1") int currentPage,
        @Schema(description = "Total number of pages for the applied filters", example = "9") int totalPages,
        @Schema(description = "Next page number, null when there is none", example = "2", nullable = true)
        Integer nextPage,
        @Schema(description = "Previous page number, null when there is none", nullable = true)
        Integer previousPage,
        @Schema(description = "Items of the requested page; empty when the page is out of range")
        List<T> results) {

    public PageResponse {
        results = List.copyOf(results);
    }

    /**
     * Divide una lista ya completa en la página solicitada.
     */
    public static <T> PageResponse<T> of(List<T> all, int page, int pageSize) {
        int totalPages = (int) ((all.size() + (long) pageSize - 1) / pageSize);
        long from = (page - 1L) * pageSize;
        List<T> results = from >= all.size()
                ? List.of()
                : all.subList((int) from, (int) Math.min(from + pageSize, all.size()));
        return new PageResponse<>(all.size(), page, totalPages, next(page, totalPages), previous(page, totalPages),
                results);
    }

    private static Integer next(int page, int totalPages) {
        return page < totalPages ? page + 1 : null;
    }

    private static Integer previous(int page, int totalPages) {
        return page > 1 && totalPages > 0 ? Math.min(page - 1, totalPages) : null;
    }

    public <R> PageResponse<R> map(Function<? super T, ? extends R> mapper) {
        return new PageResponse<>(count, currentPage, totalPages, nextPage, previousPage,
                results.stream().<R>map(mapper).toList());
    }
}
