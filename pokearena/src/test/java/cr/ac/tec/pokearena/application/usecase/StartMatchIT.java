package cr.ac.tec.pokearena.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import cr.ac.tec.pokearena.application.dto.MatchRequest;
import cr.ac.tec.pokearena.application.dto.MatchSeat;
import cr.ac.tec.pokearena.application.dto.MatchView;
import cr.ac.tec.pokearena.application.port.Clock;
import cr.ac.tec.pokearena.application.port.RandomSource;
import cr.ac.tec.pokearena.application.port.UnitOfWork;
import cr.ac.tec.pokearena.domain.effect.LocationEffectRegistry;
import cr.ac.tec.pokearena.domain.effect.TurnRestrictionEffect;
import cr.ac.tec.pokearena.domain.model.ExtraEnergy;
import cr.ac.tec.pokearena.domain.model.Location;
import cr.ac.tec.pokearena.domain.model.LocationEffect;
import cr.ac.tec.pokearena.domain.model.LocationFields;
import cr.ac.tec.pokearena.domain.model.Match;
import cr.ac.tec.pokearena.domain.model.MatchContext;
import cr.ac.tec.pokearena.domain.model.MatchParticipant;
import cr.ac.tec.pokearena.domain.model.MatchZone;
import cr.ac.tec.pokearena.domain.repository.DeckRepository;
import cr.ac.tec.pokearena.domain.repository.LocationRepository;
import cr.ac.tec.pokearena.domain.repository.MatchRepository;
import cr.ac.tec.pokearena.domain.rule.DeckRules;
import cr.ac.tec.pokearena.domain.rule.SeedMixer;
import cr.ac.tec.pokearena.domain.rule.SeededShuffle;
import cr.ac.tec.pokearena.support.PostgresIntegrationTestBase;
import cr.ac.tec.pokearena.support.TestCards;
import cr.ac.tec.pokearena.support.TestPlayers;

