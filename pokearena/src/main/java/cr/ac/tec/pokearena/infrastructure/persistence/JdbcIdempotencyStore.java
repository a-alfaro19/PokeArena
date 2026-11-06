package cr.ac.tec.pokearena.infrastructure.persistence;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import cr.ac.tec.pokearena.application.port.Clock;
import cr.ac.tec.pokearena.application.port.IdempotencyKey;
import cr.ac.tec.pokearena.application.port.IdempotencyStore;
import cr.ac.tec.pokearena.application.port.StoredResponse;
import cr.ac.tec.pokearena.domain.error.IdempotencyConflictException;

/**
 * Propósito: implementación JDBC del almacén de claves de idempotencia (sección 5.4, tabla idempotency_key).
 * Responsabilidades: leer y guardar respuestas por (key, user_id, endpoint), traducir el choque con
 * la clave primaria a {@link IdempotencyConflictException} y tomar created_at del puerto {@link Clock}.
 * Referencia: sección 5.4.
 *
 * <p>Decisiones:
 * <ul>
 *   <li>La unicidad la garantiza la clave primaria (key, user_id, endpoint): si dos transacciones
 *       insertan la misma clave, PostgreSQL hace esperar a la segunda y luego la rechaza. Ese
 *       rechazo ({@code DuplicateKeyException}) se convierte en el conflicto que resuelve la
 *       aplicación; nunca llega al cliente como un 500.</li>
 *   <li>Guardar exige una transacción abierta: la clave debe confirmarse junto con la entidad
 *       creada (sección 5.4). Llamarlo fuera de un UnitOfWork es un error de programación y se
 *       detiene de inmediato, en lugar de guardar una clave que no acompaña a ninguna entidad.</li>
 *   <li>Cualquier otra violación (por ejemplo, un user_id que no existe en player) no es un
 *       conflicto de idempotencia y se propaga tal cual.</li>
 * </ul>
 */
@Repository
public class JdbcIdempotencyStore implements IdempotencyStore {

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public JdbcIdempotencyStore(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    public Optional<StoredResponse> find(IdempotencyKey key) {
        return jdbc.query("""
                SELECT request_hash, response_status, response_body::text AS response_body
                FROM idempotency_key
                WHERE key = ? AND user_id = ? AND endpoint = ?
                """,
                (rs, row) -> new StoredResponse(rs.getString("request_hash"), rs.getInt("response_status"),
                        rs.getString("response_body")),
                key.value(), key.userId(), key.endpoint()).stream().findFirst();
    }

    @Override
    public void save(IdempotencyKey key, StoredResponse response) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException(
                    "La clave de idempotencia se guarda dentro del UnitOfWork que crea la entidad (sección 5.4)");
        }
        try {
            jdbc.update("""
                    INSERT INTO idempotency_key (key, user_id, endpoint, request_hash, response_status,
                                                 response_body, created_at)
                    VALUES (?, ?, ?, ?, ?, ?::jsonb, ?)
                    """,
                    key.value(), key.userId(), key.endpoint(), response.requestHash(), response.status(),
                    response.body(), OffsetDateTime.ofInstant(clock.now(), ZoneOffset.UTC));
        } catch (DuplicateKeyException duplicate) {
            // La transacción de PostgreSQL queda abortada: quien llama debe dejar que el UnitOfWork la revierta.
            throw new IdempotencyConflictException("Otra solicitud ya guardó la clave " + key, duplicate);
        }
    }
}
