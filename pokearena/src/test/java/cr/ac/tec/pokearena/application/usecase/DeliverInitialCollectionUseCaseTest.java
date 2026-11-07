package cr.ac.tec.pokearena.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import cr.ac.tec.pokearena.application.port.InitialCollectionStatus;
import cr.ac.tec.pokearena.application.port.UnitOfWork;
import cr.ac.tec.pokearena.domain.model.CubeMovement;
import cr.ac.tec.pokearena.domain.model.CubeMovementReason;
import cr.ac.tec.pokearena.domain.model.NewPlayer;
import cr.ac.tec.pokearena.domain.model.ProviderIdentity;
import cr.ac.tec.pokearena.domain.model.Role;
import cr.ac.tec.pokearena.domain.repository.CollectibleCardRepository;
import cr.ac.tec.pokearena.domain.rule.InitialCollectionRules;
import cr.ac.tec.pokearena.support.InMemoryPlayerRepository;

/**
 * Propósito: pruebas unitarias de {@link DeliverInitialCollectionUseCase} con los puertos en memoria
 * (historia 3.1.1).
 * Responsabilidades: comprobar el orden del diagrama A.5 (bloqueo y estado, conteo, semilla y selección,
 * escrituras), que todo ocurre en UNA transacción, que la semilla viene del puerto RandomSource, que las
 * cantidades vienen de la configuración y que una falla se propaga. Sin Spring ni base de datos: la
 * reversión real de una falla y la concurrencia se prueban con PostgreSQL en DeliverInitialCollectionIT.
 * Referencia: sección 8.2 y Anexo A.5.
 */
