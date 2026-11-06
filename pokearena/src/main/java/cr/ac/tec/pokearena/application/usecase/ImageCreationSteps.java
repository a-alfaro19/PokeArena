package cr.ac.tec.pokearena.application.usecase;

import java.util.function.Supplier;

import cr.ac.tec.pokearena.application.dto.IdempotentRequest;
import cr.ac.tec.pokearena.application.dto.IdempotentResult;
import cr.ac.tec.pokearena.application.port.ImageProcessor;
import cr.ac.tec.pokearena.application.port.ImageStorage;
import cr.ac.tec.pokearena.application.port.StoredImages;
import cr.ac.tec.pokearena.application.port.UnitOfWork;
import cr.ac.tec.pokearena.application.port.ValidatedImage;
import cr.ac.tec.pokearena.domain.error.IdempotencyConflictException;
import cr.ac.tec.pokearena.domain.error.ThumbnailGenerationException;
import cr.ac.tec.pokearena.domain.rule.ImageRules;

/**
 * Propósito: pasos comunes de las historias que crean una entidad con imagen (Crear carta y Crear
 * ubicación), para que ambas se comporten igual sin repetir código.
 * Responsabilidades: generar la miniatura sin detener la creación si falla (paso 8, ADR-002) y
 * ejecutar la transacción del paso 10 registrando la clave de idempotencia, con la compensación de
 * archivos y la resolución de una solicitud simultánea.
 * Referencia: secciones 7.1 y 7.2 (pasos 8 y 10), 5.4 y 9; Anexos A.1 y A.2.
 *
 * <p>No reemplaza a los casos de uso: cada uno sigue mostrando sus pasos en orden y llama a estos
 * métodos en el paso que corresponde. Aquí solo vive lo que sería idéntico en los dos.
 */
public class ImageCreationSteps {

    // Logger de Java (java.lang.System.Logger): la aplicación no depende de Spring ni de una
    // biblioteca de logs; Spring Boot lo redirige al mismo log JSON.
    private static final System.Logger LOG = System.getLogger(ImageCreationSteps.class.getName());

    private final IdempotencyGuard idempotency;
    private final ImageProcessor imageProcessor;
    private final ImageStorage imageStorage;
    private final UnitOfWork unitOfWork;

    public ImageCreationSteps(IdempotencyGuard idempotency, ImageProcessor imageProcessor, ImageStorage imageStorage,
            UnitOfWork unitOfWork) {
        this.idempotency = idempotency;
        this.imageProcessor = imageProcessor;
        this.imageStorage = imageStorage;
        this.unitOfWork = unitOfWork;
    }

    /**
     * Paso 8: miniatura con el tamaño exacto de {@code rules}, síncrona (ADR-002).
     *
     * @return los bytes PNG, o {@code null} si no se pudo generar: la entidad se guardará no
     *         disponible y con {@code PENDING_RETRY} (sección 7.1, "Si falla la miniatura")
     */
    public byte[] thumbnailOrPending(ValidatedImage image, ImageRules rules) {
        try {
            return imageProcessor.generateThumbnail(image, rules);
        } catch (ThumbnailGenerationException failure) {
            // No es un error de la solicitud: se registra la causa y la creación sigue.
            LOG.log(System.Logger.Level.WARNING,
                    "No se pudo generar la miniatura; la entidad queda PENDING_RETRY", failure);
            return null;
        }
    }

    /**
     * Paso 10: ejecuta {@code createEntity} y registra la clave de idempotencia en UNA transacción.
     * <ul>
     *   <li>Si otra solicitud igual confirmó primero, se borran los archivos de esta solicitud y se
     *       devuelve la respuesta de la otra (sección 5.4).</li>
     *   <li>Si falla cualquier otra cosa, la transacción se revierte, se borran los archivos del
     *       paso 9 (compensación, sección 9) y la falla se propaga sin cambios.</li>
     * </ul>
     *
     * @param stored       archivos guardados en el paso 9, que se borran si la transacción falla
     * @param createEntity trabajo de la transacción: guarda la entidad y devuelve su resultado
     */
    public <T> IdempotentResult<T> commitOrCompensate(IdempotentRequest request, StoredImages stored,
            Class<T> resultType, Supplier<T> createEntity) {
        try {
            T created = unitOfWork.execute(() -> {
                T result = createEntity.get();
                idempotency.record(request, result);
                return result;
            });
            return IdempotentResult.executed(request, created);
        } catch (IdempotencyConflictException conflict) {
            imageStorage.delete(stored.storageKey());
            return idempotency.resolveConflict(request, resultType);
        } catch (RuntimeException failure) {
            imageStorage.delete(stored.storageKey());
            throw failure;
        }
    }
}
