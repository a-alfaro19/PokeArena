package cr.ac.tec.pokearena.infrastructure.storage;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import cr.ac.tec.pokearena.application.dto.ThumbnailRetryResult;
import cr.ac.tec.pokearena.application.usecase.ThumbnailRetryUseCase;

/**
 * Propósito: tarea programada que reintenta las miniaturas pendientes (sección 7.1; ADR-002).
 * Responsabilidades: invocar {@link ThumbnailRetryUseCase} cada {@code app.images.retry-interval} y
 * registrar en el log el resultado de cada intento. No tiene reglas: todo lo decide el caso de uso.
 * Referencia: sección 7.1 ("Si falla la miniatura").
 *
 * <p>Decisiones:
 * <ul>
 *   <li>{@code fixedDelay}: la siguiente ejecución empieza un intervalo DESPUÉS de que termina la
 *       anterior, así dos ejecuciones de la misma instancia nunca se solapan. Entre instancias, las
 *       protege el bloqueo {@code FOR UPDATE SKIP LOCKED}.</li>
 *   <li>La primera ejecución también espera un intervalo, para no competir con el arranque.</li>
 *   <li>Si una ejecución falla (por ejemplo, la base no responde), se registra y la siguiente lo vuelve
 *       a intentar: una excepción no detiene la programación.</li>
 * </ul>
 */
@Component
public class ThumbnailRetryJob {

    private static final Logger LOG = LoggerFactory.getLogger(ThumbnailRetryJob.class);

    private final ThumbnailRetryUseCase retries;

    public ThumbnailRetryJob(ThumbnailRetryUseCase retries) {
        this.retries = retries;
    }

    @Scheduled(fixedDelayString = "${app.images.retry-interval}", initialDelayString = "${app.images.retry-interval}")
    public void run() {
        try {
            log(retries.execute());
        } catch (RuntimeException failure) {
            LOG.error("Falló la ejecución de los reintentos de miniaturas; se intentará en la siguiente", failure);
        }
    }

    private static void log(List<ThumbnailRetryResult> results) {
        for (ThumbnailRetryResult result : results) {
            switch (result.outcome()) {
                case READY -> LOG.info("Miniatura generada en el reintento {}: {} {} queda READY y disponible",
                        result.retryCount(), result.kind(), result.id());
                case STILL_PENDING -> LOG.info("Falló el reintento {} de la miniatura de {} {}; sigue PENDING_RETRY",
                        result.retryCount(), result.kind(), result.id());
                case FAILED -> LOG.warn("Se agotaron los reintentos de la miniatura de {} {} ({}): queda FAILED",
                        result.kind(), result.id(), result.retryCount());
                case LARGE_IMAGE_MISSING -> LOG.warn(
                        "Falta en disco la versión grande de {} {}: no se puede generar la miniatura, queda FAILED",
                        result.kind(), result.id());
            }
        }
    }
}