/**
 * Propósito: comprobar que la preparación real persiste y reproduce una partida completa.
 * Responsabilidades: verificar ubicaciones, decks, zonas, energía, apuesta, autorización de lectura y rollback.
 * Referencia: sección 7.3; criterios C.10-E1 a E8, R3 y A1; Anexos A.14 y A.15.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@DisplayName("Preparación de partida (PostgreSQL)")
class StartMatchIT extends PostgresIntegrationTestBase {

    private static final Instant NOW = Instant.parse("2026-11-24T12:00:00Z");
    private static final long MATCH_SEED = 763451L;
    private static final long FAILING_MATCH_SEED = 763452L;
    private static final String DECK_NAME = "Deck de integración de partida";

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlayerAccountService accounts;

    @Autowired
    private DeckRepository decks;

    @Autowired
    private LocationRepository locations;

    @Autowired
    private MatchRepository matches;

    @Autowired
    private UnitOfWork unitOfWork;

    @Autowired
    private StartMatchUseCase start;

    @Autowired
    private LocationEffectRegistry effects;

    @MockitoBean
    private RandomSource random;

    @MockitoBean
    private Clock clock;

    private final List<UUID> playerIds = new ArrayList<>();
    private final List<UUID> matchIds = new ArrayList<>();
    private final List<Long> locationIds = new ArrayList<>();
    private UUID firstPlayer;
    private UUID secondPlayer;
    private long firstDeckId;
    private long secondDeckId;
    private List<String> firstDeck;
    private List<String> secondDeck;

    @BeforeEach
    void setUp() {
        when(clock.now()).thenReturn(NOW);
        TestCards testCards = new TestCards(jdbc);
        testCards.reset();
        List<String> cards = IntStream.range(0, DeckRules.DECK_SIZE * 2)
                .mapToObj(index -> testCards.card("COMMON", true, true)).toList();
        firstDeck = cards.subList(0, DeckRules.DECK_SIZE);
        secondDeck = cards.subList(DeckRules.DECK_SIZE, cards.size());

        firstPlayer = TestPlayers.create(accounts).id();
        secondPlayer = TestPlayers.create(accounts).id();
        playerIds.addAll(List.of(firstPlayer, secondPlayer));
        firstDeckId = saveDeck(firstPlayer, "primero", firstDeck);
        secondDeckId = saveDeck(secondPlayer, "segundo", secondDeck);
        saveLocations();
    }

    @AfterEach
    void cleanUp() {
        for (UUID matchId : matchIds) {
            deleteMatch(matchId);
        }
        try {
            TestPlayers.delete(jdbc, playerIds);
        } finally {
            for (Long locationId : locationIds) {
                jdbc.update("DELETE FROM location WHERE id = ?", locationId);
            }
            new TestCards(jdbc).reset();
        }
    }

    @Nested
    @DisplayName("Éxito")
    class Success {

        @Test
        @DisplayName("C.10-E1 a E8 y A1: persiste el inicio y repite exactamente la preparación con la misma semilla")
        void persistsAndReproducesTheInitialMatch() {
            when(random.seed()).thenReturn(MATCH_SEED);

            Match first = startAndRead(firstDeck, secondDeck);
            assertInitialMatch(first);
            MatchView firstView = MatchView.forSeat(first, firstPlayer, NOW);
            MatchView secondView = MatchView.forSeat(matches.findByIdForPlayer(first.id(), secondPlayer)
                    .orElseThrow(), secondPlayer, NOW);
            assertThat(firstView.locations()).containsExactlyElementsOf(secondView.locations());

            deleteMatch(first.id());
            matchIds.remove(first.id());
            Match replay = startAndRead(firstDeck, secondDeck);

            assertThat(replay.seed()).isEqualTo(first.seed());
            assertThat(replay.locations()).extracting(location -> location.locationId())
                    .containsExactlyElementsOf(first.locations().stream()
                            .map(location -> location.locationId()).toList());
            for (int index = 0; index < MatchParticipant.LAST_SEAT; index++) {
                assertThat(replay.participants().get(index).cards())
                        .containsExactlyElementsOf(first.participants().get(index).cards());
            }
        }
    }

    @Nested
    @DisplayName("Errores")
    class Errors {

        @Test
        @DisplayName("C.10-R3: un fallo al insertar la tercera carta revierte partida, asientos y ubicaciones")
        void rollsBackPartialMatch() {
            when(random.seed()).thenReturn(FAILING_MATCH_SEED);
            List<String> invalidDeck = new ArrayList<>(firstDeck);
            invalidDeck.set(2, "PKA-FUE-999999");

            assertThatThrownBy(() -> unitOfWork.execute(() -> start.execute(request(invalidDeck, secondDeck))))
                    .isInstanceOf(DataIntegrityViolationException.class);

            assertThat(countRows("game_match", "seed", FAILING_MATCH_SEED)).isZero();
            assertThat(countRowsWithMatchSeed("game_match_player", FAILING_MATCH_SEED)).isZero();
            assertThat(countRowsWithMatchSeed("game_match_location", FAILING_MATCH_SEED)).isZero();
            assertThat(countRowsWithMatchSeed("game_match_card", FAILING_MATCH_SEED)).isZero();
        }
    }

    private Match startAndRead(List<String> firstCodes, List<String> secondCodes) {
        UUID id = unitOfWork.execute(() -> start.execute(request(firstCodes, secondCodes)).matchId());
        matchIds.add(id);
        return matches.findByIdForPlayer(id, firstPlayer).orElseThrow();
    }

    private MatchRequest request(List<String> firstCodes, List<String> secondCodes) {
        return new MatchRequest(MatchContext.RANKED, new MatchSeat(firstPlayer, firstDeckId, firstCodes),
                new MatchSeat(secondPlayer, secondDeckId, secondCodes));
    }

    private void assertInitialMatch(Match match) {
        assertThat(match.context()).isEqualTo(MatchContext.RANKED);
        assertThat(match.vsBot()).isFalse();
        assertThat(match.seed()).isEqualTo(MATCH_SEED);
        assertThat(match.bet()).isEqualTo(4);
        assertThat(match.turn()).isEqualTo(1);
        assertThat(match.turnStartedAt()).isEqualTo(NOW);
        assertThat(match.turnDurationSeconds()).isEqualTo(30);
        assertThat(match.locations()).hasSize(3)
                .extracting(location -> location.locationId()).doesNotHaveDuplicates();
        assertThat(match.locations()).filteredOn(this::isPlayable).hasSizeGreaterThanOrEqualTo(2);
        assertThat(match.participants()).extracting(MatchParticipant::seat).containsExactly(1, 2);

        assertParticipant(match.participants().getFirst(), firstDeck, 1);
        assertParticipant(match.participants().getLast(), secondDeck, 2);
    }

    private void assertParticipant(MatchParticipant participant, List<String> deck, int seat) {
        assertThat(participant.playerId()).isEqualTo(seat == 1 ? firstPlayer : secondPlayer);
        assertThat(participant.energy()).isEqualTo(3);
        assertThat(participant.active()).isTrue();
        assertThat(participant.cards()).hasSize(DeckRules.DECK_SIZE)
                .extracting(card -> card.cardCode())
                .containsExactlyElementsOf(SeededShuffle.shuffle(deck, SeedMixer.derive(MATCH_SEED, "seat-" + seat)));
        assertThat(participant.cards()).filteredOn(card -> card.zone() == MatchZone.HAND).hasSize(4);
        assertThat(participant.cards()).filteredOn(card -> card.zone() == MatchZone.DECK).hasSize(8);
        assertThat(participant.cards()).filteredOn(card -> card.zone() == MatchZone.GRAVEYARD).isEmpty();
    }

    private boolean isPlayable(cr.ac.tec.pokearena.domain.model.MatchLocation assigned) {
        var location = assigned.location();
        return location.fields().cardLimit() > 0
                && (location.effect() == null || !effects.blocksAllTurns(location.effect()));
    }

    private long saveDeck(UUID player, String suffix, List<String> codes) {
        return unitOfWork.execute(() -> decks.save(
                DeckRules.validateShape(player, DECK_NAME + " " + suffix, codes, NOW))).id();
    }

    private void saveLocations() {
        for (int slot = 1; slot <= 5; slot++) {
            int cardLimit = slot == 1 || slot == 2 || slot == 4 ? 3 : 0;
            ExtraEnergy energy = slot == 1 ? new ExtraEnergy(ExtraEnergy.Mode.FIXED, 1)
                    : slot == 2 ? new ExtraEnergy(ExtraEnergy.Mode.PER_CARD, 1) : ExtraEnergy.none();
            LocationEffect effect = slot == 4 ? restrictionForEveryTurn() : null;
            String name = "start-match-it-" + slot;
            Location location = Location.create(
                    new LocationFields(name, "Ubicación para " + name, cardLimit, energy), effect,
                    "/images/locations/" + name + "/large.png", "/images/locations/" + name + "/small.png", NOW);
            locationIds.add(unitOfWork.execute(() -> locations.save(location)));
        }
    }

    private static LocationEffect restrictionForEveryTurn() {
        return new LocationEffect(TurnRestrictionEffect.CODE,
                Map.of("turns", List.of(1, 2, 3, 4, 5, 6)));
    }

    private int countRows(String table, String column, long value) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE " + column + " = ?",
                Integer.class, value);
    }

    private int countRowsWithMatchSeed(String table, long seed) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table + " child "
                + "JOIN game_match parent ON parent.id = child.match_id WHERE parent.seed = ?",
                Integer.class, seed);
    }

    private void deleteMatch(UUID matchId) {
        jdbc.update("DELETE FROM game_match_card WHERE match_id = ?", matchId);
        jdbc.update("DELETE FROM game_match_location WHERE match_id = ?", matchId);
        jdbc.update("DELETE FROM game_match_player WHERE match_id = ?", matchId);
        jdbc.update("DELETE FROM game_match WHERE id = ?", matchId);
    }
}
