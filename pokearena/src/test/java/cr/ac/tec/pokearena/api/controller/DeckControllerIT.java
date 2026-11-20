package cr.ac.tec.pokearena.api.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

import cr.ac.tec.pokearena.application.port.AuthenticatedUser;
import cr.ac.tec.pokearena.application.port.TokenIssuer;
import cr.ac.tec.pokearena.application.port.UnitOfWork;
import cr.ac.tec.pokearena.application.usecase.PlayerAccountService;
import cr.ac.tec.pokearena.domain.error.DeckException;
import cr.ac.tec.pokearena.domain.model.Role;
import cr.ac.tec.pokearena.domain.repository.PlayerCardRepository;
import cr.ac.tec.pokearena.domain.rule.DeckRules;
import cr.ac.tec.pokearena.support.DeckHttp;
import cr.ac.tec.pokearena.support.DeckHttp.Result;
import cr.ac.tec.pokearena.support.PostgresIntegrationTestBase;
import cr.ac.tec.pokearena.support.TestCards;
import cr.ac.tec.pokearena.support.TestPlayers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Propósito: pruebas de punta a punta de la historia 3.1.3 "Crear Deck" por HTTP real, una por criterio de
 * aceptación (Anexo C, C.7), con PostgreSQL de Testcontainers.
 * Responsabilidades: comprobar la respuesta 201 con el deck, la lista paginada y el detalle, cada error con
 * su status, su {@code type} y su {@code field}, que un deck ajeno o inexistente responde igual (404), la
 * autenticación (401) y que una solicitud inválida no deja filas.
 * Referencia: sección 7.1 y 13 del Sprint 3; Anexos A.7 y A.8; criterios C.7-E1, E2, R1, R2, R3, R4 y A2.
 * C.7-A1 (reintento) está en {@code DeckIdempotencyIT} y la parte concurrente de C.7-A2 en
 * {@code DeckConcurrencyIT}.
 *
 * <p>Sobre 401 contra 403: las rutas de decks aceptan los dos roles que existen ({@code PLAYER} y
 * {@code ADMIN}), así que ningún token válido puede recibir 403 aquí. Se prueba que sin token o con uno
 * inválido es 401, y que ambos roles crean decks. La distinción 401/403 ya la cubren
 * {@code AuthorizationIT} y {@code RouteSecurityCoverageIT}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("Crear Deck (3.1.3) de punta a punta")
class DeckControllerIT extends PostgresIntegrationTestBase {

    /** Cartas que posee el jugador: más que un deck, para elegir 12 y tener de sobra. */
    private static final int OWNED_CARDS = DeckRules.DECK_SIZE + 3;

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
    private String notOwnedCard;
    private UUID player;
    private String playerToken;
    private String adminToken;
    private String strangerToken;
    private int keys;

    @BeforeEach
    void setUp() {
        api = new DeckHttp(port);
        TestCards cards = new TestCards(jdbc);
        cards.reset();
        owned = new ArrayList<>();
        for (int i = 0; i < OWNED_CARDS; i++) {
            owned.add(cards.card("COMMON", true, true));
        }
        notOwnedCard = cards.card("COMMON", true, true);

        player = newPlayer();
        DeckHttp.give(unitOfWork, playerCards, player, owned);
        playerToken = tokens.issue(new AuthenticatedUser(player, Role.PLAYER)).value();
        adminToken = tokens.issue(new AuthenticatedUser(player, Role.ADMIN)).value();
        strangerToken = tokens.issue(new AuthenticatedUser(newPlayer(), Role.PLAYER)).value();
    }

    @AfterEach
    void cleanUp() {
        TestPlayers.delete(jdbc, players);
    }

    private UUID newPlayer() {
        UUID id = TestPlayers.create(accounts).id();
        players.add(id);
        return id;
    }

    private List<String> deck() {
        return owned.subList(0, DeckRules.DECK_SIZE);
    }

    private Result create(String bearer, String name, List<String> cards) {
        return api.create(bearer, "clave-" + (++keys), name, cards);
    }

