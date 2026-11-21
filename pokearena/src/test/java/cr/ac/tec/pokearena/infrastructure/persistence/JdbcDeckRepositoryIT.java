package cr.ac.tec.pokearena.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import cr.ac.tec.pokearena.application.port.UnitOfWork;
import cr.ac.tec.pokearena.application.usecase.PlayerAccountService;
import cr.ac.tec.pokearena.domain.error.DeckException;
import cr.ac.tec.pokearena.domain.error.ErrorType;
import cr.ac.tec.pokearena.domain.model.Card;
import cr.ac.tec.pokearena.domain.model.Deck;
import cr.ac.tec.pokearena.domain.model.NewDeck;
import cr.ac.tec.pokearena.domain.model.StoredDeck;
import cr.ac.tec.pokearena.domain.repository.DeckRepository;
import cr.ac.tec.pokearena.domain.rule.DeckRules;
import cr.ac.tec.pokearena.support.PostgresIntegrationTestBase;
import cr.ac.tec.pokearena.support.TestCards;
import cr.ac.tec.pokearena.support.TestPlayers;

/**
 * Propósito: verificar {@link JdbcDeckRepository} sobre PostgreSQL real (historia 3.1.3, tablas de V3).
 * Responsabilidades: guardar y leer un deck con sus cartas; nombre único por jugador (también con dos
 * transacciones simultáneas); que un jugador no lee decks ajenos; la paginación por cursor; y que el deck y
 * sus cartas se guardan o se revierten juntos dentro del {@code UnitOfWork} de quien llama.
 * Referencia: sección 7.1 y sección 6.1 del Sprint 3; Anexo A.7 (mensaje 23) y A.8; criterios C.7-E1, E2,
 * R4 y A2.
 *
 * <p>Cada prueba siembra un catálogo conocido ({@link TestCards}, que vacía {@code card}) y borra sus
 * jugadores, con sus decks, al terminar ({@link TestPlayers#delete}).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@DisplayName("Repositorio de decks (JDBC)")
class JdbcDeckRepositoryIT extends PostgresIntegrationTestBase {

    private static final Instant NOW = Instant.parse("2026-11-19T12:00:00Z");

    /** Cartas del catálogo de prueba: más que un deck, para tener cartas fuera de él. */
    private static final int CATALOG_SIZE = DeckRules.DECK_SIZE + 3;

    /** Cuánto se espera, como máximo, a que la segunda transacción quede bloqueada por la primera. */
    private static final Duration BLOCK_TIMEOUT = Duration.ofSeconds(10);

    @Autowired
    private DeckRepository decks;

    @Autowired
    private UnitOfWork unitOfWork;

    @Autowired
    private PlayerAccountService accounts;

    @Autowired
    private JdbcTemplate jdbc;

    private final List<UUID> players = new ArrayList<>();
    private List<String> catalog;

    @BeforeEach
    void seedCatalog() {
        TestCards cards = new TestCards(jdbc);
        cards.reset();
        catalog = new ArrayList<>();
        for (int i = 0; i < CATALOG_SIZE; i++) {
            catalog.add(cards.card("COMMON", true, true));
        }
    }

    @AfterEach
    void cleanUp() {
        try {
            // Primero lo que apunta a las cartas: decks, colecciones y jugadores (TestPlayers.delete).
            TestPlayers.delete(jdbc, players);
        } finally {
            // Después el catálogo sembrado, aunque falle lo anterior: las pruebas comparten la base, y una que
            // no siembra cartas (por ejemplo FlywayMigrationIT) la espera sin las de otras pruebas.
            new TestCards(jdbc).reset();
        }
    }

    private UUID newPlayer() {
        UUID player = TestPlayers.create(accounts).id();
        players.add(player);
        return player;
    }

    private List<String> deckCodes() {
        return catalog.subList(0, DeckRules.DECK_SIZE);
    }

    private NewDeck newDeck(UUID player, String name) {
        return DeckRules.validateShape(player, name, deckCodes(), NOW);
    }

    private StoredDeck save(NewDeck deck) {
        return unitOfWork.execute(() -> decks.save(deck));
    }

    private int count(String sql, Object... arguments) {
        return jdbc.queryForObject(sql, Integer.class, arguments);
    }

    @Nested
    @DisplayName("Guardar y leer")
    class SaveAndRead {

        @Test
        @DisplayName("C.7-E1: el deck se guarda con sus 12 cartas y se lee con la ficha de cada una, en una consulta")
        void savesAndReads() {
            UUID player = newPlayer();
            StoredDeck stored = save(newDeck(player, "  Fuego rápido "));

            assertThat(stored.id()).isPositive();
            assertThat(stored.size()).isEqualTo(DeckRules.DECK_SIZE);

            Deck deck = decks.findByIdAndPlayer(stored.id(), player).orElseThrow();
            assertThat(deck.stored()).isEqualTo(stored);
            assertThat(deck.stored().name()).isEqualTo("Fuego rápido");
            assertThat(deck.cardCodes()).isEqualTo(deckCodes().stream().sorted().toList());
            Card first = deck.cards().getFirst();
            assertThat(first.fields().type().name()).isNotBlank();
            assertThat(first.fields().rarity().code()).isEqualTo("COMMON");
            assertThat(first.imageSmallUrl()).isNotBlank();
        }

        @Test
        @DisplayName("save devuelve la hora tal como quedó guardada (microsegundos), igual a la que leen después las consultas")
        void savedTimeMatchesStoredPrecision() {
            UUID player = newPlayer();
            Instant withNanos = Instant.parse("2026-11-19T12:00:00.123456789Z");

            StoredDeck stored = save(DeckRules.validateShape(player, "Fuego", deckCodes(), withNanos));

            assertThat(stored.createdAt()).isEqualTo(Instant.parse("2026-11-19T12:00:00.123457Z"));
            assertThat(decks.findByIdAndPlayer(stored.id(), player).orElseThrow().stored().createdAt())
                    .isEqualTo(stored.createdAt());
        }

        @Test
        @DisplayName("findCardCodes devuelve los 12 códigos del deck, ordenados")
        void cardCodes() {
            StoredDeck stored = save(newDeck(newPlayer(), "Fuego"));

            assertThat(decks.findCardCodes(stored.id())).isEqualTo(deckCodes().stream().sorted().toList());
        }

        @Test
        @DisplayName("C.7-R4: un deck de otro jugador, o inexistente, se lee vacío (mismo resultado para ambos)")
        void foreignOrMissingIsEmpty() {
            UUID owner = newPlayer();
            UUID stranger = newPlayer();
            StoredDeck stored = save(newDeck(owner, "Fuego"));

            assertThat(decks.findByIdAndPlayer(stored.id(), stranger)).isEmpty();
            assertThat(decks.findByIdAndPlayer(stored.id() + 1_000, owner)).isEmpty();
        }

        @Test
        @DisplayName("save exige una transacción: fuera de un UnitOfWork falla con un error claro y no guarda nada")
        void saveRequiresTransaction() {
            UUID player = newPlayer();

            assertThatThrownBy(() -> decks.save(newDeck(player, "Fuego")))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("UnitOfWork");
            assertThat(count("SELECT count(*) FROM deck WHERE player_id = ?", player)).isZero();
        }

        @Test
        @DisplayName("Si la transacción de quien llama se revierte, no quedan ni el deck ni sus cartas")
        void rollbackRemovesDeckAndCards() {
            UUID player = newPlayer();

            assertThatThrownBy(() -> unitOfWork.execute(() -> {
                decks.save(newDeck(player, "Fuego"));
                throw new IllegalStateException("falla simulada después de guardar");
            })).hasMessageContaining("falla simulada");

            assertThat(count("SELECT count(*) FROM deck WHERE player_id = ?", player)).isZero();
            assertThat(count("SELECT count(*) FROM deck_card dc JOIN deck d ON d.id = dc.deck_id "
                    + "WHERE d.player_id = ?", player)).isZero();
        }
    }

    @Nested
    @DisplayName("Nombre único por jugador")
    class UniqueName {

        @Test
        @DisplayName("C.7-A2: el mismo nombre (sin importar mayúsculas ni tildes) para el mismo jugador es DECK_NAME_TAKEN")
        void sameNameSamePlayer() {
            UUID player = newPlayer();
            save(newDeck(player, "Fuego Rápido"));

            assertThatThrownBy(() -> save(newDeck(player, "FUEGO RAPIDO")))
                    .isInstanceOfSatisfying(DeckException.class, error -> {
                        assertThat(error.type()).isEqualTo(ErrorType.DECK_NAME_TAKEN);
                        assertThat(error.field()).contains(DeckException.NAME_FIELD);
                    });
            assertThat(count("SELECT count(*) FROM deck WHERE player_id = ?", player)).isEqualTo(1);
            assertThat(count("SELECT count(*) FROM deck_card dc JOIN deck d ON d.id = dc.deck_id "
                    + "WHERE d.player_id = ?", player)).isEqualTo(DeckRules.DECK_SIZE);
        }

        @Test
        @DisplayName("El mismo nombre en otro jugador sí se guarda")
        void sameNameOtherPlayer() {
            save(newDeck(newPlayer(), "Fuego"));

            assertThat(save(newDeck(newPlayer(), "Fuego")).id()).isPositive();
        }

        @Test
        @DisplayName("C.7-A2: dos transacciones simultáneas con el mismo nombre: la primera confirma y la segunda recibe DECK_NAME_TAKEN")
        void concurrentFirstCommits() throws Exception {
            UUID player = newPlayer();

            Outcome outcome = race(player, false);

            assertThat(outcome.first()).isPresent();
            assertThat(outcome.second()).isInstanceOfSatisfying(DeckException.class,
                    error -> assertThat(error.type()).isEqualTo(ErrorType.DECK_NAME_TAKEN));
            assertThat(count("SELECT count(*) FROM deck WHERE player_id = ?", player)).isEqualTo(1);
        }

        @Test
        @DisplayName("Dos transacciones simultáneas con el mismo nombre: si la primera se revierte, la segunda guarda bien")
        void concurrentFirstRollsBack() throws Exception {
            UUID player = newPlayer();

            Outcome outcome = race(player, true);

            assertThat(outcome.first()).isEmpty();
            assertThat(outcome.second()).isNull();
            assertThat(count("SELECT count(*) FROM deck WHERE player_id = ?", player)).isEqualTo(1);
            assertThat(count("SELECT count(*) FROM deck_card dc JOIN deck d ON d.id = dc.deck_id "
                    + "WHERE d.player_id = ?", player)).isEqualTo(DeckRules.DECK_SIZE);
        }

        /**
         * La primera transacción guarda el deck y se queda abierta; la segunda intenta guardar el mismo nombre
         * y queda bloqueada por PostgreSQL. Recién cuando la base informa que la segunda espera un bloqueo, la
         * primera confirma o se revierte. Sin pausas fijas: latches y la vista {@code pg_stat_activity}.
         *
         * @param rollbackFirst si la primera transacción se revierte en vez de confirmar
         * @return el deck de la primera (vacío si se revirtió) y la excepción de la segunda (null si guardó)
         */
        private Outcome race(UUID player, boolean rollbackFirst) throws Exception {
            CountDownLatch firstSaved = new CountDownLatch(1);
            CountDownLatch finishFirst = new CountDownLatch(1);
            ExecutorService pool = Executors.newFixedThreadPool(2);
            try {
                Future<Optional<StoredDeck>> first = pool.submit(() -> {
                    try {
                        return Optional.of(unitOfWork.execute(() -> {
                            StoredDeck stored = decks.save(newDeck(player, "Fuego"));
                            firstSaved.countDown();
                            awaitLatch(finishFirst);
                            if (rollbackFirst) {
                                throw new RollbackSignal();
                            }
                            return stored;
                        }));
                    } catch (RollbackSignal expected) {
                        return Optional.<StoredDeck>empty();
                    }
                });
                awaitLatch(firstSaved);
                Future<StoredDeck> second = pool.submit(() -> save(newDeck(player, "FUEGO")));

                awaitSecondBlocked();
                finishFirst.countDown();

                Optional<StoredDeck> firstResult = first.get(BLOCK_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
                Throwable secondError = null;
                try {
                    second.get(BLOCK_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
                } catch (ExecutionException failure) {
                    secondError = failure.getCause();
                }
                return new Outcome(firstResult, secondError);
            } finally {
                finishFirst.countDown();
                pool.shutdownNow();
            }
        }

        /** Espera explícita: hasta que una sesión esté esperando un bloqueo en el INSERT de deck. */
        private void awaitSecondBlocked() {
            long deadline = System.nanoTime() + BLOCK_TIMEOUT.toNanos();
            while (count("""
                    SELECT count(*) FROM pg_stat_activity
                    WHERE wait_event_type = 'Lock' AND query LIKE 'INSERT INTO deck %'
                    """) == 0) {
                if (System.nanoTime() > deadline) {
                    throw new AssertionError("La segunda transacción no llegó a esperar a la primera");
                }
                Thread.onSpinWait();
            }
        }

        private static void awaitLatch(CountDownLatch latch) {
            try {
                if (!latch.await(BLOCK_TIMEOUT.toSeconds(), TimeUnit.SECONDS)) {
                    throw new AssertionError("Tiempo agotado esperando a la otra transacción");
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new AssertionError(interrupted);
            }
        }
    }

    @Nested
    @DisplayName("Mis decks paginados por cursor")
    class Pagination {

        @Test
        @DisplayName("C.7-E2: recorre todos los decks del jugador por id, con límite más uno, sin repetir ni saltar")
        void walksAllPages() {
            UUID player = newPlayer();
            UUID other = newPlayer();
            List<Long> expected = new ArrayList<>();
            for (int i = 1; i <= 5; i++) {
                expected.add(save(newDeck(player, "Deck " + i)).id());
                save(newDeck(other, "Ajeno " + i));
            }
            int pageSize = 2;

            List<Long> seen = new ArrayList<>();
            long afterId = 0;
            boolean more = true;
            while (more) {
                List<StoredDeck> rows = decks.findByPlayer(player, afterId, pageSize + 1);
                more = rows.size() > pageSize;
                List<StoredDeck> page = rows.subList(0, Math.min(pageSize, rows.size()));
                page.forEach(deck -> {
                    assertThat(deck.playerId()).isEqualTo(player);
                    assertThat(deck.size()).isEqualTo(DeckRules.DECK_SIZE);
                    assertThat(deck.createdAt()).isEqualTo(NOW);
                });
                page.forEach(deck -> seen.add(deck.id()));
                afterId = page.getLast().id();
            }

            assertThat(seen).containsExactlyElementsOf(expected).doesNotHaveDuplicates();
        }

        @Test
        @DisplayName("La página trae como máximo límite más uno filas, y un jugador sin decks recibe una lista vacía")
        void limitPlusOne() {
            UUID player = newPlayer();
            for (int i = 1; i <= 4; i++) {
                save(newDeck(player, "Deck " + i));
            }

            assertThat(decks.findByPlayer(player, 0, 3)).hasSize(3);
            assertThat(decks.findByPlayer(newPlayer(), 0, 3)).isEmpty();
        }
    }

    /** Resultado de la carrera: el deck de la primera transacción y el error de la segunda. */
    private record Outcome(Optional<StoredDeck> first, Throwable second) {
    }

    /** Señal para que la primera transacción se revierta en la carrera. */
    private static final class RollbackSignal extends RuntimeException {
    }
}
