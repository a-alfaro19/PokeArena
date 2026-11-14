package cr.ac.tec.pokearena.application.usecase;

import cr.ac.tec.pokearena.application.dto.PageRequest;
import cr.ac.tec.pokearena.application.port.CatalogCacheKey;
import cr.ac.tec.pokearena.domain.model.CardSearchCriteria;

/**
 * Propósito: armar la clave de una página en la caché del catálogo (sección 9, "Clave").
 * Responsabilidades: reunir el espacio, el hash de los criterios ya validados y normalizados, el cursor y
 * el límite. La comparten Buscar carta ({@link SearchCardsUseCase}) y Explorar el catálogo
 * ({@link ExploreCatalogUseCase}), así las dos historias arman la clave igual (corrección 7).
 * Referencia: sección 9 y sección 7.3 ("la clave de caché se arma con los criterios ya validados y
 * normalizados").
 *
 * <p>Decisiones:
 * <ul>
 *   <li>Se usan los criterios validados ({@link CardSearchCriteria#asMap()}), no el texto de la solicitud:
 *       búsquedas equivalentes ({@code name=DRAGÓN} y {@code name=dragon}, o {@code cost=3} y
 *       {@code costMin=3&costMax=3}) comparten página.</li>
 *   <li>La clave nunca incluye al jugador: las páginas cacheadas no llevan datos privados (sección 9).</li>
 *   <li>La versión del catálogo ({@code catalog:v<versión>}) la antepone la implementación del puerto.</li>
 * </ul>
 */
final class CatalogPageKeys {

    private CatalogPageKeys() {
    }

    /**
     * @param space    {@link CatalogCacheKey#ADMIN_SEARCH} o {@link CatalogCacheKey#PLAYER_CATALOG}
     * @param criteria criterios ya validados
     * @param page     paginación ya validada
     */
    static CatalogCacheKey of(String space, CardSearchCriteria criteria, PageRequest page) {
        RequestHash.Builder filters = RequestHash.builder();
        criteria.asMap().forEach(filters::field);
        String cursor = page.afterId() == PageRequest.FROM_START
                ? CatalogCacheKey.FIRST_PAGE
                : Pagination.encode(page.afterId());
        return new CatalogCacheKey(space, filters.build(), cursor, page.limit());
    }
}
