package cr.ac.tec.pokearena.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import cr.ac.tec.pokearena.application.port.UnitOfWork;
import cr.ac.tec.pokearena.application.usecase.PlayerAccountService;
import cr.ac.tec.pokearena.domain.model.AvailableLocation;
import cr.ac.tec.pokearena.domain.model.Match;
import cr.ac.tec.pokearena.domain.model.MatchCard;
import cr.ac.tec.pokearena.domain.model.MatchContext;
import cr.ac.tec.pokearena.domain.model.MatchLocation;
import cr.ac.tec.pokearena.domain.model.MatchParticipant;
import cr.ac.tec.pokearena.domain.model.MatchSeat;
import cr.ac.tec.pokearena.domain.model.MatchStatus;
import cr.ac.tec.pokearena.domain.model.MatchZone;
import cr.ac.tec.pokearena.domain.model.NewMatch;
import cr.ac.tec.pokearena.domain.model.StoredDeck;
import cr.ac.tec.pokearena.domain.repository.DeckRepository;
import cr.ac.tec.pokearena.domain.repository.LocationRepository;
import cr.ac.tec.pokearena.domain.repository.MatchRepository;
import cr.ac.tec.pokearena.domain.rule.DeckRules;
import cr.ac.tec.pokearena.support.PostgresIntegrationTestBase;
import cr.ac.tec.pokearena.support.TestCards;
import cr.ac.tec.pokearena.support.TestPlayers;

