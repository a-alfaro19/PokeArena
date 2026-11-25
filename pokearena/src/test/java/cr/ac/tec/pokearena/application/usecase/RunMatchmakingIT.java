package cr.ac.tec.pokearena.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import cr.ac.tec.pokearena.application.dto.CancelMatchSearchCommand;
import cr.ac.tec.pokearena.application.dto.MatchRequest;
import cr.ac.tec.pokearena.application.dto.MatchStarted;
import cr.ac.tec.pokearena.application.dto.MatchmakingSettings;
import cr.ac.tec.pokearena.application.port.StartMatch;
import cr.ac.tec.pokearena.application.port.UnitOfWork;
import cr.ac.tec.pokearena.domain.error.ErrorType;
import cr.ac.tec.pokearena.domain.error.LocationsUnavailableException;
import cr.ac.tec.pokearena.domain.error.MatchSearchException;
import cr.ac.tec.pokearena.domain.repository.DeckRepository;
import cr.ac.tec.pokearena.domain.repository.MatchSearchRepository;
import cr.ac.tec.pokearena.support.PostgresIntegrationTestBase;

/** Verifica el emparejador con SQL, bloqueos y transacciones reales antes de integrar la US 48. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "spring.datasource.hikari.connection-init-sql=SET search_path TO us46_matchmaking_fixture, public")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RunMatchmakingIT extends PostgresIntegrationTestBase {
    private static final Instant NOW = Instant.parse("2026-11-24T12:00:00Z");
    private static final Duration INACTIVITY = Duration.ofSeconds(30);
    private static final Duration RETRY_DELAY = Duration.ofSeconds(30);
    private static final Duration POLL_INTERVAL = Duration.ofSeconds(5);

    @Autowired private JdbcTemplate jdbc;
    @Autowired private MatchSearchRepository searches;
    @Autowired private UnitOfWork transactions;
    private final DeckRepository decks = mock(DeckRepository.class);

    @BeforeAll
    void createFixture() {
        // Aislar las tablas de la prueba hasta que V4 y V5 estén disponibles en esta rama.
        jdbc.execute("CREATE SCHEMA us46_matchmaking_fixture");
        jdbc.execute("CREATE TABLE us46_matchmaking_fixture.test_match (id uuid PRIMARY KEY)");
        jdbc.execute("""
                CREATE TABLE us46_matchmaking_fixture.test_match_seat (
                    match_id uuid REFERENCES test_match(id), player_id uuid UNIQUE, deck_id bigint NOT NULL
                )
                """);
        jdbc.execute("""
                CREATE TABLE us46_matchmaking_fixture.match_search (
                    id uuid PRIMARY KEY, player_id uuid NOT NULL, deck_id bigint NOT NULL,
                    context text NOT NULL, status text NOT NULL, elo integer NOT NULL,
                    match_id uuid REFERENCES test_match(id), notice text, not_before timestamptz,
                    created_at timestamptz NOT NULL, last_polled_at timestamptz NOT NULL, closed_at timestamptz
                )
                """);
        when(decks.findCardCodes(anyLong())).thenAnswer(call -> List.of("DECK-" + call.getArgument(0)));
    }

    @AfterAll
    void dropFixture() {
        // Eliminar únicamente el esquema aislado de este conjunto de pruebas.
        jdbc.execute("DROP SCHEMA IF EXISTS us46_matchmaking_fixture CASCADE");
    }

    @BeforeEach
    void clearFixture() {
        // Cada prueba inicia sin esperas ni partidas de la anterior.
        jdbc.execute("TRUNCATE match_search, test_match_seat, test_match");
    }

    @Test
    @DisplayName("C.8-E3 y C.8-E4: una espera sin rival no impide emparejar a los siguientes")
    void advancesPastUnpairedSearch() {
        // Los dos primeros comparten hora. El cursor debe avanzar también mediante el id.
        UUID oldest = waiting(1, 400, NOW.minusSeconds(20), NOW, "RANKED");
        UUID first = waiting(2, 1000, NOW.minusSeconds(20), NOW, "RANKED");
        UUID second = waiting(3, 1190, NOW.minusSeconds(10), NOW, "RANKED");
        UUID stale = waiting(4, 1000, NOW.minusSeconds(40), NOW.minusSeconds(31), "RANKED");
        UUID otherContext = waiting(5, 1000, NOW.minusSeconds(19), NOW, "TOURNAMENT");
        UUID deferred = waiting(6, 1000, NOW.minusSeconds(19), NOW, "RANKED");
        jdbc.update("UPDATE match_search SET not_before = ? WHERE id = ?", Timestamp.from(NOW.plusSeconds(1)), deferred);

        // El límite de inactividad es inclusivo y el reintento ya vencido puede emparejarse.
        jdbc.update("UPDATE match_search SET last_polled_at = ?, not_before = ? WHERE id = ?",
                Timestamp.from(NOW.minus(INACTIVITY)), Timestamp.from(NOW), second);
        run(this::prepare).execute();
        assertThat(status(oldest)).isEqualTo("WAITING");
        assertThat(status(otherContext)).isEqualTo("WAITING");
        assertThat(status(deferred)).isEqualTo("WAITING");
        assertThat(status(stale)).isEqualTo("EXPIRED");
        assertThat(status(first)).isEqualTo("MATCHED");
        assertThat(matchId(first)).isNotNull().isEqualTo(matchId(second));
        assertThat(jdbc.queryForList("SELECT deck_id FROM test_match_seat", Long.class))
                .containsExactlyInAnyOrder(2L, 3L);
    }

    @Test
    @DisplayName("C.8-A3: cuatro emparejadores concurrentes solo asignan una partida por jugador")
    void concurrentWorkersDoNotDuplicateMatches() throws Exception {
        // Lanzar cuatro ciclos juntos contra 50 esperas elegibles del mismo rango.
        for (int id = 1; id <= 50; id++) {
            waiting(id, 1000, NOW, NOW, "RANKED");
        }
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(4)) {
            List<Future<?>> workers = new ArrayList<>();
            for (int worker = 0; worker < 4; worker++) {
                workers.add(executor.submit(() -> {
                    await(start);
                    run(this::prepare).execute();
                }));
            }
            start.countDown();
            for (var worker : workers) {
                worker.get(10, TimeUnit.SECONDS);
            }
        }

        // Completar las filas que un ciclo pudo saltar por estar bloqueadas en otra instancia.
        run(this::prepare).execute();
        assertThat(count("SELECT count(*) FROM test_match")).isEqualTo(25);
        assertThat(count("SELECT count(DISTINCT player_id) FROM test_match_seat")).isEqualTo(50);
        assertThat(count("SELECT count(*) FROM match_search WHERE status = 'MATCHED'")).isEqualTo(50);
        assertThat(count("""
                SELECT count(*) FROM (SELECT match_id FROM match_search GROUP BY match_id HAVING count(*) <> 2) pairs
                """)).isZero();
    }

    @Test
    @DisplayName("C.8-A4: el fallo de ubicaciones revierte la partida y difiere el reintento")
    void rollsBackBeforeNotifying() {
        // Dejar escrituras parciales dentro del preparador para verificar que se revierten.
        UUID first = waiting(1, 1000, NOW, NOW, "RANKED");
        UUID second = waiting(2, 1000, NOW, NOW, "RANKED");
        run(request -> {
            prepare(request);
            throw new LocationsUnavailableException();
        }).execute();

        // El aviso y la demora sobreviven al rollback en una transacción independiente.
        assertThat(count("SELECT count(*) FROM test_match")).isZero();
        assertThat(count("SELECT count(*) FROM test_match_seat")).isZero();
        for (UUID id : List.of(first, second)) {
            assertThat(status(id)).isEqualTo("WAITING");
            assertThat(matchId(id)).isNull();
            assertThat(jdbc.queryForObject("SELECT notice FROM match_search WHERE id = ?", String.class, id))
                    .isEqualTo("LOCATIONS_UNAVAILABLE");
            assertThat(jdbc.queryForObject("SELECT not_before FROM match_search WHERE id = ?", Timestamp.class, id))
                    .isEqualTo(Timestamp.from(NOW.plus(RETRY_DELAY)));
        }
        run(this::prepare).execute();
        assertThat(count("SELECT count(*) FROM test_match")).isZero();
    }

    @Test
    @DisplayName("C.8-A4: un fallo al cerrar la segunda búsqueda revierte también el primer cierre")
    void rollsBackBothClosuresOnUnexpectedFailure() {
        // La restricción rechaza la segunda asignación después de que la primera se escribió.
        UUID first = waiting(1, 1000, NOW, NOW, "RANKED");
        UUID second = waiting(2, 1000, NOW, NOW, "RANKED");
        jdbc.execute("""
                ALTER TABLE match_search ADD CONSTRAINT test_second_failure
                CHECK (deck_id <> 2 OR status <> 'MATCHED')
                """);
        try {
            assertThatThrownBy(() -> run(this::prepare).execute())
                    .isInstanceOf(DataIntegrityViolationException.class);
        } finally {
            jdbc.execute("ALTER TABLE match_search DROP CONSTRAINT test_second_failure");
        }
        assertThat(status(first)).isEqualTo("WAITING");
        assertThat(status(second)).isEqualTo("WAITING");
        assertThat(count("SELECT count(*) FROM test_match")).isZero();
        assertThat(count("SELECT count(*) FROM match_search WHERE notice IS NOT NULL")).isZero();
    }

    @Test
    @DisplayName("C.9-A1: salir mientras se prepara la partida devuelve la asignación confirmada")
    void cancellationRacingWithMatchmakingSeesAssignedMatch() throws Exception {
        // El preparador señala que ambas búsquedas están bloqueadas antes de iniciar la salida.
        UUID first = waiting(1, 1000, NOW, NOW, "RANKED");
        UUID second = waiting(2, 1000, NOW, NOW, "RANKED");
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch cancelling = new CountDownLatch(1);
        var cancel = new CancelMatchSearchUseCase(searches, () -> NOW, transactions, POLL_INTERVAL);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var matchmaking = executor.submit(() -> run(request -> {
                locked.countDown();
                await(release);
                return prepare(request);
            }).execute());
            try {
                await(locked);
                var cancellation = executor.submit(() -> {
                    cancelling.countDown();
                    assertThatThrownBy(() -> cancel.execute(new CancelMatchSearchCommand(player(first), first.toString())))
                            .isInstanceOfSatisfying(MatchSearchException.class, failure -> {
                                assertThat(failure.type()).isEqualTo(ErrorType.SEARCH_ALREADY_MATCHED);
                                assertThat(failure.getMessage()).contains(matchId(first).toString());
                            });
                });
                await(cancelling);
                release.countDown();
                matchmaking.get(10, TimeUnit.SECONDS);
                cancellation.get(10, TimeUnit.SECONDS);
            } finally {
                release.countDown();
            }
        }
        assertThat(matchId(first)).isNotNull().isEqualTo(matchId(second));
    }

    private RunMatchmakingUseCase run(StartMatch start) {
        // Controlar el reloj y el disparo de cada ciclo permite verificar los límites exactos.
        var settings = new MatchmakingSettings(true, Duration.ofSeconds(2), 100, 50, Duration.ofSeconds(10),
                400, INACTIVITY, Duration.ofSeconds(60), POLL_INTERVAL, 20, 50, RETRY_DELAY);
        return new RunMatchmakingUseCase(searches, decks, start, transactions, () -> NOW, settings);
    }

    private MatchStarted prepare(MatchRequest request) {
        // Doblar solo el preparador de US 48, con escrituras reales en la transacción del llamador.
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO test_match (id) VALUES (?)", id);
        for (var seat : List.of(request.first(), request.second())) {
            assertThat(seat.deckCodes()).containsExactly("DECK-" + seat.deckId());
            jdbc.update("INSERT INTO test_match_seat VALUES (?, ?, ?)", id, seat.playerId(), seat.deckId());
        }
        return new MatchStarted(id);
    }

    private UUID waiting(int deck, int elo, Instant created, Instant polled, String context) {
        // Identificadores ordenados para probar el desempate del cursor con fechas iguales.
        UUID id = new UUID(0, deck);
        jdbc.update("""
                INSERT INTO match_search (id, player_id, deck_id, context, status, elo, created_at, last_polled_at)
                VALUES (?, ?, ?, ?, 'WAITING', ?, ?, ?)
                """, id, player(id), deck, context, elo, Timestamp.from(created), Timestamp.from(polled));
        return id;
    }

    private static UUID player(UUID search) {
        return new UUID(1, search.getLeastSignificantBits());
    }

    private String status(UUID id) {
        return jdbc.queryForObject("SELECT status FROM match_search WHERE id = ?", String.class, id);
    }

    private UUID matchId(UUID id) {
        return jdbc.queryForObject("SELECT match_id FROM match_search WHERE id = ?", UUID.class, id);
    }

    private int count(String query) {
        return jdbc.queryForObject(query, Integer.class);
    }

    private static void await(CountDownLatch latch) {
        // Un límite evita que una prueba fallida deje un hilo bloqueado.
        try {
            assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("La prueba fue interrumpida.", failure);
        }
    }
}
