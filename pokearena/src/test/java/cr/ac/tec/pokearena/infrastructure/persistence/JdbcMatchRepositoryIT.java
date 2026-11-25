package cr.ac.tec.pokearena.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

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
import cr.ac.tec.pokearena.domain.model.ExtraEnergy;
import cr.ac.tec.pokearena.domain.model.Location;
import cr.ac.tec.pokearena.domain.model.LocationFields;
import cr.ac.tec.pokearena.domain.model.Match;
import cr.ac.tec.pokearena.domain.model.MatchCard;
import cr.ac.tec.pokearena.domain.model.MatchContext;
import cr.ac.tec.pokearena.domain.model.MatchLocation;
import cr.ac.tec.pokearena.domain.model.MatchParticipant;
import cr.ac.tec.pokearena.domain.model.MatchStatus;
import cr.ac.tec.pokearena.domain.model.MatchZone;
import cr.ac.tec.pokearena.domain.model.NewMatch;
import cr.ac.tec.pokearena.domain.repository.ActiveMatchLookup;
import cr.ac.tec.pokearena.domain.repository.DeckRepository;
import cr.ac.tec.pokearena.domain.repository.LocationRepository;
import cr.ac.tec.pokearena.domain.repository.MatchRepository;
import cr.ac.tec.pokearena.domain.rule.DeckRules;
import cr.ac.tec.pokearena.domain.rule.SeededShuffle;
import cr.ac.tec.pokearena.support.PostgresIntegrationTestBase;
import cr.ac.tec.pokearena.support.TestCards;
import cr.ac.tec.pokearena.support.TestPlayers;

