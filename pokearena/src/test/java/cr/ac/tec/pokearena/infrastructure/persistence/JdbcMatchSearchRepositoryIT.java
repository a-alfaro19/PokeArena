package cr.ac.tec.pokearena.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import cr.ac.tec.pokearena.domain.model.MatchSearch;
import cr.ac.tec.pokearena.domain.model.MatchSearchStatus;
import cr.ac.tec.pokearena.domain.repository.MatchSearchRepository;
import cr.ac.tec.pokearena.support.PostgresIntegrationTestBase;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "app.matchmaking.inactivity-timeout=PT30S")
@Transactional
class JdbcMatchSearchRepositoryIT extends PostgresIntegrationTestBase {

    private static final Instant START = Instant.parse("2026-11-23T12:00:00Z");
    private final UUID id = UUID.randomUUID();
    private final UUID player = UUID.randomUUID();

    @Autowired private JdbcTemplate jdbc;
    @Autowired private MatchSearchRepository searches;

    @BeforeEach
    void waitingSearch() {
        // Fixture temporal de las columnas de 6.3. Solo existe en la conexión de esta prueba.
        jdbc.execute("""
                CREATE TEMP TABLE match_search (
                    id uuid PRIMARY KEY, player_id uuid NOT NULL, deck_id bigint NOT NULL,
                    context text NOT NULL, status text NOT NULL, elo integer NOT NULL, match_id uuid,
                    notice text, not_before timestamptz, created_at timestamptz NOT NULL,
                    last_polled_at timestamptz NOT NULL, closed_at timestamptz
                ) ON COMMIT DROP
                """);
        jdbc.update("""
                INSERT INTO match_search (id, player_id, deck_id, context, status, elo, created_at, last_polled_at)
                VALUES (?, ?, 7, 'RANKED', 'WAITING', 100, ?, ?)
                """, id, player, Timestamp.from(START), Timestamp.from(START));
    }

    @Test
    @DisplayName("C.9-R1: el barrido expira solo las esperas anteriores al corte y conserva su última actividad")
    void expireOnlyOlderWaitingRows() {
        Instant cutoff = START.plusNanos(1000);
        Instant now = cutoff.plusSeconds(30);
        UUID atBoundary = insertWaitingAt(cutoff);
        UUID recent = insertWaitingAt(cutoff.plusSeconds(1));
        assertThat(searches.expireStale(cutoff, now)).isOne();
        MatchSearch expired = searches.lockById(id, player).orElseThrow();
        assertThat(expired.status()).isEqualTo(MatchSearchStatus.EXPIRED);
        assertThat(expired.closedAt()).isEqualTo(now);
        assertThat(expired.lastPolledAt()).isEqualTo(START);
        assertThat(expired.matchId()).isNull();
        assertThat(jdbc.queryForList("SELECT id FROM match_search WHERE status = 'WAITING'", UUID.class))
                .containsExactlyInAnyOrder(atBoundary, recent);
        assertThat(searches.expireStale(cutoff, now.plusSeconds(1))).isZero();
        assertThat(searches.lockById(id, player)).contains(expired);
    }

