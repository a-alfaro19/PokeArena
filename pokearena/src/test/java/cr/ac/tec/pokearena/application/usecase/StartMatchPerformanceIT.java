package cr.ac.tec.pokearena.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
import cr.ac.tec.pokearena.application.dto.MatchSeat;
import cr.ac.tec.pokearena.application.port.InitialCollectionStatus;
import cr.ac.tec.pokearena.application.port.Clock;
import cr.ac.tec.pokearena.application.port.UnitOfWork;
import cr.ac.tec.pokearena.application.usecase.BotDeckSelector;
import cr.ac.tec.pokearena.application.usecase.PlayerAccountService;
import cr.ac.tec.pokearena.domain.model.ExtraEnergy;
import cr.ac.tec.pokearena.domain.model.Location;
import cr.ac.tec.pokearena.domain.model.LocationFields;
import cr.ac.tec.pokearena.domain.model.MatchContext;
import cr.ac.tec.pokearena.domain.repository.DeckRepository;
import cr.ac.tec.pokearena.domain.repository.LocationRepository;
import cr.ac.tec.pokearena.domain.rule.DeckRules;
import cr.ac.tec.pokearena.support.PostgresIntegrationTestBase;
import cr.ac.tec.pokearena.support.TestPlayers;

/**
 * Propósito: medir la preparación completa de una partida con un catálogo de 10.000 cartas.
 * Responsabilidades: crear un deck real, preparar y guardar partidas dentro del UnitOfWork, medir el p95 y
 * escribir el resultado en target para su revisión.
 * Referencia: sección 13, fila Rendimiento; criterio C.10 y Anexo A.14.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Preparar partida con 10.000 cartas (rendimiento)")
class StartMatchPerformanceIT extends PostgresIntegrationTestBase {

    private static final Path SEED_SCRIPT = Path.of("scripts", "seed-10000-cartas.sql");
    private static final Path REPORT = Path.of("target", "start-match-performance.txt");
    private static final long MAX_MILLIS = 300;
    private static final int WARM_UP_MATCHES = 3;
    private static final int MEASURED_MATCHES = 20;
    private static final double PERCENTILE = 0.95;
    private static final double NANOS_PER_MILLI = 1_000_000.0;
    private static final String DECK_NAME = "Deck de rendimiento de partida";

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlayerAccountService accounts;

    @Autowired
    private DeliverInitialCollectionUseCase initialCollection;

    @Autowired
    private DeckRepository decks;

    @Autowired
    private LocationRepository locations;

    @Autowired
    private UnitOfWork unitOfWork;

    @Autowired
    private Clock clock;

    @Autowired
    private BotDeckSelector botDecks;

    @Autowired
    private StartMatchUseCase start;

    private final List<String> report = new ArrayList<>();
    private final List<Long> locationIds = new ArrayList<>();
    private UUID player;
    private long deckId;
    private MatchRequest request;

    @BeforeAll
    void seed() throws IOException {
        clearCatalog();
        jdbc.execute(Files.readString(SEED_SCRIPT));

        player = TestPlayers.create(accounts).id();
        assertThat(initialCollection.execute(player)).isEqualTo(InitialCollectionStatus.DELIVERED);
        List<String> collection = jdbc.queryForList(
                "SELECT card_code FROM player_card WHERE player_id = ? ORDER BY card_code", String.class, player);
        List<String> deck = collection.subList(0, DeckRules.DECK_SIZE);
        List<String> botDeck = botDecks.select();
        deckId = unitOfWork.execute(() -> decks.save(
                DeckRules.validateShape(player, DECK_NAME, deck, clock.now()))).id();

        for (int slot = 1; slot <= 3; slot++) {
            String name = "start-match-performance-" + slot;
            Location location = Location.create(new LocationFields(name, "Ubicación " + slot, 3, ExtraEnergy.none()),
                    null, "/images/locations/" + slot + "/large.png", "/images/locations/" + slot + "/small.png",
                    clock.now());
            locationIds.add(unitOfWork.execute(() -> locations.save(location)));
        }
        request = new MatchRequest(MatchContext.RANKED, new MatchSeat(player, deckId, deck),
                new MatchSeat(null, null, botDeck));
    }

    @AfterAll
    void clean() throws IOException {
        try {
            Files.createDirectories(REPORT.getParent());
            Files.writeString(REPORT, String.join(System.lineSeparator(), report));
        } finally {
            try {
                if (player != null) {
                    TestPlayers.delete(jdbc, List.of(player));
                }
            } finally {
                try {
                    for (Long locationId : locationIds) {
                        jdbc.update("DELETE FROM location WHERE id = ?", locationId);
                    }
                } finally {
                    clearCatalog();
                }
            }
        }
    }

    @Test
    @DisplayName("13 y C.10: con 10.000 cartas, preparar y persistir una partida responde en menos de 300 ms (p95)")
    void preparingMatchIsFast() {
        for (int index = 0; index < WARM_UP_MATCHES; index++) {
            prepareAndDelete();
        }

        List<Double> millis = new ArrayList<>();
        for (int index = 0; index < MEASURED_MATCHES; index++) {
            long startedAt = System.nanoTime();
            UUID matchId = unitOfWork.execute(() -> start.execute(request).matchId());
            millis.add((System.nanoTime() - startedAt) / NANOS_PER_MILLI);
            deleteMatch(matchId);
        }

        List<Double> sorted = millis.stream().sorted().toList();
        double p95 = sorted.get((int) Math.ceil(sorted.size() * PERCENTILE) - 1);
        double median = (sorted.get(sorted.size() / 2 - 1) + sorted.get(sorted.size() / 2)) / 2;
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

    private void prepareAndDelete() {
        UUID matchId = unitOfWork.execute(() -> start.execute(request).matchId());
        deleteMatch(matchId);
    }

    private void deleteMatch(UUID matchId) {
        jdbc.update("DELETE FROM game_match_card WHERE match_id = ?", matchId);
        jdbc.update("DELETE FROM game_match_location WHERE match_id = ?", matchId);
        jdbc.update("DELETE FROM game_match_player WHERE match_id = ?", matchId);
        jdbc.update("DELETE FROM game_match WHERE id = ?", matchId);
    }

    private void clearCatalog() {
        jdbc.update("DELETE FROM card");
        jdbc.update("UPDATE card_type_counter SET last_number = 0");
    }
}