    private static void assertError(Result result, int status, String type, String field) {
        assertThat(result.status()).isEqualTo(status);
        assertThat(result.body().get("type").asString()).isEqualTo(type);
        assertThat(result.body().get("status").asInt()).isEqualTo(status);
        if (field == null) {
            assertThat(result.body().has("field")).isFalse();
        } else {
            assertThat(result.body().get("field").asString()).isEqualTo(field);
        }
    }

    @Nested
    @DisplayName("Éxito")
    class Success {

        @Test
        @DisplayName("C.7-E1: con exactamente 12 cartas distintas de su colección y un nombre, el deck queda guardado (201)")
        void createsDeck() {
            Result result = create(playerToken, "Fuego rápido", deck());

            assertThat(result.status()).isEqualTo(201);
            JsonNode body = result.body();
            long id = body.get("id").asLong();
            assertThat(body.get("name").asString()).isEqualTo("Fuego rápido");
            // createdAt viaja como fecha ISO-8601 y es la misma hora que quedó en la base.
            Instant createdAt = Instant.parse(body.get("createdAt").asString());
            assertThat(createdAt).isEqualTo(jdbc.queryForObject("SELECT created_at FROM deck WHERE id = ?",
                    OffsetDateTime.class, id).toInstant());

            JsonNode cards = body.get("cards");
            assertThat(cards).hasSize(DeckRules.DECK_SIZE);
            List<String> codes = new ArrayList<>();
            cards.forEach(card -> {
                codes.add(card.get("code").asString());
                assertThat(card.get("name").asString()).isNotBlank();
                assertThat(card.get("type").get("code").asString()).isNotBlank();
                assertThat(card.get("type").get("name").asString()).isNotBlank();
                assertThat(card.get("rarity").get("code").asString()).isEqualTo("COMMON");
                assertThat(card.has("cost")).isTrue();
                assertThat(card.has("power")).isTrue();
                assertThat(card.get("imageSmallUrl").asString()).isNotBlank();
            });
            assertThat(codes).isEqualTo(deck().stream().sorted().toList());
            assertThat(jdbc.queryForObject("SELECT count(*) FROM deck_card WHERE deck_id = ?", Integer.class, id))
                    .isEqualTo(DeckRules.DECK_SIZE);
        }

        @Test
        @DisplayName("C.7-E1: un administrador también es jugador y puede crear su deck")
        void adminCreatesDeck() {
            assertThat(create(adminToken, "Del administrador", deck()).status()).isEqualTo(201);
        }

        @Test
        @DisplayName("C.7-E2: el jugador puede tener varios decks, listarlos paginados y consultar el detalle de cada uno")
        void severalDecksListedAndDetailed() {
            List<Long> ids = new ArrayList<>();
            List<JsonNode> created = new ArrayList<>();
            for (String name : List.of("Uno", "Dos", "Tres")) {
                Result result = create(playerToken, name, deck());
                created.add(result.body());
                ids.add(result.body().get("id").asLong());
            }

            Result first = api.get(DeckHttp.DECKS + "?limit=2", playerToken);
            assertThat(first.status()).isEqualTo(200);
            assertThat(first.body().get("limit").asInt()).isEqualTo(2);
            assertThat(first.body().get("items")).hasSize(2);
            String cursor = first.body().get("nextCursor").asString();
            Result second = api.get(DeckHttp.DECKS + "?limit=2&cursor=" + cursor, playerToken);
            assertThat(second.body().get("items")).hasSize(1);
            assertThat(second.body().get("nextCursor").isNull()).isTrue();

            List<Long> listed = new ArrayList<>();
            for (Result page : List.of(first, second)) {
                page.body().get("items").forEach(item -> {
                    listed.add(item.get("id").asLong());
                    assertThat(item.get("size").asInt()).isEqualTo(DeckRules.DECK_SIZE);
                    assertThat(item.get("name").asString()).isNotBlank();
                    assertThat(Instant.parse(item.get("createdAt").asString())).isNotNull();
                });
            }
            assertThat(listed).containsExactlyElementsOf(ids);

            for (int i = 0; i < ids.size(); i++) {
                Result detail = api.get(DeckHttp.DECKS + "/" + ids.get(i), playerToken);
                assertThat(detail.status()).isEqualTo(200);
                assertThat(detail.body()).isEqualTo(created.get(i));
            }
        }