    private UUID insertWaitingAt(Instant lastPoll) {
        UUID searchId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO match_search (id, player_id, deck_id, context, status, elo, created_at, last_polled_at)
                VALUES (?, ?, 7, 'RANKED', 'WAITING', 100, ?, ?)
                """, searchId, UUID.randomUUID(), Timestamp.from(START), Timestamp.from(lastPoll));
        return searchId;
    }

    @ParameterizedTest
    @EnumSource(value = MatchSearchStatus.class, names = "WAITING", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("C.9-R1: el barrido preserva todas las búsquedas ya cerradas")
    void sweepPreservesClosedSearch(MatchSearchStatus status) {
        UUID match = status == MatchSearchStatus.MATCHED ? UUID.randomUUID() : null;
        jdbc.update("UPDATE match_search SET status = ?, match_id = ?, closed_at = ? WHERE id = ?",
                status.name(), match, Timestamp.from(START), id);
        MatchSearch original = searches.lockById(id, player).orElseThrow();
        assertThat(searches.expireStale(START.plusSeconds(1), START.plusSeconds(31))).isZero();
        assertThat(searches.lockById(id, player)).contains(original);
    }

    @Test
    @DisplayName("C.9-E2: bloquear y cancelar conserva el deck y la actividad, y guarda la hora de salida")
    void cancelOwnedWaitingSearch() {
        Instant exitTime = START.plusSeconds(7);
        MatchSearch before = searches.lockById(id, player).orElseThrow();
        assertThat(before.status()).isEqualTo(MatchSearchStatus.WAITING);
        MatchSearch after = searches.markCancelled(id, exitTime);
        assertThat(after.status()).isEqualTo(MatchSearchStatus.CANCELLED);
        assertThat(after.closedAt()).isEqualTo(exitTime);
        assertThat(after.matchId()).isNull();
        assertThat(after.deckId()).isEqualTo(before.deckId());
        assertThat(after.lastPolledAt()).isEqualTo(before.lastPolledAt());
        assertThat(searches.lockById(id, player)).contains(after);
    }

    @Test
    @DisplayName("C.9-E2: una búsqueda ajena o inexistente no se entrega para cancelar")
    void onlyOwnerCanLockSearch() {
        assertThat(searches.lockById(id, UUID.randomUUID())).isEmpty();
        assertThat(searches.lockById(UUID.randomUUID(), player)).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(value = MatchSearchStatus.class, names = "WAITING", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("C.9-R2 y C.9-E2: la escritura de cancelación protege las búsquedas ya cerradas")
    void cancellationCannotOverwriteClosedState(MatchSearchStatus status) {
        UUID match = status == MatchSearchStatus.MATCHED ? UUID.randomUUID() : null;
        jdbc.update("UPDATE match_search SET status = ?, match_id = ?, closed_at = ? WHERE id = ?",
                status.name(), match, Timestamp.from(START), id);
        MatchSearch before = searches.lockById(id, player).orElseThrow();
        assertThatThrownBy(() -> searches.markCancelled(id, START.plusSeconds(1)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(searches.lockById(id, player)).contains(before);
    }

    @Test
    @DisplayName("C.9-R1: en el límite exacto se renueva la espera y una petición anterior no atrasa la actividad")
    void renewAtBoundary() {
        Instant boundary = START.plusSeconds(30);
        MatchSearch result = searches.touchAndFind(id, player, boundary).orElseThrow();
        assertThat(result.status()).isEqualTo(MatchSearchStatus.WAITING);
        assertThat(result.createdAt()).isEqualTo(START);
        assertThat(result.lastPolledAt()).isEqualTo(boundary);
        assertThat(searches.touchAndFind(id, player, START.plusSeconds(29)).orElseThrow()).isEqualTo(result);
    }

    @Test
    @DisplayName("C.9-R1: una consulta tardía expira la espera sin renovar su última actividad")
    void expireLatePoll() {
        Instant late = START.plusSeconds(30).plusNanos(1000);
        MatchSearch result = searches.touchAndFind(id, player, late).orElseThrow();
        assertThat(result.status()).isEqualTo(MatchSearchStatus.EXPIRED);
        assertThat(result.lastPolledAt()).isEqualTo(START);
        assertThat(result.closedAt()).isEqualTo(late);
        assertThat(searches.touchAndFind(id, player, late.plusSeconds(1)).orElseThrow()).isEqualTo(result);
    }

    @Test
    @DisplayName("C.9-E1: una búsqueda inexistente o ajena no devuelve datos ni registra actividad")
    void onlyOwnerMayPoll() {
        assertThat(searches.touchAndFind(UUID.randomUUID(), player, START)).isEmpty();
        assertThat(searches.touchAndFind(id, UUID.randomUUID(), START.plusSeconds(31))).isEmpty();
        assertThat(jdbc.queryForObject("SELECT status FROM match_search WHERE id = ?", String.class, id))
                .isEqualTo("WAITING");
        assertThat(jdbc.queryForObject("SELECT last_polled_at FROM match_search WHERE id = ?", Timestamp.class, id))
                .isEqualTo(Timestamp.from(START));
    }

    @ParameterizedTest
    @EnumSource(value = MatchSearchStatus.class, names = "WAITING", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("C.9-E1: consultar una búsqueda cerrada conserva su estado y la partida asignada")
    void closedSearchIsStable(MatchSearchStatus status) {
        UUID match = status == MatchSearchStatus.MATCHED ? UUID.randomUUID() : null;
        jdbc.update("UPDATE match_search SET status = ?, match_id = ?, closed_at = ? WHERE id = ?",
                status.name(), match, Timestamp.from(START), id);
        MatchSearch result = searches.touchAndFind(id, player, START.plusSeconds(31)).orElseThrow();
        assertThat(result.status()).isEqualTo(status);
        assertThat(result.matchId()).isEqualTo(match);
        assertThat(result.lastPolledAt()).isEqualTo(START);
        assertThat(result.closedAt()).isEqualTo(START);
    }
}
