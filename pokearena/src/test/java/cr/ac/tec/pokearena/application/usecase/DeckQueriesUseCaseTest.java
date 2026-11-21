package cr.ac.tec.pokearena.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import cr.ac.tec.pokearena.application.dto.DeckSummary;
import cr.ac.tec.pokearena.application.dto.GetDeckCommand;
import cr.ac.tec.pokearena.application.dto.ListMyDecksCommand;
import cr.ac.tec.pokearena.application.dto.Page;
import cr.ac.tec.pokearena.domain.error.DomainException;
import cr.ac.tec.pokearena.domain.error.ErrorType;
import cr.ac.tec.pokearena.domain.model.Deck;
import cr.ac.tec.pokearena.domain.model.NewDeck;
import cr.ac.tec.pokearena.domain.model.StoredDeck;
import cr.ac.tec.pokearena.domain.repository.DeckRepository;

/**
 * Propósito: pruebas unitarias de {@link ListMyDecksUseCase} y {@link GetDeckUseCase} con un repositorio en
 * memoria (historia 3.1.3).
 * Responsabilidades: comprobar la paginación de "Mis decks" con la estrategia única ({@link Pagination}) y
 * que un deck ajeno, inexistente o con un id que no es un número da el mismo {@code DECK_NOT_FOUND}. Sin
 * Spring ni base de datos.
 * Referencia: sección 7.1 ("Listar y consultar"), sección 5.5 y Anexo A.8; criterios C.7-E2 y R4.
 */
@DisplayName("Casos de uso Listar mis decks y Consultar un deck (puertos en memoria)")
class DeckQueriesUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-11-19T12:00:00Z");
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID STRANGER = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final int DEFAULT_LIMIT = 2;
    private static final int MAX_LIMIT = 5;

    private final InMemoryDecks decks = new InMemoryDecks();
    private final ListMyDecksUseCase list = new ListMyDecksUseCase(new Pagination(DEFAULT_LIMIT, MAX_LIMIT), decks);
    private final GetDeckUseCase get = new GetDeckUseCase(decks);

    @Nested
    @DisplayName("Listar mis decks")
    class ListDecks {

        @Test
        @DisplayName("C.7-E2: recorre las páginas con nextCursor, solo con los decks del jugador y sin repetir")
        void walksPages() {
            for (int i = 1; i <= 5; i++) {
                decks.add(OWNER, "Mío " + i);
                decks.add(STRANGER, "Ajeno " + i);
            }

            List<String> names = new ArrayList<>();
            String cursor = null;
            do {
                Map<String, List<String>> parameters = cursor == null ? Map.of()
                        : Map.of(Pagination.CURSOR, List.of(cursor));
                Page<DeckSummary> page = list.execute(new ListMyDecksCommand(OWNER, parameters));
                assertThat(page.items()).hasSizeLessThanOrEqualTo(DEFAULT_LIMIT);
                page.items().forEach(deck -> names.add(deck.name()));
                cursor = page.nextCursor();
            } while (cursor != null);

            assertThat(names).containsExactly("Mío 1", "Mío 2", "Mío 3", "Mío 4", "Mío 5");
        }

        @Test
        @DisplayName("Sin decks devuelve una página vacía, sin cursor")
        void empty() {
            Page<DeckSummary> page = list.execute(new ListMyDecksCommand(OWNER, Map.of()));

            assertThat(page.items()).isEmpty();
            assertThat(page.nextCursor()).isNull();
        }

        @ParameterizedTest(name = "limit={0}")
        @ValueSource(strings = { "0", "6", "abc" })
        @DisplayName("Un limit fuera de rango o no numérico es INVALID_PAGINATION")
        void invalidLimit(String limit) {
            assertThatThrownBy(() -> list.execute(new ListMyDecksCommand(OWNER, Map.of(Pagination.LIMIT,
                    List.of(limit))))).isInstanceOfSatisfying(DomainException.class,
                            error -> assertThat(error.type()).isEqualTo(ErrorType.INVALID_PAGINATION));
        }
    }

    @Nested
    @DisplayName("Consultar un deck")
    class GetDeck {

        @Test
        @DisplayName("El dueño lo consulta con sus datos")
        void ownerReadsDeck() {
            long id = decks.add(OWNER, "Fuego");

            assertThat(get.execute(new GetDeckCommand(OWNER, Long.toString(id))).name()).isEqualTo("Fuego");
        }

        @ParameterizedTest(name = "id \"{0}\"")
        @ValueSource(strings = { "999", "abc", "0", "-1", "" })
        @DisplayName("C.7-R4: un id inexistente, no numérico o no positivo es DECK_NOT_FOUND")
        void missing(String id) {
            assertNotFound(new GetDeckCommand(OWNER, id));
        }

        @Test
        @DisplayName("C.7-R4: el deck de otro jugador es DECK_NOT_FOUND, con el mismo mensaje que uno inexistente")
        void foreignDeck() {
            long id = decks.add(OWNER, "Fuego");

            DomainException foreign = assertNotFound(new GetDeckCommand(STRANGER, Long.toString(id)));
            DomainException missing = assertNotFound(new GetDeckCommand(STRANGER, "999"));
            assertThat(foreign.getMessage()).isEqualTo(missing.getMessage());
        }

        private DomainException assertNotFound(GetDeckCommand command) {
            try {
                get.execute(command);
            } catch (DomainException error) {
                assertThat(error.type()).isEqualTo(ErrorType.DECK_NOT_FOUND);
                assertThat(error.field()).isEmpty();
                return error;
            }
            throw new AssertionError("Se esperaba DECK_NOT_FOUND");
        }
    }

    /** Decks en memoria, sin cartas: estas pruebas solo miran la paginación y la pertenencia. */
    private static final class InMemoryDecks implements DeckRepository {
        private final List<StoredDeck> rows = new ArrayList<>();

        long add(UUID player, String name) {
            StoredDeck deck = new StoredDeck(rows.size() + 1, player, name, 0, NOW);
            rows.add(deck);
            return deck.id();
        }

        @Override
        public StoredDeck save(NewDeck deck) {
            throw new UnsupportedOperationException("Estas pruebas no crean decks");
        }

        @Override
        public Optional<Deck> findByIdAndPlayer(long deckId, UUID playerId) {
            return rows.stream().filter(deck -> deck.id() == deckId && deck.playerId().equals(playerId)).findFirst()
                    .map(deck -> new Deck(deck, List.of()));
        }

        @Override
        public List<StoredDeck> findByPlayer(UUID playerId, long afterId, int maxRows) {
            return rows.stream().filter(deck -> deck.playerId().equals(playerId) && deck.id() > afterId)
                    .limit(maxRows).toList();
        }

        @Override
        public List<String> findCardCodes(long deckId) {
            throw new UnsupportedOperationException("Estas pruebas no leen códigos");
        }
    }
}
