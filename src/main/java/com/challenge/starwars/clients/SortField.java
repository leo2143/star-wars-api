package com.challenge.starwars.clients;

import com.challenge.starwars.dtos.swapi.SwapiItem;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.Locale;
import java.util.function.Function;

/**
 * Un campo por el que se puede ordenar un listado. {@code name} es el nombre público (camelCase) usado en {@code sort};
 * {@code property} es la propiedad de SWAPI que lee, por defecto el mismo nombre en snake_case. Los campos numéricos
 * se comparan como números y los de texto sin distinguir mayúsculas; un valor que no puede compararse (ausente, vacío, {@code
 * unknown}, {@code n/a}...) queda siempre al final, en cualquier dirección.
 */
public record SortField(String name, String property, boolean numeric) {

    public static SortField text(String name) {
        return new SortField(name, snakeCase(name), false);
    }

    public static SortField number(String name) {
        return number(name, snakeCase(name));
    }

    /**
     * Para las pocas propiedades de SWAPI cuyo nombre no es snake_case (p. ej. {@code MGLT}).
     */
    public static SortField number(String name, String property) {
        return new SortField(name, property, true);
    }

    private static <K extends Comparable<K>> Comparator<SwapiItem> by(Function<SwapiItem, K> key,
                                                                      boolean descending) {
        Comparator<K> order = descending ? Comparator.reverseOrder() : Comparator.naturalOrder();
        // nullsLast envuelve la dirección, de modo que los valores no comparables también quedan al final en orden descendente.
        return Comparator.comparing(key, Comparator.nullsLast(order));
    }

    /**
     * {@code costInCredits} -> {@code cost_in_credits}.
     */
    private static String snakeCase(String name) {
        return name.replaceAll("([A-Z])", "_$1").toLowerCase(Locale.ROOT);
    }

    /**
     * Ordena los elementos solo por este campo; quien llama agrega su propio criterio de desempate.
     */
    public Comparator<SwapiItem> comparator(boolean descending) {
        return numeric ? by(this::numberOf, descending) : by(this::textOf, descending);
    }

    private String textOf(SwapiItem item) {
        String value = item.text(property());
        return value == null || value.isBlank() ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    private BigDecimal numberOf(SwapiItem item) {
        String value = item.text(property());
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(value.trim().replace(",", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
