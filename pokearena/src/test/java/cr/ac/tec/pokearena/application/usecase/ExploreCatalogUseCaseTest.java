package cr.ac.tec.pokearena.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import cr.ac.tec.pokearena.application.dto.CardDetails;
import cr.ac.tec.pokearena.application.dto.CatalogCardDetails;
import cr.ac.tec.pokearena.application.dto.ExploreCatalogCommand;
import cr.ac.tec.pokearena.application.dto.Page;
import cr.ac.tec.pokearena.application.port.CatalogCache;
import cr.ac.tec.pokearena.application.port.CatalogCacheKey;
import cr.ac.tec.pokearena.domain.error.ValidationException;
import cr.ac.tec.pokearena.domain.model.Ability;
import cr.ac.tec.pokearena.domain.model.Card;
import cr.ac.tec.pokearena.domain.model.CardCode;
import cr.ac.tec.pokearena.domain.model.CardFields;
import cr.ac.tec.pokearena.domain.model.CardType;
import cr.ac.tec.pokearena.domain.model.CatalogCriteria;
import cr.ac.tec.pokearena.domain.model.OwnedCard;
import cr.ac.tec.pokearena.domain.model.Rarity;
import cr.ac.tec.pokearena.domain.model.StoredCard;
import cr.ac.tec.pokearena.domain.repository.CardCatalogRepository;
import cr.ac.tec.pokearena.domain.repository.PlayerCatalogRepository;

/**
 * Propósito: pruebas unitarias del caso de uso de la historia 3.1.2 "Explorar el catálogo y mi colección",
 * sin Spring (criterios C.6).
 * Responsabilidades: verificar la frontera entre la caché pública y las colecciones privadas: un acierto de
 * la caché no reutiliza copias, {@code owned=true} omite la caché, la validación ocurre antes de leer nada
 * y las claves de la caché usan los filtros normalizados.
 * Referencia: sección 8.3, sección 9 y Anexo A.6.
 *
 * <p>Misma estructura que las demás historias: {@code @Nested} Éxito, Errores y Alternos citando C.6.
 */
@DisplayName("Historia 3.1.2: caché pública y propiedad privada")
class ExploreCatalogUseCaseTest {

    private static final UUID FIRST = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID SECOND = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final String CODE = "PKA-FUE-001";
    private final MemoryData data = new MemoryData();
    private final MemoryCache cache = new MemoryCache();
    private final ExploreCatalogUseCase useCase = new ExploreCatalogUseCase(new Pagination(2, 5), data, data, cache);

    @Nested
    @DisplayName("Éxito")
    class Success {

        @Test
        @DisplayName("C.6 éxito: cada carta indica si el jugador la posee y cuántas copias tiene; la página compartida no lleva copias y cada consulta las lee en un lote (A.6)")
        void cachedPageNeverContainsOwnership() {
            data.copies.put(FIRST, 3);
            Page<CatalogCardDetails> a = explore(FIRST, Map.of());
            Page<CatalogCardDetails> b = explore(SECOND, Map.of());
            data.copies.put(FIRST, 7);
            Page<CatalogCardDetails> updated = explore(FIRST, Map.of());

            assertThat(a.items().getFirst().copies()).isEqualTo(3);
            assertThat(b.items().getFirst().copies()).isZero();
            assertThat(b.items().getFirst().owned()).isFalse();
            assertThat(updated.items().getFirst().copies()).isEqualTo(7);
            assertThat(data.availableReads).isEqualTo(1);
            assertThat(data.collectionReads).containsExactly(FIRST, SECOND, FIRST);
            assertThat(data.requestedCodes).containsExactly(List.of(CODE), List.of(CODE), List.of(CODE));
            assertThat(cache.pages.values().iterator().next().items().getFirst()).isInstanceOf(CardDetails.class);
            assertThat(cache.pages.keySet().iterator().next()).startsWith(CatalogCacheKey.PLAYER_CATALOG + ":")
                    .doesNotStartWith(CatalogCacheKey.ADMIN_SEARCH + ":");
        }

        @Test
        @DisplayName("C.6 éxito: el filtro de cartas propias lee cartas y copias juntas, sin caché ni consulta extra de copias (A.6)")
        void ownedBypassesCache() {
            data.copies.put(FIRST, 4);
            assertThat(explore(FIRST, Map.of("owned", List.of("true"))).items().getFirst().copies()).isEqualTo(4);
            assertThat(explore(SECOND, Map.of("owned", List.of("true"))).items()).isEmpty();
            assertThat(data.ownedReads).containsExactly(FIRST, SECOND);
            assertThat(data.availableReads).isZero();
            assertThat(data.collectionReads).isEmpty();
            assertThat(cache.gets).isZero();
            assertThat(cache.pages).isEmpty();
        }
    }

