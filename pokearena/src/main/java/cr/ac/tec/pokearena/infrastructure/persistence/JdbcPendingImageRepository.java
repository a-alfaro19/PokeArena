package cr.ac.tec.pokearena.infrastructure.persistence;

import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import cr.ac.tec.pokearena.domain.model.ImageStatus;
import cr.ac.tec.pokearena.domain.model.PendingImage;
import cr.ac.tec.pokearena.domain.repository.PendingImageRepository;
import cr.ac.tec.pokearena.domain.rule.ImageRules;

/**
 * Propósito: implementación JDBC de {@link PendingImageRepository} sobre las tablas card y location.
 * Responsabilidades: seleccionar y bloquear la siguiente entidad pendiente con
 * {@code FOR UPDATE SKIP LOCKED}, y guardar el resultado de cada intento.
 * Referencia: sección 7.1 ("Si falla la miniatura") y sección 9 (bloqueos de fila explícitos).
 *
 * <p>{@code SKIP LOCKED}: si otra instancia ya bloqueó una fila (la está procesando), esta consulta no
 * espera ni la devuelve: toma la siguiente. Por eso exige una transacción abierta, como el consecutivo
 * de {@link JdbcCardRepository}: sin ella, el bloqueo se soltaría al terminar la sentencia.
 *
 * <p>El nombre de la tabla sale de un mapa fijo de esta clase (nunca de un dato externo); los valores
 * van como parámetros.
 */
@Repository
public class JdbcPendingImageRepository implements PendingImageRepository {

    private static final Map<ImageRules.Kind, String> TABLES = Map.of(
            ImageRules.Kind.CARD, "card",
            ImageRules.Kind.LOCATION, "location");

    private final JdbcTemplate jdbc;

    public JdbcPendingImageRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<PendingImage> lockNextPending(ImageRules.Kind kind, int maxRetries, long afterId) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("La entidad pendiente se bloquea dentro del UnitOfWork del intento");
        }
        return jdbc.query("SELECT id, image_large_url, retry_count FROM " + table(kind) + """
                 WHERE image_status = ? AND retry_count < ? AND id > ?
                ORDER BY id
                LIMIT 1
                FOR UPDATE SKIP LOCKED
                """,
                (rs, row) -> new PendingImage(kind, rs.getLong("id"), rs.getString("image_large_url"),
                        rs.getInt("retry_count")),
                ImageStatus.PENDING_RETRY.name(), maxRetries, afterId).stream().findFirst();
    }

    @Override
    public void markReady(ImageRules.Kind kind, long id, String smallUrl) {
        jdbc.update("UPDATE " + table(kind) + """
                 SET image_small_url = ?, image_status = ?, available = TRUE, retry_count = retry_count + 1
                WHERE id = ?
                """, smallUrl, ImageStatus.READY.name(), id);
    }

    @Override
    public void markAttemptFailed(ImageRules.Kind kind, long id, ImageStatus status) {
        if (status == ImageStatus.READY) {
            throw new IllegalArgumentException("Un intento fallido no puede dejar la entidad READY");
        }
        jdbc.update("UPDATE " + table(kind) + """
                 SET image_status = ?, available = FALSE, retry_count = retry_count + 1
                WHERE id = ?
                """, status.name(), id);
    }

    private static String table(ImageRules.Kind kind) {
        return TABLES.get(kind);
    }
}
