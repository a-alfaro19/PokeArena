package cr.ac.tec.pokearena.application.port;

import java.util.Objects;

/**
 * Propósito: clave de una página en la caché del catálogo (sección 9).
 * Responsabilidades: reunir las partes que conoce el caso de uso. La implementación le antepone la
 * versión: {@code catalog:v<versión>:<espacio>:<hash de filtros>:<cursor>:<limit>}.
 * Referencia: sección 9 ("Clave").
 *
 * @param space       separa la búsqueda del administrador del catálogo del jugador, que ven datos distintos
 * @param filtersHash hash de los criterios activos
 * @param cursor      cursor de la página, o {@link #FIRST_PAGE} en la primera
 * @param limit       tamaño de la página
 */
public record CatalogCacheKey(String space, String filtersHash, String cursor, int limit) {

    /** Espacio de la búsqueda del administrador (7.3): incluye las cartas no disponibles. */
    public static final String ADMIN_SEARCH = "admin-search";

    /** Parte de la clave que reemplaza al cursor en la primera página. */
    public static final String FIRST_PAGE = "first";

    public CatalogCacheKey {
        Objects.requireNonNull(space, "El espacio es obligatorio");
        Objects.requireNonNull(filtersHash, "El hash de los filtros es obligatorio");
        Objects.requireNonNull(cursor, "El cursor es obligatorio");
    }

    /** La clave como texto, sin la versión: {@code <espacio>:<hash>:<cursor>:<limit>}. */
    public String asText() {
        return space + ":" + filtersHash + ":" + cursor + ":" + limit;
    }
}
