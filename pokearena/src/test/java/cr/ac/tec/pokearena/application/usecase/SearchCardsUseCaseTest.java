package cr.ac.tec.pokearena.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import cr.ac.tec.pokearena.application.dto.CardDetails;
import cr.ac.tec.pokearena.application.dto.Page;
import cr.ac.tec.pokearena.application.dto.SearchCardsCommand;
import cr.ac.tec.pokearena.application.port.CatalogCache;
import cr.ac.tec.pokearena.application.port.CatalogCacheKey;
import cr.ac.tec.pokearena.domain.error.ErrorType;
import cr.ac.tec.pokearena.domain.error.ValidationException;
import cr.ac.tec.pokearena.domain.model.Ability;
import cr.ac.tec.pokearena.domain.model.Card;
import cr.ac.tec.pokearena.domain.model.CardCode;
import cr.ac.tec.pokearena.domain.model.CardFields;
import cr.ac.tec.pokearena.domain.model.CardSearchCriteria;
import cr.ac.tec.pokearena.domain.model.CardType;
import cr.ac.tec.pokearena.domain.model.Rarity;
import cr.ac.tec.pokearena.domain.model.StoredCard;
import cr.ac.tec.pokearena.domain.repository.CardCatalogRepository;
import cr.ac.tec.pokearena.domain.repository.CardRepository;

/**
 * Propósito: pruebas unitarias de {@link SearchCardsUseCase} con los puertos en memoria (historia 2.1.2).
 * Responsabilidades: comprobar el orden del diagrama A.3 (validar antes de consultar; caché antes que
 * la base; guardar la página en la caché), la paginación con la fila extra y la clave de la caché.
 * Sin Spring ni base de datos.
 * Referencia: sección 7.3, sección 9 y Anexo A.3.
 *
 * <p>El repositorio en memoria aplica solo el cursor y el límite (los filtros SQL se prueban de punta a
 * punta en {@code CardSearchIT}); guarda los criterios que recibió para poder revisarlos.
 */
