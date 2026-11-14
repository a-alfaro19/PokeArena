package cr.ac.tec.pokearena.application.usecase;

import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import cr.ac.tec.pokearena.application.dto.ThumbnailRetryResult;
import cr.ac.tec.pokearena.application.dto.ThumbnailRetryResult.Outcome;
import cr.ac.tec.pokearena.application.port.CatalogCache;
import cr.ac.tec.pokearena.application.port.ImageProcessor;
import cr.ac.tec.pokearena.application.port.ImageStorage;
import cr.ac.tec.pokearena.application.port.UnitOfWork;
import cr.ac.tec.pokearena.domain.error.ImageValidationException;
import cr.ac.tec.pokearena.domain.error.ThumbnailGenerationException;
import cr.ac.tec.pokearena.domain.model.ImageStatus;
import cr.ac.tec.pokearena.domain.model.PendingImage;
import cr.ac.tec.pokearena.domain.repository.PendingImageRepository;
import cr.ac.tec.pokearena.domain.rule.ImageRules;

/**
 * Propósito: reintentar las miniaturas que fallaron al crear cartas y ubicaciones (criterio C.1: "el
 * sistema reintenta la generación"; ADR-002).
 * Responsabilidades: recorrer las cartas y ubicaciones en {@code PENDING_RETRY} con menos de
 * {@code app.images.max-retries} intentos y hacer UN intento por entidad en cada ejecución: leer la
 * versión grande, volver a generar la miniatura y guardarla. Cada intento suma 1 a {@code retry_count}.
 * Referencia: sección 7.1 ("Si falla la miniatura" y "Operaciones de la tarea de reintentos") y 7.2.
 *
 * <h2>Resultado de cada intento</h2>
 * <ul>
 *   <li>Miniatura generada: {@code READY}, disponible y con la URL de la miniatura.</li>
 *   <li>Falla y quedan intentos: sigue {@code PENDING_RETRY}, no disponible.</li>
 *   <li>Falla y era el último intento ({@code retry_count} llega al máximo): {@code FAILED}, no
 *       disponible. Una entidad {@code FAILED} ya no se selecciona, así que no se reintenta.</li>
 *   <li>La versión grande ya no está en disco: {@code FAILED} de inmediato, porque ningún intento
 *       futuro podría lograrlo. El adaptador lo registra como aviso en el log.</li>
 * </ul>
 *
 * <h2>Decisiones</h2>
 * <ul>
 *   <li>Concurrencia (2 instancias): una transacción por entidad. Dentro de ella se bloquea la fila con
 *       {@code FOR UPDATE SKIP LOCKED}: otra instancia que corre a la vez la salta y toma la siguiente.
 *       Así nunca hay dos intentos simultáneos sobre la misma entidad.</li>
 *   <li>Excepción a la regla de 4.3 ("sin efectos externos dentro de la transacción"): leer la versión
 *       grande y escribir la miniatura ocurren con la fila bloqueada, porque el bloqueo es justamente lo
 *       que evita el trabajo doble. No hace falta compensar: si la transacción falla después de escribir
 *       {@code small.png}, la entidad sigue sin miniatura (su URL no se publicó) y el próximo intento
 *       reemplaza el archivo.</li>
 *   <li>Un intento por entidad y por ejecución: se avanza con {@code id > último procesado}, así una
 *       entidad que sigue pendiente no se vuelve a tomar en la misma ejecución, sino en la siguiente
 *       (después de {@code app.images.retry-interval}).</li>
 *   <li>Se vuelve a validar la versión grande antes de generar la miniatura: el procesador necesita la
 *       imagen validada (formato y dimensiones), y una imagen que ya no es válida cuenta como intento
 *       fallido.</li>
 * </ul>
 */
public class ThumbnailRetryUseCase {

    /** Tipos de entidad con imagen, en el orden en que se recorren. */
    private static final List<ImageRules> ENTITY_RULES = List.of(ImageRules.CARD, ImageRules.LOCATION);

