package cr.ac.tec.pokearena.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import cr.ac.tec.pokearena.application.dto.CreateDeckCommand;
import cr.ac.tec.pokearena.application.dto.DeckDetails;
import cr.ac.tec.pokearena.application.dto.IdempotentRequest;
import cr.ac.tec.pokearena.application.dto.IdempotentResult;
import cr.ac.tec.pokearena.application.port.IdempotencyKey;
import cr.ac.tec.pokearena.application.port.IdempotencyStore;
import cr.ac.tec.pokearena.application.port.ResponseSerializer;
import cr.ac.tec.pokearena.application.port.StoredResponse;
import cr.ac.tec.pokearena.application.port.UnitOfWork;
import cr.ac.tec.pokearena.domain.error.DeckException;
import cr.ac.tec.pokearena.domain.error.DomainException;
import cr.ac.tec.pokearena.domain.error.ErrorType;
import cr.ac.tec.pokearena.domain.error.IdempotencyConflictException;
import cr.ac.tec.pokearena.domain.model.Ability;
import cr.ac.tec.pokearena.domain.model.Card;
import cr.ac.tec.pokearena.domain.model.CardCode;
import cr.ac.tec.pokearena.domain.model.CardFields;
import cr.ac.tec.pokearena.domain.model.CardType;
import cr.ac.tec.pokearena.domain.model.Deck;
import cr.ac.tec.pokearena.domain.model.NewDeck;
import cr.ac.tec.pokearena.domain.model.OwnedCard;
import cr.ac.tec.pokearena.domain.model.Rarity;
import cr.ac.tec.pokearena.domain.model.StoredCard;
import cr.ac.tec.pokearena.domain.model.StoredDeck;
import cr.ac.tec.pokearena.domain.repository.DeckRepository;
import cr.ac.tec.pokearena.domain.repository.PlayerCardRepository;
import cr.ac.tec.pokearena.domain.rule.DeckRules;

/**
 * Propósito: pruebas unitarias de {@link CreateDeckUseCase} con los puertos en memoria (historia 3.1.3).
 * Responsabilidades: comprobar el orden del diagrama A.7 (idempotencia, forma, posesión en una consulta,
 * transacción), que nada se guarda si una validación falla, que la hora viene del puerto Clock, y el manejo
 * del nombre repetido: un choque con otro deck es {@code DECK_NAME_TAKEN}, y el de un reintento de la misma
 * solicitud repite su respuesta. Sin Spring ni base de datos: la concurrencia real se prueba con PostgreSQL.
 * Referencia: sección 7.1 y Anexo A.7; criterios C.7-E1, R3, A1 y A2.
 */