@DisplayName("Caso de uso Buscar carta (puertos en memoria)")
class SearchCardsUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-11-06T12:00:00Z");
    private static final int DEFAULT_LIMIT = 2;
    private static final int MAX_LIMIT = 5;

    private final InMemoryCards cards = new InMemoryCards();
    private final InMemoryCache cache = new InMemoryCache();
    private final SearchCardsUseCase useCase = new SearchCardsUseCase(new Pagination(DEFAULT_LIMIT, MAX_LIMIT),
            new FixedCatalog(), cards, cache);

    private Page<CardDetails> search(String... pairs) {
        Map<String, List<String>> parameters = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            parameters.put(pairs[i], List.of(pairs[i + 1]));
        }
        return useCase.execute(new SearchCardsCommand(parameters));
    }

    private static List<String> codes(Page<CardDetails> page) {
        return page.items().stream().map(CardDetails::code).toList();
    }

    @Nested
    @DisplayName("Orden del diagrama A.3")
    class Order {

        @Test
        @DisplayName("Un criterio inválido se rechaza antes de consultar la caché o la base")
        void validatesBeforeQuerying() {
            assertThatThrownBy(() -> search("type", "FIRE"))
                    .isInstanceOfSatisfying(ValidationException.class,
                            error -> assertThat(error.type()).isEqualTo(ErrorType.INVALID_SEARCH_CRITERIA));

            assertThat(cache.gets).isZero();
            assertThat(cards.searches).isEmpty();
        }

        @Test
        @DisplayName("Una paginación inválida se rechaza antes de consultar la caché o la base")
        void validatesPaginationBeforeQuerying() {
            assertThatThrownBy(() -> search("limit", "0"))
                    .isInstanceOfSatisfying(ValidationException.class,
                            error -> assertThat(error.type()).isEqualTo(ErrorType.INVALID_PAGINATION));

            assertThat(cache.gets).isZero();
            assertThat(cards.searches).isEmpty();
        }

        @Test
        @DisplayName("Primero los criterios y luego la paginación: con ambos inválidos se informa el criterio")
        void criteriaBeforePagination() {
            assertThatThrownBy(() -> search("cost", "11", "limit", "0"))
                    .isInstanceOfSatisfying(ValidationException.class,
                            error -> assertThat(error.type()).isEqualTo(ErrorType.INVALID_SEARCH_CRITERIA));
        }

        @Test
        @DisplayName("Página que no está en caché: consulta la base con el cursor y el límite más uno, y la guarda")
        void missQueriesDatabaseAndStores() {
            cards.add(3);

            Page<CardDetails> page = search("type", "FUE");

            assertThat(cards.searches).hasSize(1);
            assertThat(cards.searches.getFirst().afterId()).isZero();
            assertThat(cards.searches.getFirst().maxRows()).isEqualTo(DEFAULT_LIMIT + 1);
            assertThat(cards.searches.getFirst().criteria().typeCode()).contains("FUE");
            assertThat(cache.pages).hasSize(1);
            assertThat(codes(page)).containsExactly("PKA-FUE-001", "PKA-FUE-002");
        }

        @Test
        @DisplayName("Página en caché: se responde sin consultar la base")
        void hitSkipsDatabase() {
            cards.add(3);
            Page<CardDetails> first = search();

            Page<CardDetails> again = search();

            assertThat(cards.searches).hasSize(1);
            assertThat(again).isEqualTo(first);
        }
    }

    @Nested
    @DisplayName("Paginación")
    class Paging {

        @Test
        @DisplayName("Recorre todo con nextCursor, sin repetidos ni omitidos, y la última página trae null")
        void traversesEverything() {
            cards.add(5);
            List<String> seen = new ArrayList<>();

            Page<CardDetails> page = search();
            seen.addAll(codes(page));
            while (page.nextCursor() != null) {
                page = search("cursor", page.nextCursor());
                seen.addAll(codes(page));
            }

            assertThat(seen).containsExactly("PKA-FUE-001", "PKA-FUE-002", "PKA-FUE-003", "PKA-FUE-004",
                    "PKA-FUE-005");
            assertThat(page.limit()).isEqualTo(DEFAULT_LIMIT);
        }

        @Test
        @DisplayName("Sin resultados: lista vacía y nextCursor null, no un error")
        void emptyResult() {
            Page<CardDetails> page = search("type", "AGU");

            assertThat(page.items()).isEmpty();
            assertThat(page.nextCursor()).isNull();
        }
    }

    @Nested
    @DisplayName("Caché")
    class Cache {

        @Test
        @DisplayName("C.3 alterno: una carta recién creada puede tardar en aparecer mientras la página siga en caché")
        void newCardMayTakeAWhile() {
            cards.add(1);
            assertThat(codes(search())).containsExactly("PKA-FUE-001");

            cards.add(1);

            // La página guardada sigue vigente: la carta nueva aparece cuando la caché vence (sección 9).
            assertThat(codes(search())).containsExactly("PKA-FUE-001");
            cache.pages.clear();
            assertThat(codes(search())).containsExactly("PKA-FUE-001", "PKA-FUE-002");
        }

        @Test
        @DisplayName("Búsquedas equivalentes comparten página: name=DRAGÓN y name=dragon, cost=3 y costMin=3&costMax=3")
        void equivalentSearchesShareKey() {
            search("name", "DRAGÓN");
            search("name", "dragon");
            search("cost", "3");
            search("costMin", "3", "costMax", "3");

            assertThat(cache.pages).hasSize(2);
            assertThat(cards.searches).hasSize(2);
        }

        @Test
        @DisplayName("La clave separa el espacio de búsqueda del administrador, el cursor y el límite")
        void keyParts() {
            cards.add(3);
            Page<CardDetails> first = search();
            search("cursor", first.nextCursor());
            search("limit", "1");

            assertThat(cache.pages.keySet()).hasSize(3)
                    .allSatisfy(key -> assertThat(key).startsWith(CatalogCacheKey.ADMIN_SEARCH + ":"));
            assertThat(cache.pages.keySet()).anySatisfy(key -> assertThat(key).endsWith(":first:" + DEFAULT_LIMIT));
        }
    }

    // =========================================================================================
    // Puertos en memoria.
    // =========================================================================================

    private static final class FixedCatalog implements CardCatalogRepository {
        @Override
        public List<CardType> findAllTypes() {
            return List.of(new CardType("AGU", "Agua"), new CardType("FUE", "Fuego"));
        }

        @Override
        public List<Rarity> findAllRarities() {
            return List.of(new Rarity("COMMON", "Común"));
        }
    }

    private record SearchCall(CardSearchCriteria criteria, long afterId, int maxRows) {
    }

    /** Cartas de fuego numeradas desde 1; el id coincide con el número. */
    private static final class InMemoryCards implements CardRepository {
        final List<StoredCard> rows = new ArrayList<>();
        final List<SearchCall> searches = new ArrayList<>();

        void add(int count) {
            for (int i = 0; i < count; i++) {
                int number = rows.size() + 1;
                Card card = Card.create(CardCode.of("FUE", number),
                        new CardFields("Carta " + number, "carta " + number, 1, 1, new CardType("FUE", "Fuego"),
                                new Rarity("COMMON", "Común")),
                        new Ability(Ability.NO_ABILITY_CODE, "Sin Habilidad", null, null),
                        "/images/l.png", "/images/s.png", NOW);
                rows.add(new StoredCard(number, card));
            }
        }

        @Override
        public List<StoredCard> search(CardSearchCriteria criteria, long afterId, int maxRows) {
            searches.add(new SearchCall(criteria, afterId, maxRows));
            if (criteria.typeCode().filter(type -> !type.equals("FUE")).isPresent()) {
                return List.of();
            }
            return rows.stream().filter(row -> row.id() > afterId).limit(maxRows).toList();
        }

        @Override
        public int nextNumber(String typeCode) {
            throw new UnsupportedOperationException("Buscar carta no crea cartas");
        }

        @Override
        public void save(Card card) {
            throw new UnsupportedOperationException("Buscar carta no crea cartas");
        }

        @Override
        public Optional<String> evolutionTargetOf(String cardCode) {
            throw new UnsupportedOperationException("Buscar carta no revisa evoluciones");
        }
    }

    /** Caché sin vencimiento: la prueba la vacía a mano para simular que venció. */
    private static final class InMemoryCache implements CatalogCache {
        final Map<String, Page<?>> pages = new HashMap<>();
        int gets;
        int invalidations;

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

        @Override
        public void invalidate() {
            invalidations++;
        }
    }
}