/**
 * Propósito: verificar el guardado y lectura del agregado de partida en PostgreSQL real.
 * Responsabilidades: cubrir los datos por zona, la autorización por participante, el bloqueo transaccional
 * y el rollback si una inserción hija falla.
 * Referencia: sección 6.2; sección 7.3, pasos 6 y 7; criterios C.10-R3 y C.10-A1.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@DisplayName("Repositorio de partidas (JDBC)")
class JdbcMatchRepositoryIT extends PostgresIntegrationTestBase {

    private static final Instant START = Instant.parse("2026-11-24T12:00:00Z");
    private static final String DECK_NAME = "Deck de integración";

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private UnitOfWork unitOfWork;

    @Autowired
    private PlayerAccountService accounts;

    @Autowired
    private DeckRepository decks;

    @Autowired
    private LocationRepository locations;

    @Autowired
    private MatchRepository matches;

    @Autowired
    private ActiveMatchLookup activeMatches;

    private final List<UUID> players = new ArrayList<>();
    private final List<UUID> savedMatches = new ArrayList<>();
    private final List<Long> savedLocations = new ArrayList<>();
    private List<String> deckCodes;
    private UUID player;
    private long deckId;

    @BeforeEach
    void seedCatalogAndDeck() {
        TestCards cards = new TestCards(jdbc);
        cards.reset();
        deckCodes = IntStream.range(0, DeckRules.DECK_SIZE)
                .mapToObj(index -> cards.card("COMMON", true, true))
                .toList();
        player = TestPlayers.create(accounts).id();
        players.add(player);
        deckId = unitOfWork.execute(() -> decks.save(
                DeckRules.validateShape(player, DECK_NAME, deckCodes, START))).id();
    }

    @AfterEach
    void cleanUp() {
        for (UUID matchId : savedMatches) {
            jdbc.update("DELETE FROM game_match_card WHERE match_id = ?", matchId);
            jdbc.update("DELETE FROM game_match_location WHERE match_id = ?", matchId);
            jdbc.update("DELETE FROM game_match_player WHERE match_id = ?", matchId);
            jdbc.update("DELETE FROM game_match WHERE id = ?", matchId);
        }
        try {
            TestPlayers.delete(jdbc, players);
        } finally {
            for (Long locationId : savedLocations) {
                jdbc.update("DELETE FROM location WHERE id = ?", locationId);
            }
            new TestCards(jdbc).reset();
        }
    }

    @Test
    @DisplayName("C.10-A1: guarda el agregado completo y lo lee ordenado solo para un participante")
    void savesAndReadsMatchForParticipant() {
        NewMatch initial = newMatch(Long.MIN_VALUE, deckCodes);
        UUID matchId = save(initial);

        Match loaded = matches.findByIdForPlayer(matchId, player).orElseThrow();

        assertThat(loaded.id()).isEqualTo(matchId);
        assertThat(loaded.context()).isEqualTo(MatchContext.RANKED);
        assertThat(loaded.vsBot()).isTrue();
        assertThat(loaded.status()).isEqualTo(MatchStatus.ACTIVE);
        assertThat(loaded.seed()).isEqualTo(Long.MIN_VALUE);
        assertThat(loaded.bet()).isEqualTo(4);
        assertThat(loaded.turn()).isEqualTo(1);
        assertThat(loaded.turnStartedAt()).isEqualTo(START);
        assertThat(loaded.turnDurationSeconds()).isEqualTo(30);
        assertThat(loaded.createdAt()).isEqualTo(START);
        assertThat(loaded.locations()).extracting(MatchLocation::slot).containsExactly(1, 2, 3);
        assertThat(loaded.participants()).extracting(MatchParticipant::seat).containsExactly(1, 2);
        assertThat(loaded.participants().getFirst().cards())
                .extracting(MatchCard::cardCode)
                .containsExactlyElementsOf(SeededShuffle.shuffle(deckCodes, 101L));
        assertThat(loaded.participants().getFirst().cards())
                .filteredOn(card -> card.zone() == MatchZone.HAND).hasSize(4);
        assertThat(loaded.participants().getFirst().cards())
                .filteredOn(card -> card.zone() == MatchZone.GRAVEYARD).isEmpty();
        assertThat(loaded.cardCatalog()).hasSize(DeckRules.DECK_SIZE);
        assertThat(loaded.cardCatalog().get(deckCodes.getFirst()).fields().name()).isNotBlank();
        UUID stranger = UUID.randomUUID();
        assertThat(matches.findByIdForPlayer(matchId, stranger)).isEmpty();
        assertThat(matches.findByIdForPlayer(UUID.randomUUID(), player)).isEmpty();
        assertThat(activeMatches.findActiveMatchId(player)).contains(matchId);
        assertThat(activeMatches.findActiveMatchId(stranger)).isEmpty();

        jdbc.update("UPDATE game_match_player SET active = false WHERE match_id = ? AND player_id = ?",
                matchId, player);
        assertThat(activeMatches.findActiveMatchId(player)).isEmpty();
    }

    @Test
    @DisplayName("save exige la transacción del llamador y no crea filas si se invoca fuera de ella")
    void saveRequiresCallerTransaction() {
        NewMatch initial = newMatch(918273L, deckCodes);

        assertThatThrownBy(() -> matches.save(initial))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("UnitOfWork");
        assertThat(countMatchRows(918273L)).isZero();
    }

    @Test
    @DisplayName("C.10-R3: un fallo al guardar cartas revierte la partida, los asientos y las ubicaciones")
    void rollsBackWholeAggregateWhenCardInsertFails() {
        List<String> invalidCodes = new ArrayList<>(deckCodes);
        invalidCodes.set(0, "PKA-UNKNOWN-999");
        NewMatch initial = newMatch(918274L, invalidCodes);

        assertThatThrownBy(() -> unitOfWork.execute(() -> matches.save(initial)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(countMatchRows(918274L)).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM game_match_player p
                JOIN game_match m ON m.id = p.match_id WHERE m.seed = ?
                """, Integer.class, 918274L)).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM game_match_location l
                JOIN game_match m ON m.id = l.match_id WHERE m.seed = ?
                """, Integer.class, 918274L)).isZero();
    }

    private UUID save(NewMatch match) {
        UUID id = unitOfWork.execute(() -> matches.save(match));
        savedMatches.add(id);
        return id;
    }

    private NewMatch newMatch(long seed, List<String> codes) {
        List<MatchLocation> matchLocations = new ArrayList<>();
        for (int slot = MatchLocation.FIRST_SLOT; slot <= MatchLocation.LAST_SLOT; slot++) {
            String name = "fase3-match-location-" + slot;
            LocationFields fields = new LocationFields(name, "Descripción " + name, 3, ExtraEnergy.none());
            Location location = Location.create(fields, null, "/images/locations/" + name + "/large.png",
                    "/images/locations/" + name + "/small.png", START);
            long locationId = unitOfWork.execute(() -> locations.save(location));
            savedLocations.add(locationId);
            matchLocations.add(new MatchLocation(slot, new AvailableLocation(locationId, location)));
        }
        List<String> humanDeck = SeededShuffle.shuffle(codes, 101L);
        List<String> botDeck = SeededShuffle.shuffle(codes, 202L);
        MatchParticipant human = new MatchParticipant(1, player, false, deckId, 3, true, matchCards(humanDeck));
        MatchParticipant bot = new MatchParticipant(2, null, true, null, 2, true, matchCards(botDeck));
        return new NewMatch(MatchContext.RANKED, true, MatchStatus.ACTIVE, seed, 4, 1, START, 30, START,
                matchLocations, List.of(human, bot));
    }

    private static List<MatchCard> matchCards(List<String> codes) {
        return IntStream.range(0, codes.size())
                .mapToObj(position -> new MatchCard(position, codes.get(position),
                        position < 4 ? MatchZone.HAND : MatchZone.DECK))
                .toList();
    }

    private int countMatchRows(long seed) {
        return jdbc.queryForObject("SELECT count(*) FROM game_match WHERE seed = ?", Integer.class, seed);
    }
}
