package cr.ac.tec.pokearena.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import cr.ac.tec.pokearena.application.dto.CancelMatchSearchCommand;
import cr.ac.tec.pokearena.application.dto.MatchSearchDetails;
import cr.ac.tec.pokearena.application.port.UnitOfWork;
import cr.ac.tec.pokearena.domain.error.ErrorType;
import cr.ac.tec.pokearena.domain.error.MatchSearchException;
import cr.ac.tec.pokearena.domain.model.MatchContext;
import cr.ac.tec.pokearena.domain.model.MatchSearch;
import cr.ac.tec.pokearena.domain.model.MatchSearchStatus;
import cr.ac.tec.pokearena.domain.repository.MatchSearchRepository;

class CancelMatchSearchUseCaseTest {

    private static final UUID OWNER = new UUID(0, 1);
    private static final UUID ID = new UUID(0, 2);
    private static final UUID MATCH = new UUID(0, 3);
    private static final Instant NOW = Instant.parse("2026-11-23T12:00:00Z");
    private final CancelMatchSearchCommand command = new CancelMatchSearchCommand(OWNER, ID.toString());
    private final FakeStore store = new FakeStore();
    private final CancelMatchSearchUseCase cancel = new CancelMatchSearchUseCase(store, () -> NOW, store,
            Duration.ofSeconds(7));

    @Nested
    @DisplayName("Éxito")
    class Success {
        @Test
        @DisplayName("C.9-E2: cancelar dos veces devuelve el mismo estado con una sola escritura")
        void repeatCancellation() {
            MatchSearchDetails first = cancel.execute(command);
            assertThat(first.status()).isEqualTo(MatchSearchStatus.CANCELLED);
            assertThat(first.matchId()).isNull();
            assertThat(first.botOfferAvailable()).isFalse();
            assertThat(first.pollIntervalSeconds()).isEqualTo(7);
            assertThat(store.row.closedAt()).isEqualTo(NOW);
            assertThat(cancel.execute(command)).isEqualTo(first);
            assertThat(store.writes).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Errores")
    class Errors {
        @Test
        @DisplayName("C.9-R2: el estado bajo bloqueo ya tiene partida y el error identifica cuál")
        void matchAlreadyWon() {
            store.row = row(MatchSearchStatus.MATCHED, MATCH, NOW);
            assertThatThrownBy(() -> cancel.execute(command)).isInstanceOfSatisfying(
                    MatchSearchException.class, error -> {
                        assertThat(error.type()).isEqualTo(ErrorType.SEARCH_ALREADY_MATCHED);
                        assertThat(error.getMessage()).contains(MATCH.toString());
                    });
            assertThat(store.writes).isZero();
        }

        @Test
        @DisplayName("Una búsqueda ajena, inexistente o con id inválido produce SEARCH_NOT_FOUND")
        void missingSearch() {
            for (CancelMatchSearchCommand invalid : new CancelMatchSearchCommand[] {
                    new CancelMatchSearchCommand(MATCH, ID.toString()),
                    new CancelMatchSearchCommand(OWNER, MATCH.toString()),
                    new CancelMatchSearchCommand(OWNER, "invalido") }) {
                assertThatThrownBy(() -> cancel.execute(invalid)).isInstanceOfSatisfying(
                        MatchSearchException.class, e -> assertThat(e.type()).isEqualTo(ErrorType.SEARCH_NOT_FOUND));
            }
            assertThat(store.writes).isZero();
        }
    }

    @Nested
    @DisplayName("Alternos")
    class Alternatives {
        @Test
        @DisplayName("C.9-E2: salir de una búsqueda expirada conserva su estado y su hora de cierre")
        void alreadyExpired() {
            MatchSearch expired = row(MatchSearchStatus.EXPIRED, null, NOW.minusSeconds(3));
            store.row = expired;
            assertThat(cancel.execute(command).status()).isEqualTo(MatchSearchStatus.EXPIRED);
            assertThat(store.row).isEqualTo(expired);
            assertThat(store.writes).isZero();
        }
    }

    private static MatchSearch row(MatchSearchStatus status, UUID match, Instant closed) {
        return new MatchSearch(ID, OWNER, 7, MatchContext.RANKED, status, 0, match, null, null,
                NOW.minusSeconds(70), NOW.minusSeconds(10), closed);
    }

    /** Doble que exige el mismo bloqueo y la misma transacción para leer y escribir. */
    private static final class FakeStore implements MatchSearchRepository, UnitOfWork {
        private MatchSearch row = row(MatchSearchStatus.WAITING, null, null);
        private boolean active;
        private boolean locked;
        private int writes;

        @Override
        public <T> T execute(Supplier<T> work) {
            active = true;
            try {
                return work.get();
            } finally {
                active = false;
                locked = false;
            }
        }

        @Override
        public Optional<MatchSearch> lockById(UUID id, UUID playerId) {
            assertThat(active).isTrue();
            locked = row.id().equals(id) && row.playerId().equals(playerId);
            return locked ? Optional.of(row) : Optional.empty();
        }

        @Override
        public MatchSearch markMatched(UUID id, UUID matchId, Instant now) {
            throw new AssertionError("Una salida no asigna partida");
        }

        @Override
        public MatchSearch markCancelled(UUID id, Instant now) {
            assertThat(active && locked).isTrue();
            assertThat(id).isEqualTo(row.id());
            writes++;
            row = row(MatchSearchStatus.CANCELLED, null, now);
            return row;
        }

        @Override
        public int expireStale(Instant cutoff, Instant now) {
            throw new AssertionError("La salida de un jugador no ejecuta el barrido de inactividad");
        }

        @Override
        public Optional<MatchSearch> touchAndFind(UUID id, UUID playerId, Instant now) {
            throw new AssertionError("Salir de la búsqueda no renueva la actividad");
        }
    }
}
