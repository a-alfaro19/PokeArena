package cr.ac.tec.pokearena.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import cr.ac.tec.pokearena.application.port.InitialCollectionStatus;
import cr.ac.tec.pokearena.domain.rule.InitialCollectionRules;
import cr.ac.tec.pokearena.infrastructure.persistence.JdbcCardRepository;
import cr.ac.tec.pokearena.support.PostgresIntegrationTestBase;
import cr.ac.tec.pokearena.support.TestPlayers;

/**
 * Propósito: prueba de rendimiento de la colección inicial con 10.000 cartas (secciones 8.2, 13 y 14).
 * Responsabilidades: cargar {@code scripts/seed-10000-cartas.sql} (generate_series, 50 % Comunes), medir
 * la entrega completa, y comprobar con {@code EXPLAIN ANALYZE} que la selección al azar conserva solo las
 * cartas pedidas (Top-N) en lugar de ordenar o devolver el catálogo. Imprime las medidas para documentarlas.
 * Referencia: sección 8.2 ("el selector debe funcionar con 10,000 cartas sin cargarlas todas en memoria")
 * y sección 13 (fila "Rendimiento": menos de 300 ms).
 *
 * <p>Decisión sobre el plan: elegir al azar entre TODAS las Comunes obliga a mirar cada candidata (unas
 * 5.000 aquí), así que leer la tabla es esperable. Lo que importa es que la base solo guarde en memoria
 * las 15 menores ("top-N heapsort"), nunca ordene en disco ni envíe más de 15 filas a la aplicación.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Recibir colección inicial con 10.000 cartas (rendimiento)")
class InitialCollectionPerformanceIT extends PostgresIntegrationTestBase {

    private static final Path SEED_SCRIPT = Path.of("scripts", "seed-10000-cartas.sql");
    /** Objetivo de la sección 13: menos de 300 ms. */
    private static final long MAX_MILLIS = 300;
    private static final int WARM_UP_DELIVERIES = 3;
    private static final int MEASURED_DELIVERIES = 20;
    private static final double PERCENTILE = 0.95;
    private static final long NANOS_PER_MILLI = 1_000_000;
    private static final long SEED = 12345L;
    private static final Pattern EXECUTION_TIME = Pattern.compile("Execution Time: ([0-9.]+) ms");

    @Autowired
    private DeliverInitialCollectionUseCase useCase;

    @Autowired
    private PlayerAccountService accounts;

    @Autowired
    private InitialCollectionRules rules;

    @Autowired
    private JdbcTemplate jdbc;

    private final List<UUID> createdPlayers = new ArrayList<>();

    @BeforeAll
    void seed() throws IOException {
        clearCatalog();
        jdbc.execute(Files.readString(SEED_SCRIPT));
    }

    @AfterAll
    void clean() {
        TestPlayers.delete(jdbc, createdPlayers);
        clearCatalog();
    }

    @Test
    @DisplayName("13 y 8.2: con 10.000 cartas sembradas con generate_series, la entrega completa responde en menos de 300 ms")
    void deliveryIsFast() {
        for (int i = 0; i < WARM_UP_DELIVERIES; i++) {
            assertThat(useCase.execute(newPlayer())).isEqualTo(InitialCollectionStatus.DELIVERED);
        }
        List<Long> millis = new ArrayList<>();
        for (int i = 0; i < MEASURED_DELIVERIES; i++) {
            UUID player = newPlayer();
            long start = System.nanoTime();
            InitialCollectionStatus status = useCase.execute(player);
            millis.add((System.nanoTime() - start) / NANOS_PER_MILLI);
            assertThat(status).isEqualTo(InitialCollectionStatus.DELIVERED);
        }
        long p95 = percentile(millis);
        System.out.printf("Colección inicial, 10.000 cartas: p95 = %d ms (medidas: %s)%n", p95, millis);
        assertThat(p95).isLessThan(MAX_MILLIS);
    }

    @Test
    @DisplayName("8.2: el plan de la selección conserva solo las 15 cartas elegidas (top-N heapsort), sin ordenar en disco ni devolver el catálogo")
    void selectionPlanIsTopN() {
        List<String> lines = jdbc.queryForList("EXPLAIN (ANALYZE, COSTS OFF) " + JdbcCardRepository.PICK_COLLECTIBLE_SQL,
                String.class, InitialCollectionRules.CARD_RARITY, Long.toString(SEED), rules.cardCount());
        String plan = String.join("\n", lines);
        System.out.println("Plan de la selección de la colección inicial:\n" + plan);

        assertThat(plan).contains("Limit (actual").contains("top-N heapsort").doesNotContain("external merge");
        // La primera línea (Limit) devuelve exactamente las cartas pedidas.
        assertThat(lines.getFirst()).contains("rows=" + rules.cardCount());
        Matcher time = EXECUTION_TIME.matcher(plan);
        assertThat(time.find()).isTrue();
        assertThat(Double.parseDouble(time.group(1))).isLessThan(MAX_MILLIS);
    }

    private UUID newPlayer() {
        UUID id = TestPlayers.create(accounts).id();
        createdPlayers.add(id);
        return id;
    }

    private static long percentile(List<Long> values) {
        List<Long> sorted = values.stream().sorted().toList();
        return sorted.get((int) Math.ceil(sorted.size() * PERCENTILE) - 1);
    }

    private void clearCatalog() {
        jdbc.update("DELETE FROM player_card");
        jdbc.update("DELETE FROM card");
        jdbc.update("UPDATE card_type_counter SET last_number = 0");
    }
}
