package cr.ac.tec.pokearena.application.usecase;

import java.util.List;
import java.util.Map;

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
 * Propósito: implementar Explorar catálogo y mi colección (historia 3.1.2, sección 8.3, Anexo A.6).
 * Responsabilidades: validar, paginar y combinar cartas públicas con copias privadas en lote.
 * La API ya verificó la sesión (mensajes 3 a 5); GET no requiere idempotencia ni escrituras.
 * Como en SearchCardsUseCase, no mantiene una transacción durante el acceso a la caché.
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

    public Page<CatalogCardDetails> execute(ExploreCatalogCommand command) {
        // A.6, mensaje 6: validar filtros y paginación antes de leer páginas o colecciones.
        CatalogCriteria criteria = CatalogRules.validate(Pagination.withoutPagination(command.parameters()),
                catalog.findAllTypes());
        PageRequest request = pagination.parse(command.parameters());

        // A.6, rama Solo mis cartas: copias y cartas se leen juntas; nunca se cachean.
        if (criteria.owned()) {
            return pagination.toPage(cards.owned(command.playerId(), criteria, request.afterId(), request.fetchSize()),
                    request, row -> row.stored().id())
                    .map(row -> new CatalogCardDetails(CardDetails.of(row.stored().card()), row.copies()));
        }

        // A.6, rama Todo el catálogo: esta página es compartible entre jugadores, con espacio propio.
        CatalogCacheKey key = cacheKey(criteria, request);
        Page<CardDetails> page = cache.get(key, CardDetails.class).orElseGet(() -> {
            List<StoredCard> rows = cards.available(criteria, request.afterId(), request.fetchSize());
            Page<CardDetails> result = pagination.toPage(rows, request, StoredCard::id)
                    .map(row -> CardDetails.of(row.card()));
            cache.put(key, result);
            return result;
        });

        // A.6, consulta de copias: una lectura para todos los códigos de la página, incluso en un hit.
        Map<String, Integer> copies = cards.findCopies(command.playerId(),
                page.items().stream().map(CardDetails::code).toList());
        return page.map(card -> new CatalogCardDetails(card, copies.getOrDefault(card.code(), 0)));
    }

    /** Solo filtros normalizados, cursor y límite; ninguna identidad entra en la página compartida. */
    private static CatalogCacheKey cacheKey(CatalogCriteria criteria, PageRequest page) {
        RequestHash.Builder filters = RequestHash.builder();
        criteria.cardCriteria().asMap().forEach(filters::field);
        String cursor = page.afterId() == PageRequest.FROM_START
                ? CatalogCacheKey.FIRST_PAGE : Pagination.encode(page.afterId());
        return new CatalogCacheKey(CatalogCacheKey.PLAYER_CATALOG, filters.build(), cursor, page.limit());
    }
}
