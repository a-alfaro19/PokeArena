package cr.ac.tec.pokearena.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import cr.ac.tec.pokearena.application.dto.MatchRequest;
import cr.ac.tec.pokearena.application.dto.MatchStarted;
import cr.ac.tec.pokearena.application.port.InitialCollectionStatus;
import cr.ac.tec.pokearena.application.port.UnitOfWork;
import cr.ac.tec.pokearena.domain.model.MatchContext;
import cr.ac.tec.pokearena.domain.model.MatchSeat;
import cr.ac.tec.pokearena.domain.model.StoredDeck;
import cr.ac.tec.pokearena.domain.repository.DeckRepository;
import cr.ac.tec.pokearena.domain.rule.DeckRules;
import cr.ac.tec.pokearena.support.PostgresIntegrationTestBase;
import cr.ac.tec.pokearena.support.TestPlayers;

/**
 * Propósito: medir la preparación completa de una partida con el catálogo de 10.000 cartas.
 * Responsabilidades: usar la semilla y la colección real de prueba, medir el caso de uso y guardar el resultado
 * en target/start-match-performance.txt.
 * Referencia: sección 13 (rendimiento: menos de 300 ms); criterio C.10-E2.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Preparar partida con 10.000 cartas (rendimiento)")
class StartMatchPerformanceIT extends PostgresIntegrationTestBase {

    private static final Path SEED_SCRIPT = Path.of("scripts", "seed-10000-cartas.sql");
    private static final Path REPORT_PATH = Path.of("target", "start-match-performance.txt");
    private static final long MAX_MILLIS = 300;
    private static final int WARM_UP_MATCHES = 3;
    private static final int MEASURED_MATCHES = 20;
    private static final double PERCENTILE = 0.95;
    private static final double NANOS_PER_MILLI = 1_000_000.0;
    private static final int LOCATION_COUNT = 3;
    private static final Instant CREATED_AT = Instant.parse("2026-11-19T12:00:00Z");

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlayerAccountService accounts;

    @Autowired
    private DeliverInitialCollectionUseCase initialCollection;

    @Autowired
    private DeckRepository decks;

    @Autowired
    private UnitOfWork unitOfWork;

    @Autowired
    private StartMatchUseCase startMatch;

    private final List<String> report = new ArrayList<>();
    private final List<Long> locationIds = new ArrayList<>();
    private final List<UUID> matchIds = new ArrayList<>();
    private UUID player;
    private long deckId;
    private List<String> deckCodes;

    @BeforeAll
    void seed() throws IOException {
        clearCatalog();
        jdbc.execute(Files.readString(SEED_SCRIPT));
        player = TestPlayers.create(accounts).id();
        assertThat(initialCollection.execute(player)).isEqualTo(InitialCollectionStatus.DELIVERED);
        List<String> collection = jdbc.queryForList(
                "SELECT card_code FROM player_card WHERE player_id = ? ORDER BY card_code", String.class, player);
        deckCodes = collection.subList(0, DeckRules.DECK_SIZE);
        StoredDeck deck = unitOfWork.execute(() -> decks
                .save(DeckRules.validateShape(player, "Deck de rendimiento", deckCodes, CREATED_AT)));
        deckId = deck.id();
        seedLocations();
    }

    @AfterAll
    void clean() throws IOException {
        try {
            Files.createDirectories(REPORT_PATH.getParent());
            Files.writeString(REPORT_PATH, String.join(System.lineSeparator(), report));
        } finally {
            try {
                for (UUID id : matchIds) {
                    jdbc.update("DELETE FROM game_match_card WHERE match_id = ?", id);
                    jdbc.update("DELETE FROM game_match_location WHERE match_id = ?", id);
                    jdbc.update("DELETE FROM game_match_player WHERE match_id = ?", id);
                    jdbc.update("DELETE FROM game_match WHERE id = ?", id);
                }
                locationIds.forEach(id -> jdbc.update("DELETE FROM location WHERE id = ?", id));
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
    @DisplayName("13: preparar una partida con 10.000 cartas responde en menos de 300 ms (p95)")
    void preparesMatchWithinPerformanceTarget() {
        for (int index = 0; index < WARM_UP_MATCHES; index++) {
            releasePlayer(startMatch());
        }

        List<Double> millis = new ArrayList<>();
        for (int index = 0; index < MEASURED_MATCHES; index++) {
            long startedAt = System.nanoTime();
            UUID matchId = startMatch();
            millis.add((System.nanoTime() - startedAt) / NANOS_PER_MILLI);
            releasePlayer(matchId);
        }

        List<Double> sorted = millis.stream().sorted().toList();
        double median = (sorted.get(sorted.size() / 2 - 1) + sorted.get(sorted.size() / 2)) / 2;
        double p95 = sorted.get((int) Math.ceil(sorted.size() * PERCENTILE) - 1);
        double max = sorted.getLast();
        String summary = String.format(Locale.ROOT,
                "Preparar partida, 10.000 cartas, %d medidas tras %d de calentamiento: mediana = %.1f ms,"
                        + " p95 = %.1f ms, máximo = %.1f ms%nMedidas (ms): %s",
                MEASURED_MATCHES, WARM_UP_MATCHES, median, p95, max,
                millis.stream().map(value -> String.format(Locale.ROOT, "%.1f", value)).toList());
        System.out.println(summary);
        report.add(summary);

        assertThat(p95).isLessThan(MAX_MILLIS);
    }

    private UUID startMatch() {
        MatchSeat human = new MatchSeat(MatchSeat.FIRST_SEAT, player, deckId, deckCodes);
        MatchSeat bot = new MatchSeat(MatchSeat.SECOND_SEAT, null, null, deckCodes);
        MatchRequest request = new MatchRequest(MatchContext.RANKED, List.of(human, bot));
        MatchStarted started = unitOfWork.execute(() -> startMatch.execute(request));
        matchIds.add(started.id());
        return started.id();
    }

    private void releasePlayer(UUID matchId) {
        jdbc.update("UPDATE game_match_player SET active = false WHERE match_id = ? AND player_id = ?",
                matchId, player);
    }

    private void seedLocations() {
        for (int index = 0; index < LOCATION_COUNT; index++) {
            Long id = jdbc.queryForObject("""
                    INSERT INTO location (name, description, card_limit, extra_energy_mode, extra_energy_amount,
                                          image_large_url, image_small_url, image_status, available, created_at)
                    VALUES (?, 'Ubicación para rendimiento', 3, 'NONE', 0, '/large.png', '/small.png',
                            'READY', true, ?)
                    RETURNING id
                    """, Long.class, "Ubicación rendimiento " + index,
                    OffsetDateTime.ofInstant(CREATED_AT, ZoneOffset.UTC));
            locationIds.add(id);
        }
    }

    private void clearCatalog() {
        jdbc.update("DELETE FROM card");
        jdbc.update("UPDATE card_type_counter SET last_number = 0");
    }
}
