package cr.ac.tec.pokearena.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import cr.ac.tec.pokearena.application.port.Clock;
import cr.ac.tec.pokearena.application.port.InitialCollectionStatus;
import cr.ac.tec.pokearena.application.port.RandomSource;
import cr.ac.tec.pokearena.application.port.UnitOfWork;
import cr.ac.tec.pokearena.domain.model.InitialCollectionState;
import cr.ac.tec.pokearena.domain.model.NewPlayer;
import cr.ac.tec.pokearena.domain.model.Player;
import cr.ac.tec.pokearena.domain.model.PlayerAccount;
import cr.ac.tec.pokearena.domain.model.ProviderIdentity;
import cr.ac.tec.pokearena.domain.model.Role;
import cr.ac.tec.pokearena.domain.repository.CollectibleCardRepository;
import cr.ac.tec.pokearena.domain.repository.CubeRepository;
import cr.ac.tec.pokearena.domain.repository.PlayerCardRepository;
import cr.ac.tec.pokearena.domain.repository.PlayerRepository;
import cr.ac.tec.pokearena.domain.rule.InitialCollectionRules;
import cr.ac.tec.pokearena.domain.rule.NameNormalizer;
import cr.ac.tec.pokearena.support.PostgresIntegrationTestBase;
import cr.ac.tec.pokearena.support.TestPlayers;

