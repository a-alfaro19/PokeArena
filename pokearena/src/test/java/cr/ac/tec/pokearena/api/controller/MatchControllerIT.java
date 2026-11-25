package cr.ac.tec.pokearena.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import cr.ac.tec.pokearena.application.port.AuthenticatedUser;
import cr.ac.tec.pokearena.application.port.TokenIssuer;
import cr.ac.tec.pokearena.domain.model.Role;
import cr.ac.tec.pokearena.domain.repository.MatchRepository;
import cr.ac.tec.pokearena.support.DeckHttp;
import cr.ac.tec.pokearena.support.MatchFixtures;
import cr.ac.tec.pokearena.support.PostgresIntegrationTestBase;

/**
 * Propósito: verificar el endpoint autenticado de lectura inicial de partida.
 * Responsabilidades: cubrir la vista para un participante, el ocultamiento de datos privados y el 404 uniforme.
 * Referencia: sección 7.3; Anexo A.15; contrato OpenAPI.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("Consulta inicial de partida (HTTP)")
class MatchControllerIT extends PostgresIntegrationTestBase {

    private final UUID matchId = UUID.fromString("00000000-0000-0000-0000-000000000201");
    private final UUID owner = UUID.fromString("00000000-0000-0000-0000-000000000202");

    @LocalServerPort
    private int port;

    @Autowired
    private TokenIssuer tokens;

    @MockitoBean
    private MatchRepository matches;

    @Test
    @DisplayName("C.10-A2: devuelve mano y datos de turno sin revelar la mano ni el deck del rival")
    void returnsSafeViewForParticipant() {
        when(matches.findByIdForPlayer(matchId, owner))
                .thenReturn(Optional.of(MatchFixtures.initial(matchId, owner)));

        var response = get(matchId.toString(), token(owner, Role.PLAYER));

        assertThat(response.status()).isEqualTo(200);
        var body = response.body();
        assertThat(body.get("id").asString()).isEqualTo(matchId.toString());
        assertThat(body.get("status").asString()).isEqualTo("ACTIVE");
        assertThat(body.get("context").asString()).isEqualTo("RANKED");
        assertThat(body.get("turn").asInt()).isEqualTo(1);
        assertThat(body.get("turnDurationSeconds").asInt()).isEqualTo(30);
        assertThat(Instant.parse(body.get("turnEndsAt").asString()))
                .isEqualTo(MatchFixtures.STARTED_AT.plusSeconds(30));
        assertThat(Instant.parse(body.get("serverTime").asString())).isNotNull();
        assertThat(body.get("bet").asInt()).isEqualTo(4);
        assertThat(body.get("locations").size()).isEqualTo(3);
        assertThat(body.get("locations").get(0).get("slot").asInt()).isEqualTo(1);
        assertThat(body.get("locations").get(0).get("extraEnergyMode").asString()).isEqualTo("NONE");
        assertThat(body.get("you").get("hand").size()).isEqualTo(4);
        assertThat(body.get("you").get("hand").get(0).get("code").asString()).isEqualTo("PKA-FUE-001");
        assertThat(body.get("you").get("deckRemaining").asInt()).isEqualTo(8);
        assertThat(body.get("you").get("graveyard").isEmpty()).isTrue();
        assertThat(body.get("rival").get("handCount").asInt()).isEqualTo(4);
        assertThat(body.get("rival").get("deckRemaining").asInt()).isEqualTo(8);
        assertThat(body.get("rival").get("graveyard").isEmpty()).isTrue();
        assertThat(body.get("rival").has("hand")).isFalse();
        assertThat(body.has("seed")).isFalse();
        assertThat(body.toString()).doesNotContain("PKA-AGU-001");
        assertThat(body.toString()).doesNotContain("playerId", "deckId", "cardCatalog");
        verify(matches).findByIdForPlayer(matchId, owner);
    }

    @Test
    @DisplayName("C.10-R2: ids inválidos, inexistentes o ajenos responden MATCH_NOT_FOUND")
    void hidesMissingAndForeignMatches() {
        UUID missingId = UUID.randomUUID();
        UUID stranger = UUID.randomUUID();
        var invalid = get("no-es-uuid", token(owner, Role.PLAYER));
        var missing = get(missingId.toString(), token(owner, Role.PLAYER));
        var foreign = get(matchId.toString(), token(stranger, Role.ADMIN));

        assertNotFound(invalid);
        assertNotFound(missing);
        assertNotFound(foreign);
        verify(matches).findByIdForPlayer(missingId, owner);
        verify(matches).findByIdForPlayer(matchId, stranger);
        verifyNoMoreInteractions(matches);
    }

    @Test
    @DisplayName("A.15: la sesión se valida antes de consultar la partida")
    void requiresAuthentication() {
        var response = get(matchId.toString(), null);

        assertThat(response.status()).isEqualTo(401);
        verifyNoInteractions(matches);
    }

    private void assertNotFound(DeckHttp.Result response) {
        assertThat(response.status()).isEqualTo(404);
        assertThat(response.body().get("type").asString()).isEqualTo("MATCH_NOT_FOUND");
    }

    private String token(UUID playerId, Role role) {
        return tokens.issue(new AuthenticatedUser(playerId, role)).value();
    }

    private DeckHttp.Result get(String id, String token) {
        return new DeckHttp(port).get("/api/v1/partidas/" + id, token);
    }
}