/**
 * Propósito: verificar {@link JdbcMatchRepository} sobre PostgreSQL real.
 * Responsabilidades: persistir y leer partidas, ocultar partidas ajenas, detectar partidas activas y probar
 * que la transacción del llamador revierte las filas relacionadas incluso ante un fallo parcial.
 * Referencia: secciones 6.2 y 7.3; Anexos A.14 y A.15; criterios C.10-R3 y C.10-A1.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@DisplayName("Repositorio de partidas (JDBC)")
class JdbcMatchRepositoryIT extends PostgresIntegrationTestBase {

    private static final Instant NOW = Instant.parse("2026-11-19T12:00:00Z");
    private static final int CATALOG_SIZE = DeckRules.DECK_SIZE + 2;
    private static final int TEST_LOCATION_COUNT = 3;
    private static final int INITIAL_HAND_AND_DRAW_SIZE = 4;
    private static final int INITIAL_ENERGY = 3;
    private static final int INITIAL_BET = 4;
    private static final int INITIAL_TURN = 1;
    private static final int TURN_DURATION_SECONDS = 30;
    private static final int FIRST_LOCATION_SLOT = 1;
    private static final long TEST_MATCH_SEED = 345678L;
    private static final int TEST_CARD_LIMIT = 3;
    private static final int TEST_EXTRA_ENERGY = 1;

    @Autowired
    private MatchRepository matches;

    @Autowired
    private LocationRepository locations;

    @Autowired
    private DeckRepository decks;

    @Autowired
    private PlayerAccountService accounts;

    @Autowired
    private UnitOfWork unitOfWork;

    @Autowired
    private JdbcTemplate jdbc;

    private final List<UUID> players = new ArrayList<>();
    private final List<UUID> matchIds = new ArrayList<>();
    private final List<Long> locationIds = new ArrayList<>();
    private List<String> catalog;

    @BeforeEach
    void seedCatalog() {
        TestCards cards = new TestCards(jdbc);
        cards.reset();
        catalog = new ArrayList<>();
        for (int index = 0; index < CATALOG_SIZE; index++) {
            catalog.add(cards.card("COMMON", true, true));
        }
    }

    @AfterEach
    void cleanUp() {
        try {
            for (UUID id : matchIds) {
                jdbc.update("DELETE FROM game_match_card WHERE match_id = ?", id);
                jdbc.update("DELETE FROM game_match_location WHERE match_id = ?", id);
                jdbc.update("DELETE FROM game_match_player WHERE match_id = ?", id);
                jdbc.update("DELETE FROM game_match WHERE id = ?", id);
            }
            locationIds.forEach(id -> jdbc.update("DELETE FROM location WHERE id = ?", id));
            TestPlayers.delete(jdbc, players);
        } finally {
            new TestCards(jdbc).reset();
        }
    }

    @Test
    @DisplayName("Guarda el estado reproducible y solo devuelve la partida a sus participantes")
    void savesAndReadsForParticipants() {
        UUID playerId = newPlayer();
        StoredDeck deck = saveDeck(playerId);
        UUID outsider = newPlayer();
        List<MatchLocation> selectedLocations = seedLocations();
        NewMatch newMatch = newMatch(playerId, deck.id(), selectedLocations);

        UUID matchId = unitOfWork.execute(() -> matches.save(newMatch));
        matchIds.add(matchId);

        assertThat(matches.findActiveMatchId(playerId)).contains(matchId);
        assertThat(matches.findActiveMatchId(outsider)).isEmpty();
        Match match = matches.findByIdForPlayer(matchId, playerId).orElseThrow();
        assertThat(matches.findByIdForPlayer(matchId, outsider)).isEmpty();
        assertThat(match.id()).isEqualTo(matchId);
        assertThat(match.context()).isEqualTo(MatchContext.RANKED);
        assertThat(match.vsBot()).isTrue();
        assertThat(match.status()).isEqualTo(MatchStatus.ACTIVE);
        assertThat(match.seed()).isEqualTo(345678L);
        assertThat(match.bet()).isEqualTo(4);
        assertThat(match.turn()).isEqualTo(1);
        assertThat(match.turnStartedAt()).isEqualTo(NOW);
        assertThat(match.locations()).extracting(MatchLocation::id)
                .containsExactlyElementsOf(selectedLocations.stream().map(MatchLocation::id).toList());
        assertThat(match.participants()).hasSize(2);
        assertThat(match.participants().getFirst().cards()).hasSize(DeckRules.DECK_SIZE);
        assertThat(match.participants().getFirst().cards()).extracting(MatchCard::position)
                .containsExactlyElementsOf(java.util.stream.IntStream.range(0, DeckRules.DECK_SIZE).boxed().toList());
        assertThat(match.participants().get(1).isBot()).isTrue();
        assertThat(match.participants().get(1).playerId()).isNull();
        assertThat(match.cardsByCode()).containsKey(catalog.getFirst());
        assertThat(match.cardsByCode().get(catalog.getFirst()).fields().cost()).isEqualTo(1);
    }

    @Test
    @DisplayName("Exige la transacción del llamador y revierte también si falla después de insertar la partida")
    void requiresCallerTransactionAndRollsBackPartialSave() {
        UUID playerId = newPlayer();
        StoredDeck deck = saveDeck(playerId);
        List<MatchLocation> selectedLocations = seedLocations();
        NewMatch valid = newMatch(playerId, deck.id(), selectedLocations);
        int matchesBefore = count("SELECT count(*) FROM game_match");

        assertThatThrownBy(() -> matches.save(valid))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("transacción");
        assertThat(count("SELECT count(*) FROM game_match")).isEqualTo(matchesBefore);

        List<MatchLocation> invalidLocations = List.of(
                new MatchLocation(1, Long.MAX_VALUE, selectedLocations.getFirst().location()));
        NewMatch invalid = newMatch(playerId, deck.id(), invalidLocations);
        assertThatThrownBy(() -> unitOfWork.execute(() -> matches.save(invalid)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(count("SELECT count(*) FROM game_match")).isEqualTo(matchesBefore);
        assertThat(count("SELECT count(*) FROM game_match_player")).isZero();
        assertThat(count("SELECT count(*) FROM game_match_location")).isZero();
        assertThat(count("SELECT count(*) FROM game_match_card")).isZero();
    }

    private UUID newPlayer() {
        UUID id = TestPlayers.create(accounts).id();
        players.add(id);
        return id;
    }

    private StoredDeck saveDeck(UUID playerId) {
        return unitOfWork.execute(() -> decks.save(
                DeckRules.validateShape(playerId, "Deck de prueba", catalog.subList(0, DeckRules.DECK_SIZE), NOW)));
    }

    private List<MatchLocation> seedLocations() {
        for (int index = 0; index < TEST_LOCATION_COUNT; index++) {
            Long id = jdbc.queryForObject("""
                    INSERT INTO location (name, description, card_limit, extra_energy_mode, extra_energy_amount,
                                          image_large_url, image_small_url, image_status, available, created_at)
                    VALUES (?, 'Ubicación de prueba', ?, 'FIXED', ?, '/large.png', '/small.png',
                            'READY', true, ?)
                    RETURNING id
                    """, Long.class, "Ubicación de prueba " + index, TEST_CARD_LIMIT, TEST_EXTRA_ENERGY,
                    OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC));
            locationIds.add(id);
        }
        List<AvailableLocation> available = locations.findAllAvailable().stream()
                .filter(location -> locationIds.contains(location.id()))
                .toList();
        List<MatchLocation> result = new ArrayList<>();
        for (int index = 0; index < available.size(); index++) {
            result.add(new MatchLocation(index + FIRST_LOCATION_SLOT, available.get(index)));
        }
        return List.copyOf(result);
    }

    private NewMatch newMatch(UUID playerId, long deckId, List<MatchLocation> selectedLocations) {
        List<MatchCard> humanCards = cardsInDeck();
        List<MatchCard> botCards = cardsInDeck();
        MatchParticipant player = new MatchParticipant(MatchSeat.FIRST_SEAT, playerId, false, deckId,
                INITIAL_ENERGY, true, humanCards);
        MatchParticipant bot = new MatchParticipant(MatchSeat.SECOND_SEAT, null, true, null, INITIAL_ENERGY, true,
                botCards);
        return new NewMatch(MatchContext.RANKED, true, MatchStatus.ACTIVE, TEST_MATCH_SEED, INITIAL_BET, INITIAL_TURN,
                NOW, TURN_DURATION_SECONDS, NOW, selectedLocations, List.of(player, bot));
    }

    private List<MatchCard> cardsInDeck() {
        List<MatchCard> cards = new ArrayList<>();
        for (int position = 0; position < DeckRules.DECK_SIZE; position++) {
            cards.add(new MatchCard(catalog.get(position), position,
                    position < INITIAL_HAND_AND_DRAW_SIZE ? MatchZone.HAND : MatchZone.DECK));
        }
        return cards;
    }

    private int count(String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }
}
