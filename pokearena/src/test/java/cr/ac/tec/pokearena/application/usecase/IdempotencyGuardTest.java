package cr.ac.tec.pokearena.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import cr.ac.tec.pokearena.application.dto.IdempotentRequest;
import cr.ac.tec.pokearena.application.dto.IdempotentResult;
import cr.ac.tec.pokearena.application.port.IdempotencyKey;
import cr.ac.tec.pokearena.application.port.IdempotencyStore;
import cr.ac.tec.pokearena.application.port.ResponseSerializer;
import cr.ac.tec.pokearena.application.port.StoredResponse;
import cr.ac.tec.pokearena.domain.error.ErrorType;
import cr.ac.tec.pokearena.domain.error.IdempotencyConflictException;
import cr.ac.tec.pokearena.domain.error.IdempotencyException;

/**
 * Propósito: pruebas unitarias de {@link IdempotencyGuard} (sección 5.4).
 * Responsabilidades: comprobar las tres operaciones (consultar, registrar y resolver un conflicto)
 * sin Spring ni base de datos: un almacén en memoria con la misma unicidad que la tabla.
 * Referencia: sección 5.4.
 */
@DisplayName("Guardia de idempotencia")
class IdempotencyGuardTest {

    private static final int CREATED = 201;
    private static final IdempotencyKey KEY = new IdempotencyKey("clave-1", UUID.randomUUID(), "POST /api/v1/cartas");

    private final InMemoryStore store = new InMemoryStore();
    private final IdempotencyGuard guard = new IdempotencyGuard(store, new TextSerializer());

    private static IdempotentRequest request(String hash) {
        return new IdempotentRequest(KEY, hash, CREATED);
    }

    @Test
    @DisplayName("Una clave nueva no tiene respuesta guardada")
    void newKeyHasNoPreviousResponse() {
        assertThat(guard.findPrevious(request("hash-a"), String.class)).isEmpty();
    }

    @Test
    @DisplayName("Registrar guarda el hash, el status de éxito y el cuerpo serializado")
    void recordStoresResponse() {
        guard.record(request("hash-a"), "ficha");

        assertThat(store.responses.get(KEY)).isEqualTo(new StoredResponse("hash-a", CREATED, "ficha"));
    }

    @Test
    @DisplayName("Con la misma clave y el mismo contenido devuelve la respuesta guardada, marcada como repetida")
    void sameContentReplaysStoredResponse() {
        guard.record(request("hash-a"), "ficha");

        Optional<IdempotentResult<String>> previous = guard.findPrevious(request("hash-a"), String.class);

        assertThat(previous).contains(new IdempotentResult<>(CREATED, "ficha", true));
    }

    @Test
    @DisplayName("Con la misma clave y otro contenido lanza IDEMPOTENCY_KEY_REUSED")
    void otherContentIsRejected() {
        guard.record(request("hash-a"), "ficha");

        assertThatThrownBy(() -> guard.findPrevious(request("hash-b"), String.class))
                .isInstanceOfSatisfying(IdempotencyException.class,
                        error -> assertThat(error.type()).isEqualTo(ErrorType.IDEMPOTENCY_KEY_REUSED));
    }

    @Test
    @DisplayName("Registrar una clave que ya existe propaga el conflicto para que el caso de uso lo resuelva")
    void recordingTwicePropagatesConflict() {
        guard.record(request("hash-a"), "ficha");

        assertThatThrownBy(() -> guard.record(request("hash-a"), "otra ficha"))
                .isInstanceOf(IdempotencyConflictException.class);
    }

    @Test
    @DisplayName("Resolver un conflicto devuelve la respuesta de la solicitud que ganó")
    void resolveConflictReturnsWinnerResponse() {
        guard.record(request("hash-a"), "ficha ganadora");

        assertThat(guard.resolveConflict(request("hash-a"), String.class))
                .isEqualTo(new IdempotentResult<>(CREATED, "ficha ganadora", true));
    }

    @Test
    @DisplayName("Resolver un conflicto con otro contenido lanza IDEMPOTENCY_KEY_REUSED")
    void resolveConflictWithOtherContentIsRejected() {
        guard.record(request("hash-a"), "ficha ganadora");

        assertThatThrownBy(() -> guard.resolveConflict(request("hash-b"), String.class))
                .isInstanceOf(IdempotencyException.class);
    }

    @Test
    @DisplayName("Resolver un conflicto sin respuesta guardada es un error de programación, no un replay vacío")
    void resolveConflictWithoutResponseFails() {
        assertThatThrownBy(() -> guard.resolveConflict(request("hash-a"), String.class))
                .isInstanceOf(IllegalStateException.class);
    }

    /** Almacén en memoria: una respuesta por clave, como la clave primaria de la tabla. */
    private static final class InMemoryStore implements IdempotencyStore {

        private final Map<IdempotencyKey, StoredResponse> responses = new HashMap<>();

        @Override
        public Optional<StoredResponse> find(IdempotencyKey key) {
            return Optional.ofNullable(responses.get(key));
        }

        @Override
        public void save(IdempotencyKey key, StoredResponse response) {
            if (responses.putIfAbsent(key, response) != null) {
                throw new IdempotencyConflictException("Clave repetida " + key, null);
            }
        }
    }

    /** Serializador de prueba: el resultado ya es texto. */
    private static final class TextSerializer implements ResponseSerializer {

        @Override
        public String serialize(Object value) {
            return String.valueOf(value);
        }

        @Override
        public <T> T deserialize(String text, Class<T> type) {
            return type.cast(text);
        }
    }
}