    @Nested
    @DisplayName("Errores")
    class Errors {

        @Test
        @DisplayName("C.6 errores: filtros o paginación inválidos se rechazan sin consultar páginas, propiedad ni caché (A.6, mensaje 6)")
        void validatesBeforeReadingPages() {
            for (Map<String, List<String>> parameters : List.of(Map.of("type", List.of("INVALID")),
                    Map.of("limit", List.of("0")), Map.of("owned", List.of("yes")))) {
                assertThatThrownBy(() -> explore(FIRST, parameters)).isInstanceOf(ValidationException.class);
            }
            assertThat(data.availableReads).isZero();
            assertThat(data.collectionReads).isEmpty();
            assertThat(cache.gets).isZero();
        }
    }

    @Nested
    @DisplayName("Alternos")
    class Alternatives {

        @Test
        @DisplayName("C.6 alternos: una página sin resultados devuelve una lista vacía, no un error, y no pide copias de cartas inexistentes")
        void emptyPage() {
            Page<CatalogCardDetails> empty = explore(FIRST, Map.of("cursor", List.of(Pagination.encode(1))));

            assertThat(empty.items()).isEmpty();
            assertThat(empty.nextCursor()).isNull();
            assertThat(data.requestedCodes).containsExactly(List.of());
        }

        @Test
        @DisplayName("Sección 9: filtros equivalentes comparten página en la caché; filtros distintos y cursores las separan")
        void normalizedKeys() {
            explore(FIRST, Map.of("costMin", List.of(" 3 ")));
            explore(SECOND, Map.of("costMin", List.of("3"), "owned", List.of("false")));
            assertThat(cache.pages).hasSize(1);
            explore(FIRST, Map.of("costMin", List.of("4")));
            explore(FIRST, Map.of("costMin", List.of("3"), "cursor", List.of(Pagination.encode(1))));
            assertThat(cache.pages).hasSize(3);
        }
    }

    private Page<CatalogCardDetails> explore(UUID player, Map<String, List<String>> parameters) {
        return useCase.execute(new ExploreCatalogCommand(player, parameters));
    }

    /** Datos mínimos: cuentan lecturas para detectar N+1 y la reutilización de datos personales. */
    private static final class MemoryData implements CardCatalogRepository, PlayerCatalogRepository {
        final Map<UUID, Integer> copies = new HashMap<>();
        final List<UUID> collectionReads = new ArrayList<>();
        final List<List<String>> requestedCodes = new ArrayList<>();
        final List<UUID> ownedReads = new ArrayList<>();
        int availableReads;
        final StoredCard row = new StoredCard(1, Card.create(new CardCode(CODE),
                new CardFields("Carta", "carta", 3, 1, new CardType("FUE", "Fuego"), new Rarity("COMMON", "Común")),
                new Ability("HAB-000", "Sin Habilidad", null, null), "/images/large.png", "/images/small.png",
                Instant.parse("2026-11-09T00:00:00Z")));

        @Override
        public List<CardType> findAllTypes() {
            return List.of(new CardType("FUE", "Fuego"));
        }

        @Override
        public List<Rarity> findAllRarities() {
            return List.of(new Rarity("COMMON", "Común"));
        }

        @Override
        public List<StoredCard> available(CatalogCriteria criteria, long afterId, int maxRows) {
            availableReads++;
            return afterId < row.id() ? List.of(row) : List.of();
        }

        @Override
        public List<OwnedCard> owned(UUID player, CatalogCriteria criteria, long afterId, int maxRows) {
            ownedReads.add(player);
            return copies.containsKey(player) ? List.of(new OwnedCard(row, copies.get(player))) : List.of();
        }

        @Override
        public Map<String, Integer> findCopies(UUID player, Collection<String> codes) {
            collectionReads.add(player);
            requestedCodes.add(List.copyOf(codes));
            return copies.containsKey(player) ? Map.of(CODE, copies.get(player)) : Map.of();
        }


    }

    /** Simula hits reales del puerto existente sin añadir Redis al alcance. */
    private static final class MemoryCache implements CatalogCache {
        final Map<String, Page<?>> pages = new HashMap<>();
        int gets;

        @Override
        @SuppressWarnings("unchecked")
        public <T> Optional<Page<T>> get(CatalogCacheKey key, Class<T> itemType) {
            gets++;
            return Optional.ofNullable((Page<T>) pages.get(key.asText()));
        }

        @Override
        public void put(CatalogCacheKey key, Page<?> page) {
            pages.put(key.asText(), page);
        }
    }
}
