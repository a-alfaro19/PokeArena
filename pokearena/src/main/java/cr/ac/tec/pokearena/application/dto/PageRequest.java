package cr.ac.tec.pokearena.application.dto;

/**
 * Propósito: página pedida, ya validada por {@code Pagination} (sección 5.5).
 * Responsabilidades: decir desde dónde leer ({@code id} mayor que {@code afterId}) y cuántos elementos
 * devolver. La consulta pide {@link #fetchSize()} = {@code limit + 1} filas: si llega la fila extra,
 * hay otra página.
 * Referencia: sección 5.5 ("WHERE id > :cursor ORDER BY id LIMIT :limit + 1").
 *
 * @param afterId último {@code id} de la página anterior; {@link #FROM_START} en la primera página
 * @param limit   tamaño de la página
 */
public record PageRequest(long afterId, int limit) {

    /** {@code afterId} de la primera página: los {@code id} (bigserial) empiezan en 1. */
    public static final long FROM_START = 0;

    /** Filas que se piden a la base: una más que la página, para saber si hay más. */
    public int fetchSize() {
        return limit + 1;
    }
}
