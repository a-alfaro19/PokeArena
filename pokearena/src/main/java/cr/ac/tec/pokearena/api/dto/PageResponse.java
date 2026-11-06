package cr.ac.tec.pokearena.api.dto;

import java.util.List;
import java.util.function.Function;

import cr.ac.tec.pokearena.application.dto.Page;

/**
 * Propósito: cuerpo HTTP de una página de resultados, con la forma única de la sección 5.5.
 * Responsabilidades: definir el JSON {@code { "items": [...], "nextCursor": "..." | null, "limit": 20 }}.
 * {@code nextCursor} se escribe siempre, con {@code null} en la última página. La usan la búsqueda del
 * administrador (7.3) y, más adelante, el catálogo del jugador (8.3).
 * Referencia: sección 5.5.
 *
 * @param items      elementos de la página
 * @param nextCursor cursor de la página siguiente, o {@code null} si es la última
 * @param limit      tamaño de página con el que se pidió
 * @param <T>        forma JSON de cada elemento
 */
public record PageResponse<T>(List<T> items, String nextCursor, int limit) {

    public static <A, T> PageResponse<T> from(Page<A> page, Function<A, T> mapper) {
        return new PageResponse<>(page.items().stream().map(mapper).toList(), page.nextCursor(), page.limit());
    }
}
