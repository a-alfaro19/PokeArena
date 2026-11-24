package cr.ac.tec.pokearena.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import cr.ac.tec.pokearena.application.dto.CancelMatchSearchCommand;
import cr.ac.tec.pokearena.application.port.UnitOfWork;
import cr.ac.tec.pokearena.application.usecase.CancelMatchSearchUseCase;
import cr.ac.tec.pokearena.domain.error.ErrorType;
import cr.ac.tec.pokearena.domain.error.MatchSearchException;
import cr.ac.tec.pokearena.domain.model.MatchSearchStatus;
import cr.ac.tec.pokearena.domain.repository.MatchSearchRepository;
import cr.ac.tec.pokearena.support.PostgresIntegrationTestBase;

/** C.9-A1. El lado US46 reproduce únicamente la reserva y el cierre de una fila candidata. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CancelVsMatchmakingConcurrencyIT extends PostgresIntegrationTestBase {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private UnitOfWork transactions;
    @Autowired private CancelMatchSearchUseCase cancel;
    @Autowired private MatchSearchRepository searches;

    private final String schema = "race_" + UUID.randomUUID().toString().replace("-", "");
    private final ExecutorService worker = Executors.newSingleThreadExecutor();

    @BeforeAll
    void createSharedFixture() {
        jdbc.execute("CREATE SCHEMA " + schema);
        inFixture(() -> {
            jdbc.execute("CREATE TABLE assigned_match (id uuid PRIMARY KEY)");
            jdbc.execute("""
                    CREATE TABLE match_search (
                        id uuid PRIMARY KEY, player_id uuid NOT NULL, deck_id bigint NOT NULL,
                        context text NOT NULL, status text NOT NULL, elo integer NOT NULL,
                        match_id uuid REFERENCES assigned_match, notice text, not_before timestamptz,
                        created_at timestamptz NOT NULL, last_polled_at timestamptz NOT NULL, closed_at timestamptz)
                    """);
            return null;
        });
    }

    @AfterAll
    void cleanUp() {
        worker.shutdownNow();
        jdbc.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
    }

    @RepeatedTest(50)
    @DisplayName("C.9-A1: mientras la salida conserva el bloqueo, el emparejador salta la búsqueda")
    void cancellationWins() {
        Race race = waitingSearch();
        inFixture(() -> {
            var result = cancel.execute(race.command());
            assertThat(result.status()).isEqualTo(MatchSearchStatus.CANCELLED);
            assertThat(result.matchId()).isNull();
            Future<Boolean> attempted = worker.submit(() -> inFixture(() -> tryMatch(race)));
            assertThat(await(attempted)).isFalse();
            return null;
        });
        assertCommitted(race, MatchSearchStatus.CANCELLED);
    }

    @RepeatedTest(50)
    @DisplayName("C.9-A1: la salida bloqueada recibe el identificador de la partida al confirmarse")
    void matchmakingWins() {
        Race race = waitingSearch();
        var connection = new CompletableFuture<Integer>();
        Future<?> cancellation = inFixture(() -> {
            assertThat(tryMatch(race)).isTrue();
            Future<?> request = worker.submit(() -> assertThatThrownBy(() -> inFixture(() -> {
                connection.complete(jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
                return cancel.execute(race.command());
            })).isInstanceOfSatisfying(MatchSearchException.class, error -> {
                assertThat(error.type()).isEqualTo(ErrorType.SEARCH_ALREADY_MATCHED);
                assertThat(error.getMessage()).contains(race.match.toString());
            }));
            // Confirmar solo cuando PostgreSQL ve que esta transacción bloquea a la salida.
            int pid = await(connection);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (!Boolean.TRUE.equals(jdbc.queryForObject(
                    "SELECT pg_backend_pid() = ANY(pg_blocking_pids(?))", Boolean.class, pid))) {
                assertThat(System.nanoTime()).as("La salida debe esperar el bloqueo").isLessThan(deadline);
            }
            return request;
        });
        await(cancellation);
        assertCommitted(race, MatchSearchStatus.MATCHED);
    }

    private record Race(UUID id, UUID player, UUID match) {
        CancelMatchSearchCommand command() {
            return new CancelMatchSearchCommand(player, id.toString());
        }
    }

    private Race waitingSearch() {
        Race race = new Race(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        jdbc.update("INSERT INTO " + schema + ".match_search "
                + "(id, player_id, deck_id, context, status, elo, created_at, last_polled_at) "
                + "VALUES (?, ?, 7, 'RANKED', 'WAITING', 100, now(), now())", race.id, race.player);
        return race;
    }

    private boolean tryMatch(Race race) {
        var candidate = jdbc.queryForList("""
                SELECT id FROM match_search WHERE id = ? AND status = 'WAITING' FOR UPDATE SKIP LOCKED
                """, UUID.class, race.id);
        if (candidate.isEmpty()) {
            return false;
        }
        jdbc.update("INSERT INTO assigned_match VALUES (?)", race.match);
        searches.markMatched(race.id, race.match, Instant.now());
        return true;
    }

    private void assertCommitted(Race race, MatchSearchStatus expected) {
        inFixture(() -> {
            var stored = searches.lockById(race.id, race.player).orElseThrow();
            boolean matched = expected == MatchSearchStatus.MATCHED;
            assertThat(stored.status()).isEqualTo(expected);
            assertThat(stored.matchId()).isEqualTo(matched ? race.match : null);
            assertThat(jdbc.queryForObject("SELECT EXISTS (SELECT FROM assigned_match WHERE id = ?)",
                    Boolean.class, race.match)).isEqualTo(matched);
            return null;
        });
    }

    private <T> T inFixture(Supplier<T> work) {
        return transactions.execute(() -> {
            jdbc.execute("SET LOCAL search_path TO " + schema);
            jdbc.execute("SET LOCAL statement_timeout TO '15s'");
            return work.get();
        });
    }

    private static <T> T await(Future<T> future) {
        try {
            return future.get(20, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError(interrupted);
        } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException failure) {
            throw new AssertionError(failure);
        }
    }
}
