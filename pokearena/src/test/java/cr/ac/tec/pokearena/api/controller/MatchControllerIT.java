package cr.ac.tec.pokearena.api.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

import cr.ac.tec.pokearena.application.dto.MatchStarted;
import cr.ac.tec.pokearena.application.port.AuthenticatedUser;
import cr.ac.tec.pokearena.application.port.TokenIssuer;
import cr.ac.tec.pokearena.application.port.UnitOfWork;
import cr.ac.tec.pokearena.application.usecase.PlayerAccountService;
import cr.ac.tec.pokearena.application.usecase.StartMatchUseCase;
import cr.ac.tec.pokearena.domain.model.MatchContext;
import cr.ac.tec.pokearena.domain.model.MatchSeat;
import cr.ac.tec.pokearena.domain.model.Role;
import cr.ac.tec.pokearena.domain.repository.DeckRepository;
import cr.ac.tec.pokearena.domain.rule.DeckRules;
import cr.ac.tec.pokearena.domain.rule.SeedMixer;
import cr.ac.tec.pokearena.domain.rule.SeededShuffle;
import cr.ac.tec.pokearena.support.PostgresIntegrationTestBase;
import cr.ac.tec.pokearena.support.TestCards;
import cr.ac.tec.pokearena.support.TestPlayers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Propósito: verificar por HTTP la consulta segura del inicio de una partida.
 * Responsabilidades: probar acceso de jugadores y administradores, privacidad de datos, autorización y el mismo
 * 404 para partidas inexistentes, ajenas o con id inválido.
 * Referencia: sección 7.3; Anexo A.15; criterios C.10-A2 y sección 5.2.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("Consultar el inicio de una partida por HTTP")
class MatchControllerIT extends PostgresIntegrationTestBase {

    private static final Instant CREATED_AT = Instant.parse("2026-11-19T12:00:00Z");
    private static final int CATALOG_SIZE = DeckRules.DECK_SIZE + 1;
    private static final int LOCATION_COUNT = 3;
    private static final int INITIAL_HAND_AND_DRAW_SIZE = 4;
    private static final int INITIAL_ENERGY = 2;
    private static final int INITIAL_TURN = 1;
    private static final int TURN_DURATION_SECONDS = 30;
    private static final int INITIAL_RANKED_BET = 4;
    private static final String MATCH_PATH = "/api/v1/partidas/";

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlayerAccountService accounts;

    @Autowired
    private TokenIssuer tokens;

    @Autowired
    private UnitOfWork unitOfWork;

    @Autowired
    private DeckRepository decks;

    @Autowired
    private StartMatchUseCase startMatch;

    private final HttpClient http = HttpClient.newHttpClient();
    private final JsonMapper json = JsonMapper.builder().build();
    private final List<UUID> players = new ArrayList<>();
    private final List<Long> locations = new ArrayList<>();
    private final List<UUID> matchesToDelete = new ArrayList<>();
    private UUID player;
    private String playerToken;
    private String adminToken;
    private String outsiderToken;
    private String rivalToken;
    private UUID matchId;
    private List<String> deckCodes;

    @BeforeEach
    void setUp() {
        TestCards cards = new TestCards(jdbc);
        cards.reset();
        deckCodes = new ArrayList<>();
        for (int index = 0; index < CATALOG_SIZE; index++) {
            deckCodes.add(cards.card("COMMON", true, true));
        }

        seedLocations();
        player = newPlayer();
        long deckId = unitOfWork.execute(() -> decks
                .save(DeckRules.validateShape(player, "Deck de partida",
                        deckCodes.subList(0, DeckRules.DECK_SIZE), CREATED_AT))
                .id());
        UUID rival = newPlayer();
        long rivalDeckId = unitOfWork.execute(() -> decks
                .save(DeckRules.validateShape(rival, "Deck rival",
                        deckCodes.subList(0, DeckRules.DECK_SIZE), CREATED_AT))
                .id());
        outsiderToken = tokens.issue(new AuthenticatedUser(newPlayer(), Role.PLAYER)).value();
        playerToken = tokens.issue(new AuthenticatedUser(player, Role.PLAYER)).value();
        adminToken = tokens.issue(new AuthenticatedUser(player, Role.ADMIN)).value();
        rivalToken = tokens.issue(new AuthenticatedUser(rival, Role.PLAYER)).value();

        MatchSeat human = new MatchSeat(MatchSeat.FIRST_SEAT, player, deckId,
                deckCodes.subList(0, DeckRules.DECK_SIZE));
        MatchSeat opponent = new MatchSeat(MatchSeat.SECOND_SEAT, rival, rivalDeckId,
                deckCodes.subList(0, DeckRules.DECK_SIZE));
        MatchStarted started = unitOfWork.execute(
                () -> startMatch.execute(new cr.ac.tec.pokearena.application.dto.MatchRequest(MatchContext.RANKED,
                        List.of(human, opponent))));
        matchId = started.id();
        matchesToDelete.add(matchId);
    }