@DisplayName("Caso de uso Recibir colección inicial (puertos en memoria)")
class DeliverInitialCollectionUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-11-08T12:00:00Z");
    private static final long SEED = 42L;
    private static final InitialCollectionRules RULES = new InitialCollectionRules(15, 1000);

    private final InMemoryPlayerRepository players = new InMemoryPlayerRepository();
    private final FakeCatalog catalog = new FakeCatalog();
    private final Map<UUID, List<String>> playerCards = new LinkedHashMap<>();
    private final List<CubeMovement> movements = new ArrayList<>();
    private final CountingUnitOfWork unitOfWork = new CountingUnitOfWork();
    private int seedsRequested;

    private final UUID player = players.findOrCreate(new ProviderIdentity("google", "sub-1"),
            new NewPlayer(null, null, Role.PLAYER, NOW)).player().id();

    private DeliverInitialCollectionUseCase useCase(InitialCollectionRules rules) {
        return new DeliverInitialCollectionUseCase(unitOfWork, players, catalog,
                (playerId, codes) -> playerCards.computeIfAbsent(playerId, key -> new ArrayList<>()).addAll(codes),
                movements::add, () -> {
                    seedsRequested++;
                    return SEED;
                }, () -> NOW, rules);
    }

    private InitialCollectionStatus deliver() {
        return useCase(RULES).execute(player);
    }

    @Nested
    @DisplayName("Éxito")
    class Success {

        @Test
        @DisplayName("Con Comunes suficientes entrega las cartas, los cubos y la marca, en una sola transacción")
        void deliversEverythingInOneTransaction() {
            catalog.commons(20);

            assertThat(deliver()).isEqualTo(InitialCollectionStatus.DELIVERED);

            assertThat(playerCards.get(player)).hasSize(15).doesNotHaveDuplicates();
            assertThat(movements).singleElement().satisfies(movement -> {
                assertThat(movement.amount()).isEqualTo(1000);
                assertThat(movement.reason()).isEqualTo(CubeMovementReason.INITIAL_GRANT);
                assertThat(movement.createdAt()).isEqualTo(NOW);
            });
            assertThat(players.delivered()).containsExactly(player);
            assertThat(unitOfWork.transactions).isEqualTo(1);
        }

        @Test
        @DisplayName("La selección usa la semilla del puerto RandomSource y la rareza Común")
        void selectionUsesSeedFromPort() {
            catalog.commons(20);

            deliver();

            assertThat(seedsRequested).isEqualTo(1);
            assertThat(catalog.seedsUsed).containsExactly(SEED);
            assertThat(catalog.raritiesUsed).containsOnly("COMMON");
            assertThat(playerCards.get(player)).isEqualTo(catalog.pickRandomCollectible("COMMON", SEED, 15));
        }

        @Test
        @DisplayName("Las cantidades vienen de la configuración (app.collection.*), no del código")
        void amountsComeFromConfiguration() {
            catalog.commons(20);

            useCase(new InitialCollectionRules(12, 250)).execute(player);

            assertThat(playerCards.get(player)).hasSize(12);
            assertThat(movements.getFirst().amount()).isEqualTo(250);
        }
    }

    @Nested
    @DisplayName("Orden del diagrama A.5")
    class Order {

        @Test
        @DisplayName("Paso 2: con la colección ya entregada termina sin contar, sin pedir semilla y sin escribir")
        void alreadyDeliveredStopsEarly() {
            catalog.commons(20);
            players.markInitialCollectionDelivered(player);

            assertThat(deliver()).isEqualTo(InitialCollectionStatus.ALREADY_DELIVERED);

            assertThat(catalog.counts).isZero();
            assertThat(seedsRequested).isZero();
            assertThat(playerCards).isEmpty();
            assertThat(movements).isEmpty();
        }

        @Test
        @DisplayName("Paso 3: con menos Comunes que las requeridas devuelve PENDING_NO_CARDS sin pedir semilla ni escribir")
        void notEnoughCommons() {
            catalog.commons(14);

            assertThat(deliver()).isEqualTo(InitialCollectionStatus.PENDING_NO_CARDS);

            assertThat(seedsRequested).isZero();
            assertThat(playerCards).isEmpty();
            assertThat(movements).isEmpty();
            assertThat(players.delivered()).isEmpty();
        }

        @Test
        @DisplayName("Si la selección trae menos cartas que el conteo (otra transacción las retiró), PENDING_NO_CARDS sin escribir")
        void selectionShorterThanCount() {
            catalog.commons(20);
            catalog.pickLimit = 10;

            assertThat(deliver()).isEqualTo(InitialCollectionStatus.PENDING_NO_CARDS);

            assertThat(playerCards).isEmpty();
            assertThat(movements).isEmpty();
        }
    }

    @Nested
    @DisplayName("Errores")
    class Errors {

        @Test
        @DisplayName("Una falla a mitad de la entrega se propaga (la transacción la revierte) y no marca la entrega")
        void failurePropagates() {
            catalog.commons(20);
            DeliverInitialCollectionUseCase failing = new DeliverInitialCollectionUseCase(unitOfWork, players, catalog,
                    (playerId, codes) -> playerCards.put(playerId, List.copyOf(codes)),
                    movement -> {
                        throw new IllegalStateException("falla simulada al mover los cubos");
                    }, () -> SEED, () -> NOW, RULES);

            assertThatThrownBy(() -> failing.execute(player)).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("falla simulada");
            assertThat(players.delivered()).isEmpty();
        }

        @Test
        @DisplayName("Un jugador inexistente es un error de quien invoca: IllegalStateException")
        void unknownPlayer() {
            assertThatThrownBy(() -> useCase(RULES).execute(UUID.randomUUID()))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("No existe el jugador");
        }

        @Test
        @DisplayName("C.5: una configuración con menos de 12 cartas detiene el arranque (no alcanzaría para un mazo)")
        void rulesRequireAFullDeck() {
            assertThatThrownBy(() -> new InitialCollectionRules(InitialCollectionRules.DECK_SIZE - 1, 1000))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("mazo");
            assertThatThrownBy(() -> new InitialCollectionRules(15, 0))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    /** Catálogo en memoria: Comunes con códigos fijos y una selección determinista según la semilla. */
    private static final class FakeCatalog implements CollectibleCardRepository {

        private final List<String> commons = new ArrayList<>();
        private final List<Long> seedsUsed = new ArrayList<>();
        private final List<String> raritiesUsed = new ArrayList<>();
        private int counts;
        private int pickLimit = Integer.MAX_VALUE;

        void commons(int count) {
            IntStream.rangeClosed(1, count).forEach(i -> commons.add("PKA-FUE-%03d".formatted(i)));
        }

        @Override
        public int countCollectible(String rarityCode) {
            counts++;
            raritiesUsed.add(rarityCode);
            return commons.size();
        }

        @Override
        public List<String> pickRandomCollectible(String rarityCode, long seed, int count) {
            seedsUsed.add(seed);
            raritiesUsed.add(rarityCode);
            return commons.stream()
                    .sorted(Comparator.comparingInt(code -> (code + seed).hashCode()))
                    .limit(Math.min(count, pickLimit))
                    .toList();
        }
    }

    /** Ejecuta el trabajo directamente y cuenta las transacciones abiertas. */
    private static final class CountingUnitOfWork implements UnitOfWork {

        private int transactions;

        @Override
        public <T> T execute(Supplier<T> work) {
            transactions++;
            return work.get();
        }
    }
}
