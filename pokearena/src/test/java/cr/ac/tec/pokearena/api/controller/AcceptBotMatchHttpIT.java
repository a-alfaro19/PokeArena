package cr.ac.tec.pokearena.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import cr.ac.tec.pokearena.application.dto.MatchStarted;
import cr.ac.tec.pokearena.application.port.AuthenticatedUser;
import cr.ac.tec.pokearena.application.port.Clock;
import cr.ac.tec.pokearena.application.port.StartMatch;
import cr.ac.tec.pokearena.application.port.TokenIssuer;
import cr.ac.tec.pokearena.application.usecase.PlayerAccountService;
import cr.ac.tec.pokearena.domain.error.LocationsUnavailableException;
import cr.ac.tec.pokearena.domain.model.MatchContext;
import cr.ac.tec.pokearena.domain.model.MatchSearch;
import cr.ac.tec.pokearena.domain.model.MatchSearchStatus;
import cr.ac.tec.pokearena.domain.model.Role;
import cr.ac.tec.pokearena.domain.repository.CollectibleCardRepository;
import cr.ac.tec.pokearena.domain.repository.DeckRepository;
import cr.ac.tec.pokearena.domain.repository.MatchSearchRepository;
import cr.ac.tec.pokearena.domain.rule.DeckRules;
import cr.ac.tec.pokearena.support.DeckHttp;
import cr.ac.tec.pokearena.support.DeckHttp.Result;
import cr.ac.tec.pokearena.support.PostgresIntegrationTestBase;
import cr.ac.tec.pokearena.support.TestPlayers;

/** POST real con sesión y almacén idempotente en PostgreSQL. La preparación de A.14 usa un doble. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AcceptBotMatchHttpIT extends PostgresIntegrationTestBase {
    @LocalServerPort private int port;
    @Autowired private PlayerAccountService accounts;
    @Autowired private TokenIssuer tokens;
    @Autowired private Clock clock;
    @Autowired private JdbcTemplate jdbc;
    @MockitoBean private StartMatch start;
    @MockitoBean private MatchSearchRepository searches;
    @MockitoBean private CollectibleCardRepository cards;
    @MockitoBean private DeckRepository decks;

    private final UUID searchId = UUID.randomUUID();
    private final UUID matchId = UUID.randomUUID();
    private UUID owner;
    private String bearer;
    private Instant now;
    private DeckHttp api;

    @BeforeEach
    void setUp() {
        owner = TestPlayers.create(accounts).id();
        bearer = tokens.issue(new AuthenticatedUser(owner, Role.PLAYER)).value();
        now = clock.now();
        api = new DeckHttp(port);
    }

    @AfterEach
    void cleanUp() {
        TestPlayers.delete(jdbc, List.of(owner));
    }

    @Test
    @DisplayName("C.9-E3: el POST crea, repite el 201 y distingue otra búsqueda con la misma clave")
    void replayAndKeyReuse() {
        offerReady();
        when(start.execute(any())).thenReturn(new MatchStarted(matchId));
        Result first = post(searchId, bearer, "misma");
        assertThat(first.status()).isEqualTo(201);
        assertThat(first.body().get("status").asString()).isEqualTo("MATCHED");
        assertThat(first.body().get("matchId").asString()).isEqualTo(matchId.toString());
        Result repeated = post(searchId, bearer, "misma");
        assertThat(repeated.status()).isEqualTo(201);
        assertThat(repeated.body()).isEqualTo(first.body());
        assertError(post(UUID.randomUUID(), bearer, "misma"), 422, "IDEMPOTENCY_KEY_REUSED");
        verify(start, times(1)).execute(any());
    }

    @Test
    @DisplayName("C.9-E3: el POST exige sesión y luego clave, y oculta las búsquedas ajenas")
    void authAndKey() {
        assertError(post(searchId, null, null), 401, "NOT_AUTHENTICATED");
        assertError(post(searchId, bearer, null), 400, "IDEMPOTENCY_KEY_REQUIRED");
        offerReady();
        String foreign = tokens.issue(new AuthenticatedUser(UUID.randomUUID(), Role.ADMIN)).value();
        assertError(post(searchId, foreign, "ajena"), 404, "SEARCH_NOT_FOUND");
    }

    @Test
    @DisplayName("C.9-E3: el dueño con rol ADMIN recibe el conflicto correspondiente al estado")
    void statusConflicts() {
        String admin = tokens.issue(new AuthenticatedUser(owner, Role.ADMIN)).value();
        when(searches.lockById(searchId, owner)).thenReturn(Optional.of(row(MatchSearchStatus.WAITING, now)));
        assertError(post(searchId, admin, "oferta"), 409, "BOT_OFFER_NOT_AVAILABLE");
        when(searches.lockById(searchId, owner)).thenReturn(Optional.of(row(MatchSearchStatus.EXPIRED, now)));
        assertError(post(searchId, admin, "oferta"), 409, "SEARCH_NOT_OPEN");
        when(searches.lockById(searchId, owner)).thenReturn(Optional.of(row(MatchSearchStatus.MATCHED, now)));
        Result matched = post(searchId, admin, "oferta");
        assertError(matched, 409, "SEARCH_ALREADY_MATCHED");
        assertThat(matched.body().get("detail").asString()).contains(matchId.toString());
    }

    @Test
    @DisplayName("C.10-R1: un fallo de preparación permite reintentar con la misma clave")
    void preparationFailureCanRetry() {
        offerReady();
        when(start.execute(any())).thenThrow(new LocationsUnavailableException()).thenReturn(new MatchStarted(matchId));
        assertError(post(searchId, bearer, "reintento"), 409, "MATCH_PREPARATION_FAILED");
        assertThat(post(searchId, bearer, "reintento").status()).isEqualTo(201);
    }

    private void offerReady() {
        List<String> codes = IntStream.range(0, DeckRules.DECK_SIZE).mapToObj(i -> "carta-" + i).toList();
        when(cards.pickRandomCollectible(eq("COMMON"), anyLong(), eq(DeckRules.DECK_SIZE))).thenReturn(codes);
        when(decks.findCardCodes(7)).thenReturn(codes);
        Instant created = now.minusSeconds(65);
        when(searches.lockById(searchId, owner)).thenReturn(Optional.of(row(MatchSearchStatus.WAITING, created)));
        when(searches.markMatched(eq(searchId), eq(matchId), any())).thenReturn(row(MatchSearchStatus.MATCHED, created));
    }

    private MatchSearch row(MatchSearchStatus status, Instant created) {
        return new MatchSearch(searchId, owner, 7, MatchContext.RANKED, status, 0,
                status == MatchSearchStatus.MATCHED ? matchId : null, null, null, created, now,
                status == MatchSearchStatus.WAITING ? null : now);
    }

    private Result post(UUID id, String token, String key) {
        return api.post("/api/v1/busquedas/" + id + "/bot", token, key);
    }

    private static void assertError(Result result, int status, String type) {
        assertThat(result.status()).isEqualTo(status);
        assertThat(result.body().get("type").asString()).isEqualTo(type);
    }
}
