package cr.ac.tec.pokearena.application.usecase;

import java.util.List;
import java.util.Optional;

import cr.ac.tec.pokearena.application.dto.CardDetails;
import cr.ac.tec.pokearena.application.dto.Page;
import cr.ac.tec.pokearena.application.dto.PageRequest;
import cr.ac.tec.pokearena.application.dto.SearchCardsCommand;
import cr.ac.tec.pokearena.application.port.CatalogCache;
import cr.ac.tec.pokearena.application.port.CatalogCacheKey;
import cr.ac.tec.pokearena.domain.model.CardSearchCriteria;
import cr.ac.tec.pokearena.domain.model.StoredCard;
import cr.ac.tec.pokearena.domain.repository.CardCatalogRepository;
import cr.ac.tec.pokearena.domain.repository.CardRepository;
import cr.ac.tec.pokearena.domain.rule.CardSearchRules;

/**
 * Propósito: caso de uso de la historia 2.1.2 "Buscar carta".
 * Responsabilidades: validar criterios y paginación, consultar la caché y, si la página no está, la
 * base, y guardar la página en la caché, en el orden del diagrama de secuencia.
 * Referencia: sección 7.3, sección 5.5 (paginación) y Anexo A.3 (diagrama de secuencia).
 *
 * <h2>Misma plantilla que Crear carta (corrección 7; ver {@link CreateCardUseCase})</h2>
 * <ol>
 *   <li><b>Autorizar</b> (mensajes 1 a 5): ya ocurrió en la API. El interceptor verificó la sesión y el
 *       rol de administrador ({@code @RequiresRole(ADMIN)} en {@code CardController}); el caso de uso no
 *       vuelve a comprobarlo. Una solicitud sin credenciales recibe 401 aunque sus criterios sean
 *       inválidos, igual que en Crear carta.</li>
 *   <li><b>Idempotencia</b>: no aplica. Una búsqueda (GET) no cambia nada: repetirla ya da el mismo
 *       efecto, por eso no lleva {@code Idempotency-Key} (sección 5.4 la pide solo a los POST de creación).</li>
 *   <li><b>Validar</b> (mensaje 6): todos los criterios y la paginación, con errores tipados
 *       (INVALID_SEARCH_CRITERIA, INVALID_PAGINATION), <b>antes</b> de tocar la caché o la base.</li>
 *   <li><b>Ejecutar</b> (mensajes 7 a 12): caché o base. No usa {@code UnitOfWork}: el diagrama A.3 no
 *       lo tiene porque no hay nada que confirmar ni revertir; es una sola consulta de lectura, que en
 *       PostgreSQL ya ve un estado consistente por sí sola.</li>
 *   <li><b>Responder</b> (mensajes 13 a 15): la API responde 200 con la página; sin resultados, una
 *       lista vacía, no un error (criterio C.3).</li>
 * </ol>
 *
 * <h2>Decisiones</h2>
 * <ul>
 *   <li>El administrador ve todas las cartas, también las no disponibles (por ejemplo con la miniatura
 *       en {@code PENDING_RETRY}), para poder revisarlas (sección 7.3).</li>
 *   <li>La clave de la caché usa los criterios ya validados y normalizados: {@code name=DRAGÓN} y
 *       {@code name=dragon}, o {@code cost=3} y {@code costMin=3&costMax=3}, comparten página.</li>
 *   <li>La página guardada en la caché ya trae su {@code nextCursor}: como el cursor es por clave
 *       ({@code id}), sigue siendo válido aunque se creen cartas después.</li>
 * </ul>
 */
public class SearchCardsUseCase {

    private final Pagination pagination;
    private final CardCatalogRepository catalog;
    private final CardRepository cards;
    private final CatalogCache cache;

    public SearchCardsUseCase(Pagination pagination, CardCatalogRepository catalog, CardRepository cards,
            CatalogCache cache) {
        this.pagination = pagination;
        this.catalog = catalog;
        this.cards = cards;
        this.cache = cache;
    }

    /**
     * Busca cartas.
     *
     * @param command todos los parámetros de la consulta, criterios y paginación
     * @return la página de fichas, en orden de {@code id}, con el cursor de la siguiente
     */
    public Page<CardDetails> execute(SearchCardsCommand command) {
        // Mensajes 1 a 5 del diagrama A.3: la interfaz envió el GET y la seguridad validó la sesión y
        // el rol de administrador; el controlador llamó al caso de uso con los criterios.

        // Mensaje 6: validar criterios y paginación, antes de consultar nada.
        CardSearchCriteria criteria = CardSearchRules.validate(Pagination.withoutPagination(command.parameters()),
                catalog.findAllTypes(), catalog.findAllRarities());
        PageRequest page = pagination.parse(command.parameters());

        // Mensajes 7 y 8: consultar la página en la caché.
        CatalogCacheKey key = cacheKey(criteria, page);
        Optional<Page<CardDetails>> cached = cache.get(key, CardDetails.class);
        if (cached.isPresent()) {
            return cached.get();
        }

        // Mensajes 9 a 11: la página no está; consultar con filtros, id mayor al cursor, orden por id y
        // límite más uno.
        List<StoredCard> rows = cards.search(criteria, page.afterId(), page.fetchSize());
        Page<CardDetails> result = pagination.toPage(rows, page, StoredCard::id)
                .map(stored -> CardDetails.of(stored.card()));

        // Mensaje 12: guardar la página con vencimiento.
        cache.put(key, result);

        // Mensajes 13 a 15: con el resultado, la API responde 200 con las cartas y el cursor.
        return result;
    }

    /**
     * Clave de la página en la caché (sección 9): espacio de la búsqueda del administrador, hash de los
     * criterios validados, cursor y límite.
     */
    private static CatalogCacheKey cacheKey(CardSearchCriteria criteria, PageRequest page) {
        RequestHash.Builder filters = RequestHash.builder();
        criteria.asMap().forEach(filters::field);
        String cursor = page.afterId() == PageRequest.FROM_START
                ? CatalogCacheKey.FIRST_PAGE
                : Pagination.encode(page.afterId());
        return new CatalogCacheKey(CatalogCacheKey.ADMIN_SEARCH, filters.build(), cursor, page.limit());
    }
}