    @AfterEach
    void cleanUp() {
        try {
            for (UUID id : matchesToDelete) {
                jdbc.update("DELETE FROM game_match_card WHERE match_id = ?", id);
                jdbc.update("DELETE FROM game_match_location WHERE match_id = ?", id);
                jdbc.update("DELETE FROM game_match_player WHERE match_id = ?", id);
                jdbc.update("DELETE FROM game_match WHERE id = ?", id);
            }
            locations.forEach(id -> jdbc.update("DELETE FROM location WHERE id = ?", id));
            TestPlayers.delete(jdbc, players);
        } finally {
            new TestCards(jdbc).reset();
        }
    }

    @Test
    @DisplayName("C.10-E1 y A2: ambos jugadores ven las mismas ubicaciones, pero nunca el orden ni la mano rival")
    void returnsPrivateMatchViewToBothPlayersAndAdmin() {
        JsonNode body = get(MATCH_PATH + matchId, playerToken).body();

        assertThat(body.get("id").asString()).isEqualTo(matchId.toString());
        assertThat(body.get("status").asString()).isEqualTo("ACTIVE");
        assertThat(body.get("context").asString()).isEqualTo("RANKED");
        assertThat(body.get("vsBot").asBoolean()).isFalse();
        assertThat(body.get("turn").asInt()).isEqualTo(INITIAL_TURN);
        assertThat(body.get("turnDurationSeconds").asInt()).isEqualTo(TURN_DURATION_SECONDS);
        Instant turnStartedAt = jdbc.queryForObject("SELECT turn_started_at FROM game_match WHERE id = ?",
                OffsetDateTime.class, matchId).toInstant();
        assertThat(Instant.parse(body.get("turnEndsAt").asString()))
                .isEqualTo(turnStartedAt.plusSeconds(TURN_DURATION_SECONDS));
        assertThat(body.get("bet").asInt()).isEqualTo(INITIAL_RANKED_BET);
        assertThat(body.get("locations")).hasSize(LOCATION_COUNT);
        assertThat(locationIds(body.get("locations"))).doesNotHaveDuplicates();
        assertThat(body.get("you").get("seat").asInt()).isEqualTo(MatchSeat.FIRST_SEAT);
        assertThat(body.get("you").get("energy").asInt()).isEqualTo(INITIAL_ENERGY);
        assertThat(body.get("you").get("hand")).hasSize(INITIAL_HAND_AND_DRAW_SIZE);
        assertThat(body.get("you").get("graveyard")).isEmpty();
        assertThat(body.get("you").get("deckRemaining").asInt())
                .isEqualTo(DeckRules.DECK_SIZE - INITIAL_HAND_AND_DRAW_SIZE);
        assertThat(body.get("rival").get("isBot").asBoolean()).isFalse();
        assertThat(body.get("rival").get("handCount").asInt()).isEqualTo(INITIAL_HAND_AND_DRAW_SIZE);
        assertThat(body.get("rival").get("graveyard")).isEmpty();
        assertThat(body.get("rival").has("hand")).isFalse();
        assertThat(body.has("seed")).isFalse();
        assertThat(body.has("deck")).isFalse();
        assertThat(body.get("you").has("deckOrder")).isFalse();

        long seed = jdbc.queryForObject("SELECT seed FROM game_match WHERE id = ?", Long.class, matchId);
        for (int seat : List.of(MatchSeat.FIRST_SEAT, MatchSeat.SECOND_SEAT)) {
            List<String> persistedDeck = jdbc.queryForList("""
                    SELECT card_code FROM game_match_card
                    WHERE match_id = ? AND seat = ? ORDER BY position
                    """, String.class, matchId, seat);
            List<String> expectedOrder = SeededShuffle.shuffle(deckCodes.subList(0, DeckRules.DECK_SIZE),
                    SeedMixer.derive(seed, "seat-%d".formatted(seat)));
            assertThat(persistedDeck).containsExactlyElementsOf(expectedOrder);
        }
        assertThat(cardCodes(body.get("you").get("hand")))
                .containsExactlyElementsOf(SeededShuffle.shuffle(deckCodes.subList(0, DeckRules.DECK_SIZE),
                        SeedMixer.derive(seed, "seat-%d".formatted(MatchSeat.FIRST_SEAT)))
                        .subList(0, INITIAL_HAND_AND_DRAW_SIZE));
        JsonNode rivalView = get(MATCH_PATH + matchId, rivalToken).body();
        assertThat(locationIds(rivalView.get("locations"))).containsExactlyElementsOf(locationIds(body.get("locations")));
        assertThat(rivalView.get("you").get("seat").asInt()).isEqualTo(MatchSeat.SECOND_SEAT);
        assertThat(rivalView.get("you").get("hand")).hasSize(INITIAL_HAND_AND_DRAW_SIZE);
        assertThat(rivalView.get("rival").has("hand")).isFalse();
        assertThat(rivalView.has("seed")).isFalse();
        assertThat(rivalView.get("you").has("deckOrder")).isFalse();
        assertThat(get(MATCH_PATH + matchId, adminToken).statusCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("5.2 y C.10-R2: sin credenciales responde 401 y un id inválido, ajeno o inexistente responde el mismo 404")
    void protectsAndDoesNotRevealForeignMatches() {
        assertThat(get(MATCH_PATH + matchId, null).statusCode()).isEqualTo(401);

        HttpResult foreign = get(MATCH_PATH + matchId, outsiderToken);
        HttpResult missing = get(MATCH_PATH + UUID.randomUUID(), outsiderToken);
        HttpResult invalid = get(MATCH_PATH + "no-es-uuid", outsiderToken);
        for (HttpResult result : List.of(foreign, missing, invalid)) {
            assertThat(result.statusCode()).isEqualTo(404);
            assertThat(result.body().get("type").asString()).isEqualTo("MATCH_NOT_FOUND");
        }
        assertThat(withoutCorrelation(foreign.body())).isEqualTo(withoutCorrelation(missing.body()));
        assertThat(withoutCorrelation(foreign.body())).isEqualTo(withoutCorrelation(invalid.body()));
    }

    private void seedLocations() {
        for (int index = 0; index < LOCATION_COUNT; index++) {
            Long id = jdbc.queryForObject("""
                    INSERT INTO location (name, description, card_limit, extra_energy_mode, extra_energy_amount,
                                          image_large_url, image_small_url, image_status, available, created_at)
                    VALUES (?, 'Ubicación de prueba', 3, 'NONE', 0, '/large.png', '/small.png',
                            'READY', true, ?)
                    RETURNING id
                    """, Long.class, "Ubicación de partida " + index,
                    OffsetDateTime.ofInstant(CREATED_AT, ZoneOffset.UTC));
            locations.add(id);
        }
    }

    private UUID newPlayer() {
        UUID id = TestPlayers.create(accounts).id();
        players.add(id);
        return id;
    }

    private HttpResult get(String path, String bearer) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET();
        if (bearer != null) {
            request.header("Authorization", "Bearer " + bearer);
        }
        try {
            HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
            JsonNode body = response.body().isBlank() ? null : json.readTree(response.body());
            return new HttpResult(response.statusCode(), body);
        } catch (IOException failure) {
            throw new IllegalStateException("No se pudo consultar la API de partidas.", failure);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("La consulta de la API fue interrumpida.", interrupted);
        }
    }

    private static JsonNode withoutCorrelation(JsonNode body) {
        ObjectNode copy = (ObjectNode) body.deepCopy();
        copy.remove("correlationId");
        return copy;
    }

    private static List<Long> locationIds(JsonNode locationNodes) {
        List<Long> ids = new ArrayList<>();
        locationNodes.forEach(location -> ids.add(location.get("id").asLong()));
        return ids;
    }

    private static List<String> cardCodes(JsonNode cardNodes) {
        List<String> codes = new ArrayList<>();
        cardNodes.forEach(card -> codes.add(card.get("code").asString()));
        return codes;
    }

    private record HttpResult(int statusCode, JsonNode body) {
    }
}