        @Test
        @DisplayName("C.7-E2: sin decks, la lista es 200 con items vacío y sin cursor")
        void emptyList() {
            Result result = api.get(DeckHttp.DECKS, strangerToken);

            assertThat(result.status()).isEqualTo(200);
            assertThat(result.body().get("items")).isEmpty();
            assertThat(result.body().get("nextCursor").isNull()).isTrue();
        }
    }

    @Nested
    @DisplayName("Errores")
    class Errors {

        @Test
        @DisplayName("C.7-R1: un deck con 11 o 13 cartas es 400 INVALID_DECK_SIZE e indica la cantidad actual, sin guardar nada")
        void wrongSize() {
            for (int count : List.of(DeckRules.DECK_SIZE - 1, DeckRules.DECK_SIZE + 1)) {
                Result result = create(playerToken, "Tamaño " + count, owned.subList(0, count));

                assertError(result, 400, "INVALID_DECK_SIZE", DeckException.CARDS_FIELD);
                assertThat(result.body().get("detail").asString()).contains("tiene " + count);
            }
            assertThat(DeckHttp.deckCount(jdbc, player)).isZero();
        }

        @Test
        @DisplayName("C.7-R2: un deck con cartas repetidas es 400 DUPLICATE_DECK_CARDS e indica cuáles")
        void duplicates() {
            List<String> cards = new ArrayList<>(deck());
            cards.set(1, cards.getFirst());

            Result result = create(playerToken, "Repetidas", cards);

            assertError(result, 400, "DUPLICATE_DECK_CARDS", DeckException.CARDS_FIELD);
            assertThat(result.body().get("detail").asString()).contains(cards.getFirst());
            assertThat(DeckHttp.deckCount(jdbc, player)).isZero();
        }

        @Test
        @DisplayName("C.7-R3: un deck con una carta que el jugador no posee es 400 CARD_NOT_OWNED e indica cuál")
        void notOwned() {
            List<String> cards = new ArrayList<>(deck());
            cards.set(0, notOwnedCard);

            Result result = create(playerToken, "Ajena", cards);

            assertError(result, 400, "CARD_NOT_OWNED", DeckException.CARDS_FIELD);
            assertThat(result.body().get("detail").asString()).contains(notOwnedCard);
            assertThat(DeckHttp.deckCount(jdbc, player)).isZero();
        }

        @Test
        @DisplayName("C.7-R3: una carta inexistente tampoco está en la colección: 400 CARD_NOT_OWNED")
        void nonexistentCard() {
            List<String> cards = new ArrayList<>(deck());
            cards.set(0, "PKA-LUC-9999");

            Result result = create(playerToken, "Inexistente", cards);

            assertError(result, 400, "CARD_NOT_OWNED", DeckException.CARDS_FIELD);
            assertThat(result.body().get("detail").asString()).contains("PKA-LUC-9999");
        }

        @Test
        @DisplayName("C.7-R4: un deck de otro jugador y uno inexistente responden igual: 404 DECK_NOT_FOUND con el mismo cuerpo")
        void foreignDeckIsNotFound() {
            long id = create(playerToken, "Mío", deck()).body().get("id").asLong();

            Result foreign = api.get(DeckHttp.DECKS + "/" + id, strangerToken);
            Result missing = api.get(DeckHttp.DECKS + "/" + (id + 1_000), strangerToken);
            Result notANumber = api.get(DeckHttp.DECKS + "/abc", strangerToken);

            for (Result result : List.of(foreign, missing, notANumber)) {
                assertError(result, 404, "DECK_NOT_FOUND", null);
            }
            assertThat(withoutCorrelation(foreign.body())).isEqualTo(withoutCorrelation(missing.body()));
            assertThat(withoutCorrelation(foreign.body())).isEqualTo(withoutCorrelation(notANumber.body()));
        }

        @Test
        @DisplayName("C.7-R4: la lista de otro jugador no incluye mis decks")
        void foreignListIsEmpty() {
            create(playerToken, "Mío", deck());

            assertThat(api.get(DeckHttp.DECKS, strangerToken).body().get("items")).isEmpty();
        }

