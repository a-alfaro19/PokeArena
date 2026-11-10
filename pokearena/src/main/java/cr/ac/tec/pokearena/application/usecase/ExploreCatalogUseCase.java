package cr.ac.tec.pokearena.application.usecase;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import cr.ac.tec.pokearena.application.dto.CardDetails;
import cr.ac.tec.pokearena.application.dto.CatalogCardDetails;
import cr.ac.tec.pokearena.application.dto.ExploreCatalogCommand;
import cr.ac.tec.pokearena.application.dto.Page;
import cr.ac.tec.pokearena.application.dto.PageRequest;
import cr.ac.tec.pokearena.application.port.CatalogCache;
import cr.ac.tec.pokearena.application.port.CatalogCacheKey;
import cr.ac.tec.pokearena.domain.model.CatalogCriteria;
import cr.ac.tec.pokearena.domain.model.StoredCard;
import cr.ac.tec.pokearena.domain.repository.CardCatalogRepository;
import cr.ac.tec.pokearena.domain.repository.PlayerCatalogRepository;
import cr.ac.tec.pokearena.domain.rule.CatalogRules;

/**
 * Propósito: caso de uso de la historia 3.1.2 "Explorar el catálogo y mi colección".
 * Responsabilidades: validar filtros y paginación, obtener la página del catálogo (de la caché o de la
 * base) y combinarla con la propiedad y las copias del jugador, o leer solo sus cartas, en el orden del
 * diagrama de secuencia.
 * Referencia: sección 8.3, sección 5.5 (paginación) y Anexo A.6 (diagrama de secuencia).
 *
 * <h2>Misma plantilla que Buscar carta (corrección 7; ver {@link SearchCardsUseCase})</h2>
 * <ol>
 *   <li><b>Autorizar</b> (mensajes 1 a 5): ya ocurrió en la API. El interceptor verificó la sesión y el
 *       rol ({@code @RequiresRole({PLAYER, ADMIN})} en {@code CatalogController}); el caso de uso no vuelve
 *       a comprobarlo. El jugador sale siempre de la sesión verificada, nunca de un parámetro.</li>
 *   <li><b>Idempotencia</b>: no aplica. Es una consulta (GET) que no cambia nada; repetirla da el mismo
 *       efecto (sección 5.4 pide la clave solo a los POST de creación).</li>
 *   <li><b>Validar</b> (mensaje 6): filtros y paginación, con errores tipados (INVALID_FIELD,
 *       INVALID_PAGINATION), <b>antes</b> de tocar la caché o la base.</li>
 *   <li><b>Ejecutar</b> (mensajes 7 a 16): rama "Solo mis cartas" o rama "Todo el catálogo". No usa
 *       {@code UnitOfWork}, igual que Buscar carta: son lecturas sin nada que confirmar ni revertir.</li>
 *   <li><b>Responder</b> (mensajes 17 a 19): la API responde 200 con la página; sin resultados, una lista
 *       vacía, no un error (criterio C.6).</li>
 * </ol>
 *
 * <h2>Decisiones</h2>
 * <ul>
 *   <li>La página del catálogo no depende del jugador y se cachea en su propio espacio
 *       ({@link CatalogCacheKey#PLAYER_CATALOG}); la propiedad y las copias se leen siempre de la base, sin
 *       caché, en <b>una</b> consulta para todos los códigos de la página (sin N+1), también en un acierto
 *       de la caché (sección 8.3, "Cómo se resuelve").</li>
 *   <li>Con {@code owned=true} la consulta depende del jugador: se lee de la base con la unión a
 *       {@code player_card} y nunca se cachea.</li>
 *   <li>Solo se listan cartas disponibles y activas; el administrador ve las demás en Buscar carta.</li>
 *   <li>La clave de la caché la arma {@link CatalogPageKeys} con los criterios validados, igual que en
 *       Buscar carta.</li>
 * </ul>
 */
public class ExploreCatalogUseCase {

    private final Pagination pagination;
    private final CardCatalogRepository catalog;
    private final PlayerCatalogRepository cards;
    private final CatalogCache cache;

    public ExploreCatalogUseCase(Pagination pagination, CardCatalogRepository catalog,
            PlayerCatalogRepository cards, CatalogCache cache) {
        this.pagination = pagination;
        this.catalog = catalog;
        this.cards = cards;
        this.cache = cache;
    }

    /**
     * Explora el catálogo con la propiedad del jugador de la sesión.
     *
     * @param command jugador verificado y todos los parámetros de la consulta, filtros y paginación
     * @return la página, en orden de {@code id}, con propiedad y copias, y el cursor de la siguiente
     */
    public Page<CatalogCardDetails> execute(ExploreCatalogCommand command) {
        // Mensajes 1 a 5 del diagrama A.6: la interfaz envió el GET y la seguridad validó la sesión; el
        // controlador llamó al caso de uso con el jugador verificado, los filtros, el cursor y el límite.

        // Mensaje 6: validar filtros y paginación, antes de leer páginas o colecciones.
        CatalogCriteria criteria = CatalogRules.validate(Pagination.withoutPagination(command.parameters()),
                catalog.findAllTypes());
        PageRequest request = pagination.parse(command.parameters());

        // Mensajes 7 y 8 (rama "Solo mis cartas"): cartas propias con filtros y unión a la colección, con sus
        // copias en la misma consulta. Depende del jugador: nunca se cachea.
        if (criteria.owned()) {
            return pagination.toPage(cards.owned(command.playerId(), criteria, request.afterId(), request.fetchSize()),
                    request, row -> row.stored().id())
                    .map(row -> new CatalogCardDetails(CardDetails.of(row.stored().card()), row.copies()));
        }

        // Mensajes 9 y 10 (rama "Todo el catálogo"): consultar la página en la caché; 10 es el acierto. La página no lleva
        // datos del jugador, así que es compartible entre jugadores.
        CatalogCacheKey key = CatalogPageKeys.of(CatalogCacheKey.PLAYER_CATALOG, criteria.cardCriteria(), request);
        Optional<Page<CardDetails>> cached = cache.get(key, CardDetails.class);
        Page<CardDetails> page;
        if (cached.isPresent()) {
            page = cached.get();
        } else {
            // Mensajes 11 a 13: la página no está; consultar cartas disponibles con filtros, id mayor al
            // cursor, orden por id y límite más uno.
            List<StoredCard> rows = cards.available(criteria, request.afterId(), request.fetchSize());
            page = pagination.toPage(rows, request, StoredCard::id).map(row -> CardDetails.of(row.card()));

            // Mensaje 14: guardar la página con vencimiento.
            cache.put(key, page);
        }

        // Mensajes 15 y 16: copias del jugador para los códigos de la página, en una sola consulta y sin
        // caché, también cuando la página vino de la caché.
        Map<String, Integer> copies = cards.findCopies(command.playerId(),
                page.items().stream().map(CardDetails::code).toList());

        // Mensajes 17 a 19: con la página combinada, la API responde 200 con las cartas y el cursor.
        return page.map(card -> new CatalogCardDetails(card, copies.getOrDefault(card.code(), 0)));
    }
}
