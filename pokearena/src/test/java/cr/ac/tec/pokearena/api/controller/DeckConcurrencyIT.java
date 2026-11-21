package cr.ac.tec.pokearena.api.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.RepeatedTest;
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
 * Propósito: nombre de deck único bajo concurrencia, por HTTP real (criterio C.7-A2; sección 8).
 * Responsabilidades: comprobar que dos creaciones simultáneas del mismo jugador con el mismo nombre y claves
 * distintas terminan en exactamente un 201 y un 409 {@code DECK_NAME_TAKEN}, con un solo deck guardado.
 * Referencia: sección 7.1 ("el choque se detecta al guardar ... correcto bajo concurrencia") y sección 8
 * ("Dos decks con el mismo nombre a la vez: uno 201 y otro DECK_NAME_TAKEN").
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("Crear Deck (3.1.3): concurrencia")
class DeckConcurrencyIT extends PostgresIntegrationTestBase {

    /** Repeticiones de la carrera, para que el resultado no dependa de un orden afortunado. */
    private static final int REPETITIONS = 10;

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
    private List<String> deck;
    private UUID player;
    private String token;

    @BeforeEach
    void setUp() {
        api = new DeckHttp(port);
        TestCards cards = new TestCards(jdbc);
        cards.reset();
        deck = new ArrayList<>();
        for (int i = 0; i < DeckRules.DECK_SIZE; i++) {
            deck.add(cards.card("COMMON", true, true));
        }
        player = TestPlayers.create(accounts).id();
        players.add(player);
        DeckHttp.give(unitOfWork, playerCards, player, deck);
        token = tokens.issue(new AuthenticatedUser(player, Role.PLAYER)).value();
    }

    @AfterEach
    void cleanUp() {
        try {
            // Primero lo que apunta a las cartas: decks, colecciones y jugadores (TestPlayers.delete).
            TestPlayers.delete(jdbc, players);
        } finally {
            // Después el catálogo sembrado, aunque falle lo anterior: las pruebas comparten la base, y una que
            // no siembra cartas (por ejemplo FlywayMigrationIT) la espera sin las de otras pruebas.
            new TestCards(jdbc).reset();
        }
    }

    @Nested
    @DisplayName("Alternos")
    class Alternates {

        @RepeatedTest(REPETITIONS)
        @DisplayName("C.7-A2: dos creaciones simultáneas con el mismo nombre y claves distintas: una 201 y una 409 DECK_NAME_TAKEN")
        void sameNameAtTheSameTime() {
            String id = UUID.randomUUID().toString();
            // Nombre único y dentro del largo máximo del nombre.
            String name = "Carrera " + id.substring(id.length() - SUFFIX_LENGTH);

            List<Result> results = DeckHttp.concurrently(List.of(
                    () -> api.create(token, "clave-a-" + name, name, deck),
                    () -> api.create(token, "clave-b-" + name, name, deck)));

            assertThat(results).extracting(Result::status).containsExactlyInAnyOrder(201, 409);
            Result conflict = results.stream().filter(result -> result.status() == 409).findFirst().orElseThrow();
            assertThat(conflict.body().get("type").asString()).isEqualTo("DECK_NAME_TAKEN");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM deck WHERE player_id = ? AND name = ?",
                    Integer.class, player, name)).isEqualTo(1);
        }
    }
}