    /** {@code afterId} de la primera entidad: los {@code id} (bigserial) empiezan en 1. */
    private static final long FROM_START = 0;

    private final UnitOfWork unitOfWork;
    private final PendingImageRepository pending;
    private final ImageStorage imageStorage;
    private final ImageProcessor imageProcessor;
    private final int maxRetries;
    private final CatalogCache catalogCache;

    /**
     * @param maxRetries intentos máximos de la tarea antes de pasar a {@code FAILED} ({@code app.images.max-retries})
     */
    public ThumbnailRetryUseCase(UnitOfWork unitOfWork, PendingImageRepository pending, ImageStorage imageStorage,
            ImageProcessor imageProcessor, int maxRetries, CatalogCache catalogCache) {
        if (maxRetries < 1) {
            throw new IllegalArgumentException("app.images.max-retries debe ser al menos 1; recibió " + maxRetries);
        }
        this.unitOfWork = unitOfWork;
        this.pending = pending;
        this.imageStorage = imageStorage;
        this.imageProcessor = imageProcessor;
        this.maxRetries = maxRetries;
        this.catalogCache = catalogCache;
    }

    /**
     * Una ejecución: un intento por cada carta y ubicación pendiente que no esté tomando otra instancia.
     *
     * @return el resultado de cada intento, en orden (vacío si no había nada pendiente)
     */
    public List<ThumbnailRetryResult> execute() {
        List<ThumbnailRetryResult> results = new ArrayList<>();
        for (ImageRules rules : ENTITY_RULES) {
            long afterId = FROM_START;
            Optional<ThumbnailRetryResult> result;
            while ((result = retryNext(rules, afterId)).isPresent()) {
                results.add(result.get());
                afterId = result.get().id();
            }
        }
        // Un intento sobre una carta cambia lo que muestran el catálogo y la búsqueda (disponibilidad y
        // estado de imagen): se invalida la caché una vez, después de confirmar (sección 9).
        if (results.stream().anyMatch(result -> result.kind() == ImageRules.Kind.CARD)) {
            catalogCache.invalidate();
        }
        return results;
    }

    /** Un intento sobre la siguiente entidad pendiente, en su propia transacción. */
    private Optional<ThumbnailRetryResult> retryNext(ImageRules rules, long afterId) {
        return unitOfWork.execute(() -> pending.lockNextPending(rules.kind(), maxRetries, afterId)
                .map(entity -> attempt(entity, rules)));
    }

    private ThumbnailRetryResult attempt(PendingImage entity, ImageRules rules) {
        int attempts = entity.retryCount() + 1;

        Optional<byte[]> large = imageStorage.readLarge(entity.largeUrl());
        if (large.isEmpty()) {
            pending.markAttemptFailed(entity.kind(), entity.id(), ImageStatus.FAILED);
            return new ThumbnailRetryResult(entity.kind(), entity.id(), Outcome.LARGE_IMAGE_MISSING, attempts);
        }
        try {
            byte[] thumbnail = imageProcessor.generateThumbnail(imageProcessor.validate(large.get(), rules), rules);
            String smallUrl = imageStorage.saveThumbnail(entity.largeUrl(), thumbnail);
            pending.markReady(entity.kind(), entity.id(), smallUrl);
            return new ThumbnailRetryResult(entity.kind(), entity.id(), Outcome.READY, attempts);
        } catch (ThumbnailGenerationException | ImageValidationException | UncheckedIOException failure) {
            boolean exhausted = attempts >= maxRetries;
            pending.markAttemptFailed(entity.kind(), entity.id(),
                    exhausted ? ImageStatus.FAILED : ImageStatus.PENDING_RETRY);
            return new ThumbnailRetryResult(entity.kind(), entity.id(),
                    exhausted ? Outcome.FAILED : Outcome.STILL_PENDING, attempts);
        }
    }
}