@DisplayName("Caso de uso Crear deck (puertos en memoria)")
class CreateDeckUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-11-19T12:00:00Z");
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String ENDPOINT = "POST /api/v1/decks";
    private static final int CREATED = 201;

    private final InMemoryCollection collection = new InMemoryCollection();
    private final InMemoryDecks decks = new InMemoryDecks();
    private final InMemoryIdempotencyStore keys = new InMemoryIdempotencyStore();
    private final CreateDeckUseCase useCase = new CreateDeckUseCase(new IdempotencyGuard(keys, new ObjectSerializer()),
            collection, decks, new SnapshotUnitOfWork(), () -> NOW);

    private static List<String> codes(int count) {
        return IntStream.rangeClosed(1, count).mapToObj(n -> "PKA-FUE-%03d".formatted(n)).toList();
    }

    private static IdempotentRequest request(String key, String hash) {
        return new IdempotentRequest(new IdempotencyKey(key, PLAYER, ENDPOINT), hash, CREATED);
    }

    private IdempotentResult<DeckDetails> create(String key, String hash, String name, List<String> cards) {
        return useCase.execute(request(key, hash), new CreateDeckCommand(PLAYER, name, cards));
    }

    private IdempotentResult<DeckDetails> createValid(String key, String name) {
        collection.own(PLAYER, codes(DeckRules.DECK_SIZE));
        return create(key, "huella-" + name, name, codes(DeckRules.DECK_SIZE));
    }

    @Nested
    @DisplayName("Éxito")
    class Success {

        @Test
        @DisplayName("C.7-E1: con 12 cartas distintas poseídas y un nombre, guarda el deck y su clave en una transacción")
        void createsDeck() {
            IdempotentResult<DeckDetails> result = createValid("k1", "Fuego");

            assertThat(result.status()).isEqualTo(CREATED);
            assertThat(result.replayed()).isFalse();
            assertThat(result.body().name()).isEqualTo("Fuego");
            assertThat(result.body().createdAt()).isEqualTo(NOW);
            assertThat(result.body().cards()).extracting(DeckDetails.DeckCard::code)
                    .containsExactlyElementsOf(codes(DeckRules.DECK_SIZE));
            assertThat(decks.saved).hasSize(1);
            assertThat(keys.responses).hasSize(1);
        }

        @Test
        @DisplayName("La posesión se comprueba con UNA consulta para las 12 cartas")
        void ownershipInOneQuery() {
            createValid("k1", "Fuego");

            assertThat(collection.queries).isEqualTo(1);
        }

        @Test
        @DisplayName("C.7-A1: la misma clave y la misma huella repiten la respuesta sin crear otro deck")
        void retryReplays() {
            IdempotentResult<DeckDetails> first = createValid("k1", "Fuego");
            IdempotentResult<DeckDetails> second = createValid("k1", "Fuego");

            assertThat(second.replayed()).isTrue();
            assertThat(second.body()).isEqualTo(first.body());
            assertThat(decks.saved).hasSize(1);
        }
    }

    @Nested
    @DisplayName("Errores")
    class Errors {

        @Test
        @DisplayName("Una forma inválida se rechaza antes de consultar la colección y sin guardar nada")
        void shapeBeforeOwnership() {
            assertThatThrownBy(() -> create("k1", "h", "Fuego", codes(DeckRules.DECK_SIZE - 1)))
                    .isInstanceOfSatisfying(DomainException.class,
                            error -> assertThat(error.type()).isEqualTo(ErrorType.INVALID_DECK_SIZE));
            assertThat(collection.queries).isZero();
            assertThat(decks.saved).isEmpty();
            assertThat(keys.responses).isEmpty();
        }

        @Test
        @DisplayName("C.7-R3: con una carta no poseída es CARD_NOT_OWNED y no se guarda nada")
        void notOwned() {
            collection.own(PLAYER, codes(DeckRules.DECK_SIZE).subList(1, DeckRules.DECK_SIZE));

            assertThatThrownBy(() -> create("k1", "h", "Fuego", codes(DeckRules.DECK_SIZE)))
                    .isInstanceOfSatisfying(DomainException.class, error -> {
                        assertThat(error.type()).isEqualTo(ErrorType.CARD_NOT_OWNED);
                        assertThat(error.getMessage()).contains("PKA-FUE-001");
                    });
            assertThat(decks.saved).isEmpty();
            assertThat(keys.responses).isEmpty();
        }

        @Test
        @DisplayName("Misma clave con otra huella es IDEMPOTENCY_KEY_REUSED, sin validar ni guardar")
        void keyReused() {
            createValid("k1", "Fuego");

            assertThatThrownBy(() -> create("k1", "otra-huella", "Fuego", codes(DeckRules.DECK_SIZE)))
                    .isInstanceOfSatisfying(DomainException.class,
                            error -> assertThat(error.type()).isEqualTo(ErrorType.IDEMPOTENCY_KEY_REUSED));
            assertThat(decks.saved).hasSize(1);
        }

        @Test
        @DisplayName("Si falla guardar la clave, la transacción revierte también el deck")
        void failureRollsBackDeck() {
            keys.failNextSave = true;

            assertThatThrownBy(() -> createValid("k1", "Fuego")).isInstanceOf(IllegalStateException.class);
            assertThat(decks.saved).isEmpty();
        }
    }

    @Nested
    @DisplayName("Nombre repetido e idempotencia (mensajes 28 a 33 de A.7)")
    class NameTaken {

        @Test
        @DisplayName("C.7-A2: un nombre usado por otro deck del jugador es DECK_NAME_TAKEN, y el error no se guarda")
        void nameUsedByAnotherDeck() {
            createValid("k1", "Fuego");

            assertThatThrownBy(() -> createValid("k2", "FUEGO"))
                    .isInstanceOfSatisfying(DomainException.class,
                            error -> assertThat(error.type()).isEqualTo(ErrorType.DECK_NAME_TAKEN));
            assertThat(keys.responses).hasSize(1);
            assertThat(decks.saved).hasSize(1);
        }

        @Test
        @DisplayName("Reintento posterior con la misma clave tras un 409: se vuelve a ejecutar y da 409 otra vez")
        void retryAfterConflictRunsAgain() {
            createValid("k1", "Fuego");
            for (int attempt = 0; attempt < 2; attempt++) {
                assertThatThrownBy(() -> createValid("k2", "Fuego"))
                        .isInstanceOfSatisfying(DomainException.class,
                                error -> assertThat(error.type()).isEqualTo(ErrorType.DECK_NAME_TAKEN));
            }
            assertThat(decks.saveAttempts).isEqualTo(3);
        }

        @Test
        @DisplayName("C.7-A1: si el choque de nombre es con la misma solicitud que confirmó primero, repite su respuesta")
        void sameRequestWonFirst() {
            IdempotentResult<DeckDetails> winner = createValid("k1", "Fuego");
            // Simula la carrera: findPrevious no ve la clave (la otra aún no confirmaba), pero al guardar la
            // otra ya confirmó el deck y la clave.
            keys.hideOnNextFind = true;

            IdempotentResult<DeckDetails> retry = createValid("k1", "Fuego");

            assertThat(retry.replayed()).isTrue();
            assertThat(retry.body()).isEqualTo(winner.body());
            assertThat(decks.saved).hasSize(1);
        }

        @Test
        @DisplayName("Choque de nombre con la misma clave y otra huella: IDEMPOTENCY_KEY_REUSED")
        void sameKeyOtherContent() {
            createValid("k1", "Fuego");
            keys.hideOnNextFind = true;

            assertThatThrownBy(() -> create("k1", "otra-huella", "Fuego", codes(DeckRules.DECK_SIZE)))
                    .isInstanceOfSatisfying(DomainException.class,
                            error -> assertThat(error.type()).isEqualTo(ErrorType.IDEMPOTENCY_KEY_REUSED));
        }

        @Test
        @DisplayName("Conflicto de clave al registrar (IdempotencyConflictException): repite la respuesta de la otra")
        void idempotencyConflictResolves() {
            collection.own(PLAYER, codes(DeckRules.DECK_SIZE));
            keys.conflictOnNextSave = true;

            IdempotentResult<DeckDetails> result = create("k1", "h", "Fuego", codes(DeckRules.DECK_SIZE));

            assertThat(result.replayed()).isTrue();
            assertThat(decks.saved).isEmpty();
        }
    }

    // =========================================================================================
    // Puertos en memoria.
    // =========================================================================================

    private interface Transactional {
        Runnable snapshot();
    }

    private final class SnapshotUnitOfWork implements UnitOfWork {
        @Override
        public <T> T execute(Supplier<T> work) {
            List<Runnable> restores = List.of(decks.snapshot(), keys.snapshot());
            try {
                return work.get();
            } catch (RuntimeException failure) {
                restores.forEach(Runnable::run);
                throw failure;
            }
        }
    }

    private static Card card(String code) {
        CardFields fields = new CardFields("Carta " + code, "carta " + code, 1, 1, new CardType("FUE", "Fuego"),
                new Rarity("COMMON", "Común"));
        return Card.create(new CardCode(code), fields, new Ability("HAB-000", "Sin Habilidad", null, null),
                "/images/l.png", "/images/s.png", NOW);
    }

    private static final class InMemoryCollection implements PlayerCardRepository {
        final Map<UUID, Set<String>> owned = new HashMap<>();
        int queries;

        void own(UUID player, Collection<String> codes) {
            owned.computeIfAbsent(player, key -> new HashSet<>()).addAll(codes);
        }

        @Override
        public void addOneCopyOfEach(UUID playerId, Collection<String> cardCodes) {
            own(playerId, cardCodes);
        }

        @Override
        public List<OwnedCard> findOwnedCards(UUID playerId, Collection<String> cardCodes) {
            queries++;
            Set<String> mine = owned.getOrDefault(playerId, Set.of());
            return cardCodes.stream().filter(mine::contains).sorted()
                    .map(code -> new OwnedCard(new StoredCard(1, card(code)), 1)).toList();
        }
    }

    private static final class InMemoryDecks implements DeckRepository, Transactional {
        final List<StoredDeck> saved = new ArrayList<>();
        final Map<String, UUID> names = new HashMap<>();
        int saveAttempts;

        @Override
        public StoredDeck save(NewDeck deck) {
            saveAttempts++;
            String key = deck.playerId() + "/" + deck.nameNormalized();
            if (names.containsKey(key)) {
                throw DeckException.nameTaken("Ya tienes un deck llamado \"" + deck.name() + "\".");
            }
            names.put(key, deck.playerId());
            StoredDeck stored = new StoredDeck(saved.size() + 1, deck.playerId(), deck.name(), deck.cardCodes().size(),
                    deck.createdAt());
            saved.add(stored);
            return stored;
        }

        @Override
        public Optional<Deck> findByIdAndPlayer(long deckId, UUID playerId) {
            throw new UnsupportedOperationException("Crear deck no lee decks");
        }

        @Override
        public List<StoredDeck> findByPlayer(UUID playerId, long afterId, int maxRows) {
            throw new UnsupportedOperationException("Crear deck no lista decks");
        }

        @Override
        public List<String> findCardCodes(long deckId) {
            throw new UnsupportedOperationException("Crear deck no lee códigos");
        }

        @Override
        public Runnable snapshot() {
            List<StoredDeck> savedBefore = new ArrayList<>(saved);
            Map<String, UUID> namesBefore = new HashMap<>(names);
            return () -> {
                saved.clear();
                saved.addAll(savedBefore);
                names.clear();
                names.putAll(namesBefore);
            };
        }
    }

    private static final class InMemoryIdempotencyStore implements IdempotencyStore, Transactional {
        final Map<IdempotencyKey, StoredResponse> responses = new HashMap<>();
        boolean hideOnNextFind;
        boolean failNextSave;
        boolean conflictOnNextSave;

        @Override
        public Optional<StoredResponse> find(IdempotencyKey key) {
            if (hideOnNextFind) {
                // La primera lectura no ve la clave de la otra solicitud, que aún no había confirmado.
                hideOnNextFind = false;
                return Optional.empty();
            }
            return Optional.ofNullable(responses.get(key));
        }

        @Override
        public void save(IdempotencyKey key, StoredResponse response) {
            if (failNextSave) {
                failNextSave = false;
                throw new IllegalStateException("Falla simulada al guardar la clave");
            }
            if (conflictOnNextSave) {
                // Otra solicitud con la misma clave y la misma huella confirmó primero (fuera de esta transacción).
                conflictOnNextSave = false;
                DeckDetails winner = new DeckDetails(99, "Fuego", NOW, List.of());
                committedByOthers.put(key, new StoredResponse(response.requestHash(), response.status(),
                        ObjectSerializer.register(winner)));
                throw new IdempotencyConflictException("Clave ya guardada", null);
            }
            if (responses.putIfAbsent(key, response) != null) {
                throw new IdempotencyConflictException("Clave ya guardada", null);
            }
        }

        private final Map<IdempotencyKey, StoredResponse> committedByOthers = new HashMap<>();

        @Override
        public Runnable snapshot() {
            Map<IdempotencyKey, StoredResponse> before = new HashMap<>(responses);
            return () -> {
                responses.clear();
                responses.putAll(before);
                responses.putAll(committedByOthers);
            };
        }
    }

    private static final class ObjectSerializer implements ResponseSerializer {
        private static final Map<String, Object> OBJECTS = new HashMap<>();

        static String register(Object value) {
            String id = "objeto-" + UUID.randomUUID();
            OBJECTS.put(id, value);
            return id;
        }

        @Override
        public String serialize(Object value) {
            return register(value);
        }

        @Override
        public <T> T deserialize(String text, Class<T> type) {
            return type.cast(OBJECTS.get(text));
        }
    }
}