        @Test
        @DisplayName("5.2: sin token o con un token inválido, las tres operaciones responden 401 NOT_AUTHENTICATED")
        void notAuthenticated() {
            for (String bearer : new String[] {null, "token-que-no-es-valido"}) {
                assertError(api.create(bearer, "clave", "Fuego", deck()), 401, "NOT_AUTHENTICATED", null);
                assertError(api.get(DeckHttp.DECKS, bearer), 401, "NOT_AUTHENTICATED", null);
                assertError(api.get(DeckHttp.DECKS + "/1", bearer), 401, "NOT_AUTHENTICATED", null);
            }
        }

        @Test
        @DisplayName("Sin nombre es 400 MISSING_FIELD en name; un nombre de 41 caracteres es INVALID_FIELD con [\"1..40\"]")
        void invalidName() {
            assertError(create(playerToken, " ", deck()), 400, "MISSING_FIELD", DeckException.NAME_FIELD);

            Result tooLong = create(playerToken, "a".repeat(DeckRules.NAME_MAX_LENGTH + 1), deck());
            assertError(tooLong, 400, "INVALID_FIELD", DeckException.NAME_FIELD);
            assertThat(tooLong.body().get("allowedValues").get(0).asString())
                    .isEqualTo(DeckRules.NAME_MIN_LENGTH + ".." + DeckRules.NAME_MAX_LENGTH);
        }

        @Test
        @DisplayName("Sin lista de cartas es 400 MISSING_FIELD en cards")
        void missingCards() {
            assertError(create(playerToken, "Sin cartas", null), 400, "MISSING_FIELD", DeckException.CARDS_FIELD);
        }

        @Test
        @DisplayName("5.5: un limit inválido en la lista es 400 INVALID_PAGINATION")
        void invalidPagination() {
            assertError(api.get(DeckHttp.DECKS + "?limit=0", playerToken), 400, "INVALID_PAGINATION", "limit");
        }

        @Test
        @DisplayName("6.1: ningún camino deja un deck con otra cantidad de cartas que 12")
        void everyStoredDeckHasExactSize() {
            create(playerToken, "Válido", deck());
            create(playerToken, "Corto", owned.subList(0, DeckRules.DECK_SIZE - 1));
            create(playerToken, "Largo", owned.subList(0, DeckRules.DECK_SIZE + 1));

            assertThat(jdbc.queryForList("""
                    SELECT count(dc.card_code) FROM deck d LEFT JOIN deck_card dc ON dc.deck_id = d.id
                    WHERE d.player_id = ? GROUP BY d.id
                    """, Integer.class, player)).containsExactly(DeckRules.DECK_SIZE);
        }
    }

    @Nested
    @DisplayName("Alternos")
    class Alternates {

        @Test
        @DisplayName("C.7-A2: un nombre repetido entre mis decks (sin importar mayúsculas ni tildes) es 409 DECK_NAME_TAKEN")
        void nameTaken() {
            assertThat(create(playerToken, "Fuego Rápido", deck()).status()).isEqualTo(201);

            Result repeated = create(playerToken, "FUEGO RAPIDO", deck());

            assertError(repeated, 409, "DECK_NAME_TAKEN", DeckException.NAME_FIELD);
            assertThat(repeated.body().get("detail").asString()).contains("FUEGO RAPIDO");
            assertThat(DeckHttp.deckCount(jdbc, player)).isEqualTo(1);
        }

        @Test
        @DisplayName("C.7-A2: el mismo nombre en decks de jugadores distintos no es un conflicto")
        void sameNameOtherPlayer() {
            UUID other = newPlayer();
            DeckHttp.give(unitOfWork, playerCards, other, deck());
            String otherToken = tokens.issue(new AuthenticatedUser(other, Role.PLAYER)).value();

            assertThat(create(playerToken, "Fuego", deck()).status()).isEqualTo(201);
            assertThat(create(otherToken, "Fuego", deck()).status()).isEqualTo(201);
        }
    }

    /** El cuerpo de error sin {@code correlationId}, que es distinto en cada solicitud. */
    private static JsonNode withoutCorrelation(JsonNode body) {
        ObjectNode copy = (ObjectNode) body.deepCopy();
        copy.remove("correlationId");
        return copy;
    }
}
