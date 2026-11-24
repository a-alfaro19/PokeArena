package cr.ac.tec.pokearena.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import cr.ac.tec.pokearena.application.port.AuthenticatedUser;
import cr.ac.tec.pokearena.application.port.TokenIssuer;
import cr.ac.tec.pokearena.domain.model.MatchContext;
import cr.ac.tec.pokearena.domain.model.MatchSearch;
import cr.ac.tec.pokearena.domain.model.MatchSearchStatus;
import cr.ac.tec.pokearena.domain.model.Role;
import cr.ac.tec.pokearena.domain.repository.MatchSearchRepository;
import cr.ac.tec.pokearena.support.DeckHttp;
import cr.ac.tec.pokearena.support.DeckHttp.Result;
import cr.ac.tec.pokearena.support.PostgresIntegrationTestBase;

/** Contrato HTTP de consulta y salida con seguridad real. JdbcMatchSearchRepositoryIT prueba la operación SQL. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.matchmaking.client-poll-interval=PT7S")
class MatchSearchControllerIT extends PostgresIntegrationTestBase {

    private static final Instant CREATED = Instant.parse("2020-01-01T00:00:00Z");
    private final UUID id = UUID.randomUUID();
    private final UUID owner = UUID.randomUUID();

    @LocalServerPort private int port;
    @Autowired private TokenIssuer tokens;
    @MockitoBean private MatchSearchRepository searches;

    @ParameterizedTest
    @EnumSource(MatchSearchStatus.class)
    @DisplayName("C.9-E1: el jugador recibe el estado, los tiempos y la partida asignada")
    void returnsState(MatchSearchStatus status) {
        UUID match = status == MatchSearchStatus.MATCHED ? UUID.randomUUID() : null;
        String notice = status == MatchSearchStatus.WAITING ? "LOCATIONS_UNAVAILABLE" : null;
        MatchSearch search = new MatchSearch(id, owner, 7, MatchContext.RANKED, status, 0, match, notice,
                null, CREATED, CREATED, null);
        when(searches.touchAndFind(eq(id), eq(owner), any())).thenReturn(Optional.of(search));
        Result response = get(id.toString(), token(owner, Role.PLAYER));
        assertThat(response.status()).isEqualTo(200);
        var body = response.body();
        assertThat(body.size()).isEqualTo(9);
        assertThat(body.get("id").asString()).isEqualTo(id.toString());
        assertThat(body.get("status").asString()).isEqualTo(status.name());
        assertThat(body.get("deckId").asLong()).isEqualTo(7L);
        assertThat(body.get("context").asString()).isEqualTo("RANKED");
        assertThat(Instant.parse(body.get("createdAt").asString())).isEqualTo(CREATED);
        assertThat(body.get("matchId").asString(null)).isEqualTo(match == null ? null : match.toString());
        assertThat(body.get("notice").asString(null)).isEqualTo(notice);
        assertThat(body.get("botOfferAvailable").asBoolean()).isEqualTo(status == MatchSearchStatus.WAITING);
        assertThat(body.get("pollIntervalSeconds").asInt()).isEqualTo(7);
    }

    @Test
    @DisplayName("C.9-E1: el administrador solo puede consultar búsquedas propias")
    void adminIsAlsoScopedToOwner() {
        MatchSearch search = new MatchSearch(id, owner, 7, MatchContext.RANKED, MatchSearchStatus.WAITING,
                0, null, null, null, CREATED, CREATED, null);
        when(searches.touchAndFind(eq(id), eq(owner), any())).thenReturn(Optional.of(search));
        assertThat(get(id.toString(), token(owner, Role.ADMIN)).status()).isEqualTo(200);
        Result foreign = get(id.toString(), token(UUID.randomUUID(), Role.ADMIN));
        assertThat(foreign.status()).isEqualTo(404);
        assertThat(foreign.body().get("type").asString()).isEqualTo("SEARCH_NOT_FOUND");
    }

    @Test
    @DisplayName("C.9-E1: la sesión se comprueba antes del identificador y un id inválido da 404 al titular")
    void validatesSessionFirst() {
        assertThat(get("invalido", null).status()).isEqualTo(401);
        Result invalid = get("invalido", token(owner, Role.PLAYER));
        assertThat(invalid.status()).isEqualTo(404);
        assertThat(invalid.body().get("type").asString()).isEqualTo("SEARCH_NOT_FOUND");
    }

    @Test
    @DisplayName("C.9-E2: el titular puede salir dos veces sin clave de idempotencia")
    void cancelTwice() {
        MatchSearch waiting = search(MatchSearchStatus.WAITING, null);
        MatchSearch closed = search(MatchSearchStatus.CANCELLED, null);
        when(searches.lockById(id, owner)).thenReturn(Optional.of(waiting)).thenReturn(Optional.of(closed));
        when(searches.markCancelled(eq(id), any())).thenReturn(closed);

        Result first = delete(token(owner, Role.PLAYER));
        Result second = delete(token(owner, Role.PLAYER));
        assertThat(first.status()).isEqualTo(200);
        assertThat(first.body().get("status").asString()).isEqualTo("CANCELLED");
        assertThat(first.body().get("matchId").isNull()).isTrue();
        assertThat(second.status()).isEqualTo(200);
        assertThat(second.body()).isEqualTo(first.body());
    }

    @Test
    @DisplayName("C.9-E2: un administrador puede salir de su búsqueda expirada")
    void cancelExpired() {
        when(searches.lockById(id, owner)).thenReturn(Optional.of(search(MatchSearchStatus.EXPIRED, null)));
        Result response = delete(token(owner, Role.ADMIN));
        assertThat(response.status()).isEqualTo(200);
        assertThat(response.body().get("status").asString()).isEqualTo("EXPIRED");
    }

    @Test
    @DisplayName("C.9-R2: salir después del emparejamiento devuelve 409 con el id de la partida")
    void cancelMatched() {
        UUID match = UUID.randomUUID();
        when(searches.lockById(id, owner)).thenReturn(Optional.of(search(MatchSearchStatus.MATCHED, match)));
        Result response = delete(token(owner, Role.PLAYER));
        assertThat(response.status()).isEqualTo(409);
        assertThat(response.body().get("type").asString()).isEqualTo("SEARCH_ALREADY_MATCHED");
        assertThat(response.body().get("detail").asString()).contains(match.toString());
    }

    @Test
    @DisplayName("Salir exige sesión y oculta la existencia de búsquedas ajenas")
    void cancelRequiresOwner() {
        when(searches.lockById(id, owner)).thenReturn(Optional.of(search(MatchSearchStatus.WAITING, null)));
        assertThat(delete(null).status()).isEqualTo(401);
        Result foreign = delete(token(UUID.randomUUID(), Role.ADMIN));
        assertThat(foreign.status()).isEqualTo(404);
        assertThat(foreign.body().get("type").asString()).isEqualTo("SEARCH_NOT_FOUND");
    }

    private MatchSearch search(MatchSearchStatus status, UUID matchId) {
        return new MatchSearch(id, owner, 7, MatchContext.RANKED, status, 0, matchId, null, null,
                CREATED, CREATED, status == MatchSearchStatus.WAITING ? null : CREATED);
    }

    private Result delete(String bearer) {
        return new DeckHttp(port).delete("/api/v1/busquedas/" + id, bearer);
    }

    private String token(UUID player, Role role) {
        return tokens.issue(new AuthenticatedUser(player, role)).value();
    }

    private Result get(String searchId, String token) {
        return new DeckHttp(port).get("/api/v1/busquedas/" + searchId, token);
    }
}
