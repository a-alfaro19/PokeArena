package cr.ac.tec.pokearena.api.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

import cr.ac.tec.pokearena.application.port.AuthenticatedUser;
import cr.ac.tec.pokearena.application.port.TokenIssuer;
import cr.ac.tec.pokearena.application.port.UnitOfWork;
import cr.ac.tec.pokearena.application.usecase.PlayerAccountService;
import cr.ac.tec.pokearena.domain.model.Role;
import cr.ac.tec.pokearena.domain.repository.PlayerCardRepository;
import cr.ac.tec.pokearena.domain.rule.DeckRules;
import cr.ac.tec.pokearena.support.DeckHttp;
import cr.ac.tec.pokearena.support.DeckHttp.Result;
import cr.ac.tec.pokearena.support.PostgresIntegrationTestBase;
import cr.ac.tec.pokearena.support.TestCards;
import cr.ac.tec.pokearena.support.TestPlayers;

/**
 * Propósito: idempotencia de Crear deck por HTTP real (sección 5.4; criterio C.7-A1).
 * Responsabilidades: comprobar que un reintento con la misma {@code Idempotency-Key} no crea otro deck,
 * también cuando dos envíos idénticos llegan a la vez; que la misma clave con otro contenido se rechaza; que
 * el orden de las cartas no cambia la huella; que un 409 por nombre no se guarda como respuesta (el reintento
 * se vuelve a ejecutar); y que la clave es obligatoria.
 * Referencia: sección 5.4 y 7.1 del Sprint 3; Anexo A.7, mensajes 10 a 13 y 21 a 25; criterio C.7-A1.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("Crear Deck (3.1.3): idempotencia")
class DeckIdempotencyIT extends PostgresIntegrationTestBase {

    /** Repeticiones de la carrera de envíos idénticos, para que no pase por casualidad. */
    private static final int RACE_REPETITIONS = 5;

    /** Caracteres del UUID que se usan para que cada nombre sea único sin pasar el largo máximo. */
    private static final int SUFFIX_LENGTH = 12;

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
    private PlayerCardRepository playerCards;

    private final List<UUID> players = new ArrayList<>();
    private DeckHttp api;
    private List<String> owned;
    private UUID player;
    private String token;

    @BeforeEach
    void setUp() {
        api = new DeckHttp(port);
        TestCards cards = new TestCards(jdbc);
        cards.reset();
        owned = new ArrayList<>();
        for (int i = 0; i < DeckRules.DECK_SIZE + 1; i++) {
            owned.add(cards.card("COMMON", true, true));
        }
        player = TestPlayers.create(accounts).id();
        players.add(player);
        DeckHttp.give(unitOfWork, playerCards, player, owned);
        token = tokens.issue(new AuthenticatedUser(player, Role.PLAYER)).value();
    }

    @AfterEach
    void cleanUp() {
        TestPlayers.delete(jdbc, players);
    }

    private List<String> deck() {
        return owned.subList(0, DeckRules.DECK_SIZE);
    }

    @Nested
    @DisplayName("Alternos")
    class Alternates {

        @RepeatedTest(RACE_REPETITIONS)
        @DisplayName("C.7-A1: dos envíos idénticos y simultáneos con la misma clave reciben el mismo 201 y queda un solo deck")
        void identicalConcurrentRequests() {
            String key = "clave-" + UUID.randomUUID();
            // Nombre único y dentro del largo máximo del nombre.
            String name = "Simultáneo " + key.substring(key.length() - SUFFIX_LENGTH);
            Supplier<Result> send = () -> api.create(token, key, name, deck());

            List<Result> results = DeckHttp.concurrently(List.of(send, send));

            assertThat(results).allSatisfy(result -> assertThat(result.status()).isEqualTo(201));
            assertThat(results.get(0).body()).isEqualTo(results.get(1).body());
            assertThat(jdbc.queryForObject("SELECT count(*) FROM deck WHERE player_id = ? AND name = ?",
                    Integer.class, player, name)).isEqualTo(1);
        }

        @Test
        @DisplayName("C.7-A1: un reintento posterior con la misma clave devuelve la misma respuesta, sin otro deck")
        void sequentialRetry() {
            Result first = api.create(token, "clave-1", "Fuego", deck());
            Result retry = api.create(token, "clave-1", "Fuego", deck());

            assertThat(retry.status()).isEqualTo(201);
            assertThat(retry.body()).isEqualTo(first.body());
            assertThat(DeckHttp.deckCount(jdbc, player)).isEqualTo(1);
        }

        @Test
        @DisplayName("5.4: el orden en que se envían las cartas no cambia la huella: mismo 201, sin otro deck")
        void orderDoesNotChangeHash() {
            List<String> reversed = new ArrayList<>(deck());
            Collections.reverse(reversed);

            Result first = api.create(token, "clave-1", "Fuego", deck());
            Result retry = api.create(token, "clave-1", "Fuego", reversed);

            assertThat(retry.status()).isEqualTo(201);
            assertThat(retry.body()).isEqualTo(first.body());
            assertThat(DeckHttp.deckCount(jdbc, player)).isEqualTo(1);
        }

        @Test
        @DisplayName("5.4: claves distintas con el mismo nombre: la primera 201 y la segunda 409 DECK_NAME_TAKEN")
        void distinctKeysSameName() {
            assertThat(api.create(token, "clave-1", "Fuego", deck()).status()).isEqualTo(201);

            Result second = api.create(token, "clave-2", "Fuego", deck());

            assertThat(second.status()).isEqualTo(409);
            assertThat(second.body().get("type").asString()).isEqualTo("DECK_NAME_TAKEN");
            assertThat(DeckHttp.deckCount(jdbc, player)).isEqualTo(1);
        }

        @Test
        @DisplayName("5.4: un 409 no se guarda como respuesta: el reintento con la misma clave se vuelve a ejecutar")
        void conflictIsNotStored() {
            api.create(token, "clave-1", "Fuego", deck());

            // Primer intento y reintento con la misma clave: ambos se ejecutan y chocan con el deck existente.
            for (int attempt = 0; attempt < 2; attempt++) {
                Result conflict = api.create(token, "clave-2", "Fuego", deck());
                assertThat(conflict.status()).isEqualTo(409);
                assertThat(conflict.body().get("type").asString()).isEqualTo("DECK_NAME_TAKEN");
            }
            assertThat(jdbc.queryForObject("SELECT count(*) FROM idempotency_key WHERE user_id = ? AND key = ?",
                    Integer.class, player, "clave-2")).isZero();

            // Si el choque desaparece (aquí se borra el deck en la base), el mismo reintento ya puede crear el deck:
            // prueba que el 409 no quedó guardado como respuesta de la clave.
            jdbc.update("DELETE FROM deck_card WHERE deck_id IN (SELECT id FROM deck WHERE player_id = ?)", player);
            jdbc.update("DELETE FROM deck WHERE player_id = ?", player);
            assertThat(api.create(token, "clave-2", "Fuego", deck()).status()).isEqualTo(201);
        }
    }

    @Nested
    @DisplayName("Errores")
    class Errors {

        @Test
        @DisplayName("5.4: la misma clave con otro contenido (otras cartas) es 422 IDEMPOTENCY_KEY_REUSED, sin otro deck")
        void sameKeyOtherContent() {
            api.create(token, "clave-1", "Fuego", deck());
            List<String> otherCards = owned.subList(1, DeckRules.DECK_SIZE + 1);

            Result reused = api.create(token, "clave-1", "Fuego", otherCards);

            assertThat(reused.status()).isEqualTo(422);
            assertThat(reused.body().get("type").asString()).isEqualTo("IDEMPOTENCY_KEY_REUSED");
            assertThat(reused.body().get("field").asString()).isEqualTo("Idempotency-Key");
            assertThat(DeckHttp.deckCount(jdbc, player)).isEqualTo(1);
        }

        @Test
        @DisplayName("5.4: sin Idempotency-Key es 400 IDEMPOTENCY_KEY_REQUIRED y no se guarda nada")
        void keyRequired() {
            Result result = api.create(token, null, "Fuego", deck());

            assertThat(result.status()).isEqualTo(400);
            assertThat(result.body().get("type").asString()).isEqualTo("IDEMPOTENCY_KEY_REQUIRED");
            assertThat(DeckHttp.deckCount(jdbc, player)).isZero();
        }
    }
}
