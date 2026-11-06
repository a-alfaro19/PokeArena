package cr.ac.tec.pokearena.application.dto;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Propósito: página de resultados con la forma única de la sección 5.5.
 * Responsabilidades: llevar los elementos, el cursor de la página siguiente ({@code null} en la última)
 * y el tamaño pedido. Sin {@code total} ni vencimientos (5.5). La comparten la búsqueda del
 * administrador (7.3) y el catálogo del jugador (8.3).
 * Referencia: sección 5.5 ("{ items, nextCursor | null, limit }").
 *
 * @param items      elementos de la página, en orden de {@code id}
 * @param nextCursor cursor opaco para pedir la página siguiente, o {@code null} si es la última
 * @param limit      tamaño de página con el que se pidió
 * @param <T>        tipo de cada elemento
 */
public record Page<T>(List<T> items, String nextCursor, int limit) {

    public Page {
        items = List.copyOf(Objects.requireNonNull(items, "Los elementos son obligatorios"));
    }

    /** La misma página con cada elemento convertido; el cursor y el límite no cambian. */
    public <R> Page<R> map(Function<T, R> mapper) {
        return new Page<>(items.stream().map(mapper).toList(), nextCursor, limit);
    }
}