/**
 * Propósito: pruebas de punta a punta de la historia 3.1.1 "Recibir colección inicial" (criterios C.5).
 * Responsabilidades: verificar el caso de uso con PostgreSQL real: las 15 cartas Comunes distintas y los
 * 1.000 cubos, el primer movimiento y el invariante del saldo, la falta de Comunes, que una falla a mitad
 * no deja nada, que se entrega exactamente una vez (también con 8 entregas simultáneas) y que la
 * selección depende solo de la semilla del servidor.
 * Referencia: sección 8.2, Anexo A.5, Anexo C.5 y sección 9 (invariantes de cubos y colección).
 *
 * <p>Cada prueba parte de un catálogo conocido: borra las cartas (como las demás pruebas del catálogo) y
 * siembra 20 Comunes disponibles y activas, más cartas que NO deben entregarse (Comunes no disponibles,
 * Comunes desactivadas y cartas de otras rarezas). Al terminar borra sus jugadores y sus cartas.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@DisplayName("Historia 3.1.1: recibir colección inicial (PostgreSQL real)")
class DeliverInitialCollectionIT extends PostgresIntegrationTestBase {

    private static final int AVAILABLE_COMMONS = 20;
    private static final int EXCLUDED_PER_KIND = 3;
    private static final int SIMULTANEOUS_DELIVERIES = 8;
    private static final long SEED = 20261108L;
    private static final long OTHER_SEED = 777L;
    private static final String[] TYPES = { "FUE", "AGU", "PLA", "ELE", "PSI", "LUC" };

    @Autowired
    private DeliverInitialCollectionUseCase useCase;

    @Autowired
    private PlayerAccountService accounts;

    @Autowired
    private InitialCollectionRules rules;

    @Autowired
    private UnitOfWork unitOfWork;

    @Autowired
    private PlayerRepository players;

    @Autowired
    private CollectibleCardRepository collectible;

    @Autowired
    private PlayerCardRepository playerCards;

    @Autowired
    private CubeRepository cubes;

    @Autowired
    private Clock clock;

    @Autowired
    private JdbcTemplate jdbc;

    private final List<UUID> createdPlayers = new ArrayList<>();
    private int nextCardNumber;

    @BeforeEach
    void knownCatalog() {
        resetCatalog();
        commons(AVAILABLE_COMMONS);
        for (int i = 0; i < EXCLUDED_PER_KIND; i++) {
            card("COMMON", false, true);     // Común no disponible (miniatura pendiente)
            card("COMMON", true, false);     // Común desactivada
            card("UNCOMMON", true, true);    // otra rareza
            card("LEGENDARY", true, true);
        }
    }

    @AfterEach
    void cleanUp() {
        TestPlayers.delete(jdbc, createdPlayers);
        resetCatalog();
    }

    @Nested
    @DisplayName("Éxito")
    class Success {

        @Test
        @DisplayName("C.5 éxito: en el primer ingreso el jugador recibe 15 cartas distintas de rareza Común, elegidas entre las disponibles del catálogo")
        void fifteenDistinctCommons() {
            UUID player = newPlayer();

            assertThat(useCase.execute(player)).isEqualTo(InitialCollectionStatus.DELIVERED);

            List<Map<String, Object>> rows = jdbc.queryForList("""
                    SELECT pc.card_code, pc.copies, c.rarity_code, c.available, c.active
                    FROM player_card pc JOIN card c ON c.code = pc.card_code
                    WHERE pc.player_id = ?
                    """, player);
            assertThat(rows).hasSize(rules.cardCount()).hasSize(15);
            assertThat(rows).extracting(row -> row.get("card_code")).doesNotHaveDuplicates();
            assertThat(rows).allSatisfy(row -> {
                assertThat(row.get("rarity_code")).isEqualTo("COMMON");
                assertThat(row.get("available")).isEqualTo(true);
                assertThat(row.get("active")).isEqualTo(true);
                assertThat(((Number) row.get("copies")).intValue()).isEqualTo(1);
            });
            assertThat(state(player)).isEqualTo("DELIVERED");
        }

        @Test
        @DisplayName("C.5 éxito: en el primer ingreso el jugador recibe 1,000 cubos")
        void thousandCubes() {
            UUID player = newPlayer();

            useCase.execute(player);

            assertThat(balance(player)).isEqualTo(rules.cubes()).isEqualTo(1000);
        }

        @Test
        @DisplayName("C.5 éxito: con la colección inicial se pueden armar 12 cartas distintas, un mazo válido")
        void enoughForADeck() {
            UUID player = newPlayer();

            useCase.execute(player);

            Integer distinct = jdbc.queryForObject(
                    "SELECT count(DISTINCT card_code) FROM player_card WHERE player_id = ?", Integer.class, player);
            assertThat(distinct).isGreaterThanOrEqualTo(InitialCollectionRules.DECK_SIZE);
        }
    }

    @Nested
    @DisplayName("Errores")
    class Errors {

        @Test
        @DisplayName("C.5 error: con menos de 15 Comunes disponibles la cuenta queda sin colección ni cubos, PENDING_NO_CARDS, y se completa en un ingreso posterior")
        void notEnoughCommons() {
            resetCatalog();
            commons(rules.cardCount() - 1);
            // Las no disponibles y las desactivadas no cuentan, aunque sean Comunes.
            card("COMMON", false, true);
            card("COMMON", true, false);
            UUID player = newPlayer();

            assertThat(useCase.execute(player)).isEqualTo(InitialCollectionStatus.PENDING_NO_CARDS);

            assertNothingDelivered(player);

            // Cuando hay cartas suficientes, el siguiente ingreso completa la entrega.
            commons(1);
            assertThat(useCase.execute(player)).isEqualTo(InitialCollectionStatus.DELIVERED);
            assertThat(cardCount(player)).isEqualTo(15);
        }

        @Test
        @DisplayName("C.5 error: si la entrega falla a mitad no queda nada (ni cartas, ni cubos, ni marca) y se completa en el siguiente ingreso")
        void failureLeavesNothing() {
            UUID player = newPlayer();
            // Falla en el último paso (marcar entregada), cuando las cartas y los cubos ya se escribieron.
            DeliverInitialCollectionUseCase failing = new DeliverInitialCollectionUseCase(unitOfWork,
                    new FailingMark(players), collectible, playerCards, cubes, () -> SEED, clock, rules);

            assertThatThrownBy(() -> failing.execute(player)).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("falla simulada");

            assertNothingDelivered(player);

            assertThat(useCase.execute(player)).isEqualTo(InitialCollectionStatus.DELIVERED);
            assertThat(cardCount(player)).isEqualTo(15);
            assertThat(balance(player)).isEqualTo(1000);
        }

        @Test
        @DisplayName("C.5 error: ningún dato del cliente influye: la selección depende solo de la semilla del servidor (misma semilla, misma selección; otra semilla, otra)")
        void selectionDependsOnlyOnServerSeed() {
            UUID first = newPlayer();
            UUID second = newPlayer();
            UUID third = newPlayer();

            withSeed(SEED).execute(first);
            withSeed(SEED).execute(second);
            withSeed(OTHER_SEED).execute(third);

            assertThat(cardsOf(second)).isEqualTo(cardsOf(first));
            assertThat(cardsOf(third)).isNotEqualTo(cardsOf(first));
            // La entrada del caso de uso es solo el id del jugador: no hay otro dato que el cliente pueda enviar.
            assertThat(cardsOf(first)).isEqualTo(
                    collectible.pickRandomCollectible("COMMON", SEED, rules.cardCount()).stream().sorted().toList());
        }

        @Test
        @DisplayName("Un jugador inexistente es un error de quien invoca y no escribe nada")
        void unknownPlayer() {
            assertThatThrownBy(() -> useCase.execute(UUID.randomUUID())).isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("Alternos")
    class Alternatives {

        @Test
        @DisplayName("C.5 alterno: los ingresos posteriores no vuelven a entregar la colección ni los cubos (ALREADY_DELIVERED)")
        void laterLoginsDoNotDeliverAgain() {
            UUID player = newPlayer();
            useCase.execute(player);
            List<String> before = cardsOf(player);

            assertThat(useCase.execute(player)).isEqualTo(InitialCollectionStatus.ALREADY_DELIVERED);
            assertThat(useCase.execute(player)).isEqualTo(InitialCollectionStatus.ALREADY_DELIVERED);

            assertThat(cardsOf(player)).isEqualTo(before);
            assertThat(balance(player)).isEqualTo(1000);
            assertThat(movementCount(player)).isEqualTo(1);
        }

        @Test
        @DisplayName("C.5 alterno: dos (aquí 8) primeros ingresos simultáneos entregan la colección y los cubos exactamente una vez")
        void simultaneousDeliveries() throws Exception {
            UUID player = newPlayer();
            CountDownLatch start = new CountDownLatch(1);
            ExecutorService threads = Executors.newFixedThreadPool(SIMULTANEOUS_DELIVERIES);
            try {
                List<Future<InitialCollectionStatus>> results = new ArrayList<>();
                for (int i = 0; i < SIMULTANEOUS_DELIVERIES; i++) {
                    Callable<InitialCollectionStatus> attempt = () -> {
                        start.await();
                        return useCase.execute(player);
                    };
                    results.add(threads.submit(attempt));
                }
                start.countDown();

                List<InitialCollectionStatus> statuses = new ArrayList<>();
                for (Future<InitialCollectionStatus> result : results) {
                    statuses.add(result.get());
                }

                assertThat(statuses).filteredOn(InitialCollectionStatus.DELIVERED::equals).hasSize(1);
                assertThat(statuses).filteredOn(InitialCollectionStatus.ALREADY_DELIVERED::equals)
                        .hasSize(SIMULTANEOUS_DELIVERIES - 1);
                assertThat(cardCount(player)).isEqualTo(15);
                assertThat(balance(player)).isEqualTo(1000);
                assertThat(movementCount(player)).isEqualTo(1);
            } finally {
                threads.shutdownNow();
            }
        }

        @Test
        @DisplayName("C.5 alterno: los 1,000 cubos iniciales quedan registrados como el primer movimiento del historial")
        void initialGrantIsFirstMovement() {
            UUID player = newPlayer();

            useCase.execute(player);

            Map<String, Object> first = jdbc.queryForMap("""
                    SELECT amount, reason FROM cube_movement WHERE player_id = ? ORDER BY id LIMIT 1
                    """, player);
            assertThat(first.get("reason")).isEqualTo("INITIAL_GRANT");
            assertThat(((Number) first.get("amount")).longValue()).isEqualTo(1000);
            assertThat(movementCount(player)).isEqualTo(1);
        }

        @Test
        @DisplayName("9 invariante: el saldo de cubos coincide con la suma de los movimientos y nunca es negativo")
        void balanceMatchesMovements() {
            UUID player = newPlayer();

            useCase.execute(player);

            Long sum = jdbc.queryForObject("SELECT coalesce(sum(amount), 0) FROM cube_movement WHERE player_id = ?",
                    Long.class, player);
            assertThat(balance(player)).isEqualTo(sum).isNotNegative();
        }
    }

    // =========================================================================================
    // Ayudantes.
    // =========================================================================================

    private DeliverInitialCollectionUseCase withSeed(long seed) {
        RandomSource fixed = () -> seed;
        return new DeliverInitialCollectionUseCase(unitOfWork, players, collectible, playerCards, cubes, fixed, clock,
                rules);
    }

    private UUID newPlayer() {
        UUID id = TestPlayers.create(accounts).id();
        createdPlayers.add(id);
        return id;
    }

    private void assertNothingDelivered(UUID player) {
        assertThat(cardCount(player)).isZero();
        assertThat(balance(player)).isZero();
        assertThat(movementCount(player)).isZero();
        assertThat(state(player)).isEqualTo("PENDING");
    }

    private void commons(int count) {
        for (int i = 0; i < count; i++) {
            card("COMMON", true, true);
        }
    }

    private void card(String rarity, boolean available, boolean active) {
        nextCardNumber++;
        String type = TYPES[nextCardNumber % TYPES.length];
        String code = "PKA-%s-%03d".formatted(type, nextCardNumber);
        String name = "Colección inicial " + nextCardNumber;
        jdbc.update("""
                INSERT INTO card (code, name, name_normalized, cost, power, type_code, rarity_code, ability_code,
                                  image_large_url, image_small_url, image_status, available, active, created_at)
                VALUES (?, ?, ?, 1, 1, ?, ?, 'HAB-000', ?, ?, ?, ?, ?, now())
                """, code, name, NameNormalizer.normalize(name), type, rarity, "/images/cards/" + code + "/large.png",
                available ? "/images/cards/" + code + "/small.png" : null, available ? "READY" : "PENDING_RETRY",
                available, active);
    }

    private void resetCatalog() {
        jdbc.update("DELETE FROM card");
        jdbc.update("UPDATE card_type_counter SET last_number = 0");
    }

    private List<String> cardsOf(UUID player) {
        return jdbc.queryForList("SELECT card_code FROM player_card WHERE player_id = ? ORDER BY card_code",
                String.class, player);
    }

    private int cardCount(UUID player) {
        return jdbc.queryForObject("SELECT count(*) FROM player_card WHERE player_id = ?", Integer.class, player);
    }

    private int movementCount(UUID player) {
        return jdbc.queryForObject("SELECT count(*) FROM cube_movement WHERE player_id = ?", Integer.class, player);
    }

    private long balance(UUID player) {
        return jdbc.queryForObject("SELECT cube_balance FROM player WHERE id = ?", Long.class, player);
    }

    private String state(UUID player) {
        return jdbc.queryForObject("SELECT initial_collection_status FROM player WHERE id = ?", String.class, player);
    }

    /** Repositorio real de jugadores que falla al marcar la entrega: el último paso de la transacción. */
    private static final class FailingMark implements PlayerRepository {

        private final PlayerRepository real;

        FailingMark(PlayerRepository real) {
            this.real = real;
        }

        @Override
        public Optional<Player> findByIdentity(ProviderIdentity identity) {
            return real.findByIdentity(identity);
        }

        @Override
        public PlayerAccount findOrCreate(ProviderIdentity identity, NewPlayer candidate) {
            return real.findOrCreate(identity, candidate);
        }

        @Override
        public void updateRole(UUID playerId, Role role) {
            real.updateRole(playerId, role);
        }

        @Override
        public Optional<InitialCollectionState> lockInitialCollectionState(UUID playerId) {
            return real.lockInitialCollectionState(playerId);
        }

        @Override
        public void markInitialCollectionDelivered(UUID playerId) {
            throw new IllegalStateException("falla simulada al marcar la entrega");
        }
    }
}
