package cr.ac.tec.pokearena.api.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

import cr.ac.tec.pokearena.application.port.AuthenticatedUser;
import cr.ac.tec.pokearena.application.port.InitialCollectionStatus;
import cr.ac.tec.pokearena.application.port.TokenIssuer;
import cr.ac.tec.pokearena.application.usecase.DeliverInitialCollectionUseCase;
import cr.ac.tec.pokearena.application.usecase.PlayerAccountService;
import cr.ac.tec.pokearena.domain.model.Role;
import cr.ac.tec.pokearena.domain.rule.DeckRules;
import cr.ac.tec.pokearena.infrastructure.persistence.JdbcPlayerCardRepository;
import cr.ac.tec.pokearena.support.DeckHttp;
import cr.ac.tec.pokearena.support.DeckHttp.Result;
import cr.ac.tec.pokearena.support.PostgresIntegrationTestBase;
import cr.ac.tec.pokearena.support.TestPlayers;

/**
 * Propósito: prueba de rendimiento de Crear deck con 10.000 cartas (sección 13 del Sprint 3, fila
 * "Rendimiento").
 * Responsabilidades: cargar {@code scripts/seed-10000-cartas.sql}, dar al jugador su colección inicial REAL
 * (15 Comunes, por {@link DeliverInitialCollectionUseCase}), medir {@code POST /api/v1/decks} por HTTP con 12
 * de esas cartas, y comprobar con {@code EXPLAIN (ANALYZE)} que la consulta de posesión usa la clave de
 * {@code player_card}. Guarda la mediana, el p95, el máximo y el plan en {@code target/deck-performance.txt}.
 * Referencia: sección 13 (menos de 300 ms; "el plan de la comprobación de posesión usa el índice de
 * player_card") y sección 7.1 ("una consulta por la clave (player_id, card_code)").
 *
 * <h2>Cómo se mide (decisión del equipo)</h2>
 * <ul>
 *   <li>3 creaciones de calentamiento (JIT, pool de conexiones, caché de planes) que no cuentan, y 20
 *       medidas. Cada una con nombre y clave distintos: ninguna repite una respuesta guardada ni choca.</li>
 *   <li>Tiempo = {@code System.nanoTime()} alrededor de la solicitud HTTP completa: envío, autorización, caso
 *       de uso, transacción en PostgreSQL y lectura del cuerpo 201.</li>
 *   <li>p95 = el valor 19 de las 20 medidas ordenadas, el mismo criterio de {@code CardSearchPerformanceIT} y
 *       {@code CatalogIT}; se exige menor que 300 ms.</li>
 *   <li>Para que el plan sea representativo, {@code player_card} tiene las colecciones de
 *       {@value #EXTRA_PLAYERS} jugadores más y estadísticas al día ({@code ANALYZE}): con una tabla de 15
 *       filas, PostgreSQL la leería completa porque es más barato, no por falta de índice.</li>
 *   <li>Todo lo que se agrega se borra en {@code @AfterAll}, aunque la prueba falle; el catálogo se vacía
 *       igual que en {@code CardSearchPerformanceIT}.</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Crear Deck con 10.000 cartas (rendimiento)")
class DeckPerformanceIT extends PostgresIntegrationTestBase {

    private static final Path SEED_SCRIPT = Path.of("scripts", "seed-10000-cartas.sql");
    private static final Path REPORT = Path.of("target", "deck-performance.txt");
    /** Objetivo de la sección 13: menos de 300 ms. */
    private static final long MAX_MILLIS = 300;
    private static final int WARM_UP_CREATIONS = 3;
    private static final int MEASURED_CREATIONS = 20;
    private static final double PERCENTILE = 0.95;
    private static final double NANOS_PER_MILLI = 1_000_000.0;
    /** Jugadores extra con colección, para que player_card tenga un tamaño realista. */
    private static final int EXTRA_PLAYERS = 400;
    private static final String EXTRA_PLAYER_MARKER = "jugador-rendimiento-deck";
    private static final Pattern EXECUTION_TIME = Pattern.compile("Execution Time: ([0-9.]+) ms");
    /** Lectura completa de card o de player_card (no de card_type, que es un catálogo de 6 filas). */
    private static final Pattern FULL_SCAN = Pattern.compile("Seq Scan on (card|player_card)\\b");

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlayerAccountService accounts;

    @Autowired
    private DeliverInitialCollectionUseCase initialCollection;

    @Autowired
    private TokenIssuer tokens;

    private final List<String> report = new ArrayList<>();
    private UUID player;
    private List<String> deck;
    private String token;

    @BeforeAll
    void seed() throws IOException {
        clearCatalog();
        jdbc.execute(Files.readString(SEED_SCRIPT));

        // Colección real: la misma entrega del primer ingreso, sobre el catálogo de 10.000 cartas.
        player = TestPlayers.create(accounts).id();
        assertThat(initialCollection.execute(player)).isEqualTo(InitialCollectionStatus.DELIVERED);
        List<String> collection = jdbc.queryForList(
                "SELECT card_code FROM player_card WHERE player_id = ? ORDER BY card_code", String.class, player);
        assertThat(collection.size()).isGreaterThanOrEqualTo(DeckRules.DECK_SIZE);
        deck = collection.subList(0, DeckRules.DECK_SIZE);
        token = tokens.issue(new AuthenticatedUser(player, Role.PLAYER)).value();

        // Colecciones de otros jugadores, del mismo tamaño que la real (Comunes contiguas, desplazadas por
        // jugador para que no todas tengan las mismas cartas) y estadísticas al día.
        int perPlayer = collection.size();
        int commons = jdbc.queryForObject("SELECT count(*) FROM card WHERE rarity_code = 'COMMON'", Integer.class);
        jdbc.update("""
                INSERT INTO player (display_name, role, cube_balance, created_at)
                SELECT ?, 'PLAYER', 0, now() FROM generate_series(1, ?)
                """, EXTRA_PLAYER_MARKER, EXTRA_PLAYERS);
        jdbc.update("""
                INSERT INTO player_card (player_id, card_code, copies)
                SELECT p.id, c.code, 1
                FROM (SELECT id, row_number() OVER (ORDER BY id) AS n FROM player WHERE display_name = ?) p
                CROSS JOIN LATERAL (
                    SELECT code FROM card WHERE rarity_code = 'COMMON'
                    ORDER BY id OFFSET (p.n * ?) % ? LIMIT ?) c
                """, EXTRA_PLAYER_MARKER, perPlayer, commons - perPlayer, perPlayer);
        jdbc.execute("ANALYZE player_card");
    }

    @AfterAll
    void clean() throws IOException {
        try {
            Files.createDirectories(REPORT.getParent());
            Files.writeString(REPORT, String.join(System.lineSeparator(), report));
        } finally {
            try {
                jdbc.update("DELETE FROM player_card WHERE player_id IN (SELECT id FROM player WHERE display_name = ?)",
                        EXTRA_PLAYER_MARKER);
                jdbc.update("DELETE FROM player WHERE display_name = ?", EXTRA_PLAYER_MARKER);
            } finally {
                try {
                    if (player != null) {
                        TestPlayers.delete(jdbc, List.of(player));
                    }
                } finally {
                    clearCatalog();
                }
            }
        }
    }

    @Test
    @Order(1)
    @DisplayName("13: con 10.000 cartas, crear un deck con 12 cartas de una colección real responde en menos de 300 ms (p95)")
    void createDeckIsFast() {
        DeckHttp api = new DeckHttp(port);
        for (int i = 0; i < WARM_UP_CREATIONS; i++) {
            assertThat(api.create(token, "calentamiento-" + i, "Calentamiento " + i, deck).status()).isEqualTo(201);
        }

        List<Double> millis = new ArrayList<>();
        for (int i = 0; i < MEASURED_CREATIONS; i++) {
            long start = System.nanoTime();
            Result result = api.create(token, "medida-" + i, "Medida " + i, deck);
            millis.add((System.nanoTime() - start) / NANOS_PER_MILLI);
            assertThat(result.status()).isEqualTo(201);
        }

        List<Double> sorted = millis.stream().sorted().toList();
        double median = (sorted.get(sorted.size() / 2 - 1) + sorted.get(sorted.size() / 2)) / 2;
        double p95 = sorted.get((int) Math.ceil(sorted.size() * PERCENTILE) - 1);
        double max = sorted.getLast();
        String summary = String.format(Locale.ROOT,
                "Crear deck, 10.000 cartas, %d medidas tras %d de calentamiento: mediana = %.1f ms, p95 = %.1f ms,"
                        + " máximo = %.1f ms%nMedidas (ms): %s",
                MEASURED_CREATIONS, WARM_UP_CREATIONS, median, p95, max,
                millis.stream().map(value -> String.format(Locale.ROOT, "%.1f", value)).toList());
        System.out.println(summary);
        report.add(summary);

        assertThat(p95).isLessThan(MAX_MILLIS);
    }

    @Test
    @Order(2)
    @DisplayName("13: el plan de la consulta de posesión usa la clave pk_player_card y no lee card ni player_card completas")
    void ownershipPlanUsesPlayerCardKey() {
        List<String> lines = jdbc.queryForList("EXPLAIN (ANALYZE) " + JdbcPlayerCardRepository.ownedCardsSql(deck.size()),
                String.class, JdbcPlayerCardRepository.ownedCardsArguments(player, deck));
        String plan = String.join("\n", lines);
        Matcher time = EXECUTION_TIME.matcher(plan);
        String executionTime = time.find() ? time.group(1) + " ms" : "no informado";
        String summary = "Plan de findOwnedCards (12 códigos, " + EXTRA_PLAYERS + " jugadores más en player_card),"
                + " tiempo de ejecución " + executionTime + ":\n" + plan;
        System.out.println(summary);
        report.add(summary);

        // Se comprueba por nombre de índice y por ausencia de lecturas completas, no por el texto exacto del plan:
        // Index Scan o Bitmap Index Scan son igual de válidos.
        assertThat(plan).contains("pk_player_card");
        assertThat(FULL_SCAN.matcher(plan).find()).as("lectura completa de card o player_card en:%n%s", plan)
                .isFalse();
    }

    private void clearCatalog() {
        jdbc.update("DELETE FROM card");
        jdbc.update("UPDATE card_type_counter SET last_number = 0");
    }
}
