package cr.ac.tec.pokearena.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import cr.ac.tec.pokearena.application.port.AuthenticatedUser;
import cr.ac.tec.pokearena.application.port.TokenIssuer;
import cr.ac.tec.pokearena.application.port.UnitOfWork;
import cr.ac.tec.pokearena.application.usecase.PlayerAccountService;
import cr.ac.tec.pokearena.domain.model.Role;
import cr.ac.tec.pokearena.domain.repository.ActiveMatchLookup;
import cr.ac.tec.pokearena.domain.repository.PlayerCardRepository;
import cr.ac.tec.pokearena.domain.rule.DeckRules;
import cr.ac.tec.pokearena.support.DeckHttp;
import cr.ac.tec.pokearena.support.DeckHttp.Result;
import cr.ac.tec.pokearena.support.PostgresIntegrationTestBase;
import cr.ac.tec.pokearena.support.TestCards;
import cr.ac.tec.pokearena.support.TestPlayers;

/** Prueba el alta por HTTP con transacciones y bloqueos reales de PostgreSQL (A.9). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.hikari.connection-init-sql=SET search_path TO us46_http_fixture, public")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StartMatchSearchHttpIT extends PostgresIntegrationTestBase {

    private static final String PATH = "/api/v1/busquedas";
    private static final int MAX_BET = 8;

    @LocalServerPort private int port;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlayerAccountService accounts;
    @Autowired private TokenIssuer tokens;
    @Autowired private UnitOfWork transactions;
    @Autowired private PlayerCardRepository playerCards;
    @MockitoSpyBean private ActiveMatchLookup activeMatches;

    private DeckHttp api;
    private UUID player;
    private String token;
    private List<String> owned;
    private long deckId;

    @BeforeAll
    void createMatchmakingFixture() {
        // Hasta que la US 48 integre V4/V5, estas tablas reproducen las columnas e índices usados por el alta.
        jdbc.execute("CREATE SCHEMA us46_http_fixture");
        jdbc.execute("""
                CREATE TABLE us46_http_fixture.game_match_player (
                    match_id uuid NOT NULL, player_id uuid NOT NULL, active boolean NOT NULL
                )
                """);
        jdbc.execute("""
                CREATE TABLE us46_http_fixture.match_search (
                    id uuid PRIMARY KEY, player_id uuid NOT NULL, deck_id bigint NOT NULL,
                    context text NOT NULL, status text NOT NULL, elo integer NOT NULL, match_id uuid,
                    notice text, not_before timestamptz, created_at timestamptz NOT NULL,
                    last_polled_at timestamptz NOT NULL, closed_at timestamptz
                )
                """);
        jdbc.execute("CREATE UNIQUE INDEX uq_match_search_open ON us46_http_fixture.match_search (player_id)"
                + " WHERE status = 'WAITING'");
    }

    @AfterAll
    void dropMatchmakingFixture() {
        // Liberar las tablas de la prueba antes de devolver la base compartida a la suite.
        jdbc.execute("DROP TABLE IF EXISTS us46_http_fixture.match_search, us46_http_fixture.game_match_player");
        jdbc.execute("DROP SCHEMA IF EXISTS us46_http_fixture");
    }

    @BeforeEach
    void createOwnedDeck() {
        // Usar un deck y una cuenta reales para comprobar titularidad, cartas, Elo y saldo.
        api = new DeckHttp(port);
        var cards = new TestCards(jdbc);
        cards.reset();
        owned = IntStream.range(0, DeckRules.DECK_SIZE)
                .mapToObj(i -> cards.card("COMMON", true, true)).toList();
        player = TestPlayers.create(accounts).id();
        jdbc.update("UPDATE player SET elo = 1450, cube_balance = ? WHERE id = ?", MAX_BET, player);
        DeckHttp.give(transactions, playerCards, player, owned);
        token = tokens.issue(new AuthenticatedUser(player, Role.PLAYER)).value();
        deckId = createDeck("Clasificatorio");
    }

    @AfterEach
    void clearPlayer() {
        // Liberar las referencias del jugador antes de limpiar sus decks y cartas.
        if (player != null) {
            jdbc.update("DELETE FROM match_search WHERE player_id = ?", player);
            jdbc.update("DELETE FROM game_match_player WHERE player_id = ?", player);
            TestPlayers.delete(jdbc, List.of(player));
            player = null;
        }
        new TestCards(jdbc).reset();
    }

    @Test
    @DisplayName("C.8-E1, C.8-E2 y C.8-E5: un deck válido con exactamente ocho cubos abre la búsqueda")
    void createsRankedSearch() {
        // El alta recibe el Elo de la cuenta, registra la actividad y conserva el saldo.
        Result result = start("first", deckId);
        assertThat(result.status()).isEqualTo(201);
        assertThat(result.body().get("status").asString()).isEqualTo("WAITING");
        assertThat(result.body().get("deckId").asLong()).isEqualTo(deckId);
        assertThat(result.body().get("context").asString()).isEqualTo("RANKED");
        assertThat(result.body().get("pollIntervalSeconds").asLong()).isPositive();
        assertThat(jdbc.queryForObject("SELECT elo FROM match_search WHERE player_id = ?", Integer.class, player))
                .isEqualTo(1450);
        assertThat(jdbc.queryForObject("SELECT cube_balance FROM player WHERE id = ?", Long.class, player))
                .isEqualTo(MAX_BET);
    }

    @Test
    @DisplayName("C.8-R1: sesión, clave y campos de alta se validan antes de buscar el deck")
    void validatesRequest() {
        // El JWT y el encabezado son requisitos incluso para una solicitud sin deck.
        assertError(api.post(PATH, null, "key", "{}"), 401, "NOT_AUTHENTICATED");
        assertError(api.post(PATH, token, null, "{}"), 400, "IDEMPOTENCY_KEY_REQUIRED");
        Result missing = api.post(PATH, token, "key", "{}");
        assertError(missing, 400, "MISSING_FIELD");
        assertThat(missing.body().get("field").asString()).isEqualTo("deckId");
        Result context = api.post(PATH, token, "key", body(deckId, "TOURNAMENT"));
        assertError(context, 400, "INVALID_FIELD");
        assertThat(context.body().get("allowedValues").get(0).asString()).isEqualTo("RANKED");
    }

    @Test
    @DisplayName("C.8-R2: un deck ajeno no se distingue de uno inexistente y se comprueba la posesión actual")
    void rejectsUnavailableDecks() {
        // Un token de otro jugador recibe exactamente el mismo detalle que ante un id inexistente.
        String stranger = tokens.issue(new AuthenticatedUser(UUID.randomUUID(), Role.PLAYER)).value();
        Result foreign = api.post(PATH, stranger, "foreign", body(deckId, "RANKED"));
        Result missing = start("missing", Long.MAX_VALUE);
        assertError(foreign, 404, "DECK_NOT_FOUND");
        assertError(missing, 404, "DECK_NOT_FOUND");
        assertThat(foreign.body().get("detail")).isEqualTo(missing.body().get("detail"));

        // Un deck guardado puede quedar inválido si alguna carta deja de pertenecer al jugador.
        String removed = owned.getFirst();
        jdbc.update("DELETE FROM player_card WHERE player_id = ? AND card_code = ?", player, removed);
        Result noLongerOwned = start("unowned", deckId);
        assertError(noLongerOwned, 400, "INVALID_DECK");
        assertThat(noLongerOwned.body().get("detail").asString()).contains(removed);
    }

    @Test
    @DisplayName("C.8-R3: una partida activa impide entrar y el error incluye su identificador")
    void rejectsActiveMatch() {
        // Una partida histórica no bloquea al jugador, pero la activa sí.
        UUID active = UUID.randomUUID();
        jdbc.update("INSERT INTO game_match_player VALUES (?, ?, false), (?, ?, true)",
                UUID.randomUUID(), player, active, player);
        Result result = start("active", deckId);
        assertError(result, 409, "ACTIVE_MATCH_EXISTS");
        assertThat(result.body().get("detail").asString()).contains(active.toString());
    }

    @Test
    @DisplayName("C.8-R3: una asignación que se confirma durante el alta revierte la nueva búsqueda")
    void rejectsMatchCommittedDuringInsert() {
        // Reproducir el cambio de estado entre la validación inicial y la lectura tras el insert.
        UUID match = UUID.randomUUID();
        doReturn(Optional.empty(), Optional.of(match), Optional.empty()).when(activeMatches).findActiveMatchId(player);
        Result result = start("racing-match", deckId);
        assertError(result, 409, "ACTIVE_MATCH_EXISTS");
        assertThat(result.body().get("detail").asString()).contains(match.toString());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM match_search WHERE player_id = ?", Integer.class, player))
                .isZero();
        assertThat(start("racing-match", deckId).status()).isEqualTo(201);
    }

    @Test
    @DisplayName("C.8-R4: siete cubos devuelven 422 y el jugador puede reintentar cuando alcanza el mínimo")
    void rejectsInsufficientCubes() {
        // El fallo de saldo no consume la clave de idempotencia.
        jdbc.update("UPDATE player SET cube_balance = ? WHERE id = ?", MAX_BET - 1, player);
        Result result = start("balance", deckId);
        assertError(result, 422, "INSUFFICIENT_CUBES");
        assertThat(result.body().get("detail").asString()).contains("7", "8", "Compra cubos");
        jdbc.update("UPDATE player SET cube_balance = ? WHERE id = ?", MAX_BET, player);
        assertThat(start("balance", deckId).status()).isEqualTo(201);
    }

    @Test
    @DisplayName("C.8-R5: una búsqueda de torneo impide abrir una clasificatoria")
    void rejectsDifferentContext() {
        // La US 46 aún no abre torneos, por lo que se prepara la espera de otro contexto en PostgreSQL.
        UUID tournament = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO match_search (id, player_id, deck_id, context, status, elo, created_at, last_polled_at)
                VALUES (?, ?, ?, 'TOURNAMENT', 'WAITING', 1450, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, tournament, player, deckId);
        Result result = start("ranked", deckId);
        assertError(result, 409, "SEARCH_ALREADY_OPEN");
        assertThat(result.body().get("detail").asString()).contains(tournament.toString(), Long.toString(deckId));
    }

    @Test
    @DisplayName("C.8-A1 y C.8-A2: reintentar devuelve la búsqueda y cambiar el deck abierto da conflicto")
    void handlesReplaysAndOtherDeck() {
        // La misma clave conserva el 201 y una clave nueva recupera la espera existente con 200.
        long otherDeck = createDeck("Alternativo");
        Result created = start("original", deckId);
        assertThat(created.status()).isEqualTo(201);
        assertThat(start("original", deckId)).isEqualTo(created);
        Result existing = start("new-key", deckId);
        assertThat(existing.status()).isEqualTo(200);
        assertThat(existing.body().get("id")).isEqualTo(created.body().get("id"));
        assertError(start("original", otherDeck), 422, "IDEMPOTENCY_KEY_REUSED");
        assertError(start("other", otherDeck), 409, "SEARCH_ALREADY_OPEN");
    }

    @Test
    @DisplayName("C.8-A1: diez altas concurrentes con la misma clave devuelven un solo 201 persistido")
    void concurrentReplaysKeepOneSearch() {
        // Enviar la misma operación desde diez solicitudes fuerza la resolución después de la carrera.
        List<Supplier<Result>> requests = new ArrayList<>();
        IntStream.range(0, 10).forEach(i -> requests.add(() -> start("shared-key", deckId)));
        List<Result> results = DeckHttp.concurrently(requests);
        assertThat(results).allSatisfy(result -> {
            assertThat(result.status()).isEqualTo(201);
            assertThat(result.body()).isEqualTo(results.getFirst().body());
        });
        assertThat(jdbc.queryForObject("SELECT count(*) FROM match_search WHERE player_id = ?", Integer.class, player))
                .isOne();
    }

    private long createDeck(String name) {
        // Crear el deck por la API garantiza que la búsqueda usa una entrada normal del jugador.
        Result result = api.create(token, name, name, owned);
        assertThat(result.status()).isEqualTo(201);
        return result.body().get("id").asLong();
    }

    private Result start(String key, long selectedDeck) {
        return api.post(PATH, token, key, body(selectedDeck, "RANKED"));
    }

    private static String body(long deckId, String context) {
        return "{\"deckId\":%d,\"context\":\"%s\"}".formatted(deckId, context);
    }

    private static void assertError(Result result, int status, String type) {
        assertThat(result.status()).isEqualTo(status);
        assertThat(result.body().get("type").asString()).isEqualTo(type);
    }
}
