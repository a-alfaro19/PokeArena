package cr.ac.tec.pokearena.application.usecase;

import java.util.Optional;

import cr.ac.tec.pokearena.application.dto.CreateLocationCommand;
import cr.ac.tec.pokearena.application.dto.IdempotentRequest;
import cr.ac.tec.pokearena.application.dto.IdempotentResult;
import cr.ac.tec.pokearena.application.dto.LocationDetails;
import cr.ac.tec.pokearena.application.port.Clock;
import cr.ac.tec.pokearena.application.port.EffectReader;
import cr.ac.tec.pokearena.application.port.EffectReader.EffectInput;
import cr.ac.tec.pokearena.application.port.ImageProcessor;
import cr.ac.tec.pokearena.application.port.ImageStorage;
import cr.ac.tec.pokearena.application.port.StoredImages;
import cr.ac.tec.pokearena.application.port.ValidatedImage;
import cr.ac.tec.pokearena.domain.effect.LocationEffectRegistry;
import cr.ac.tec.pokearena.domain.error.ValidationException;
import cr.ac.tec.pokearena.domain.model.Location;
import cr.ac.tec.pokearena.domain.model.LocationEffect;
import cr.ac.tec.pokearena.domain.model.LocationFields;
import cr.ac.tec.pokearena.domain.repository.LocationRepository;
import cr.ac.tec.pokearena.domain.rule.ImageRules;
import cr.ac.tec.pokearena.domain.rule.LocationFieldRules;

/**
 * Propósito: caso de uso de la historia 2.2.1 "Crear ubicación".
 * Responsabilidades: orquestar idempotencia, validaciones, efecto, imagen y persistencia, en el orden
 * del diagrama de secuencia.
 * Referencia: sección 7.2 (tabla de pasos) y Anexo A.2 (diagrama de secuencia).
 *
 * <p>Sigue la misma plantilla que {@link CreateCardUseCase} (ver su encabezado): autorizar (pasos 1 a
 * 3, en la API), idempotencia (paso 4), validar todo antes de guardar nada (pasos 5 a 7), efectos
 * externos fuera de la transacción (pasos 8 y 9), una sola transacción (paso 10) y responder (paso 11).
 * Los pasos 8 y 10 son idénticos a los de Crear carta y viven en {@link ImageCreationSteps}.
 *
 * <h2>Decisiones</h2>
 * <ul>
 *   <li>Efecto (paso 6): lo valida {@link LocationEffectRegistry}, que busca el plug-in de su tipo.
 *       Este caso de uso no conoce ningún efecto concreto: agregar uno nuevo no lo modifica.</li>
 *   <li>La transacción solo guarda la ubicación y la clave de idempotencia: no toca ninguna otra
 *       tabla, así las partidas en curso no se ven afectadas (criterio C.2).</li>
 *   <li>Miniatura (ADR-002, síncrona): si falla, la ubicación se guarda igual, no disponible y con
 *       {@code PENDING_RETRY}, igual que una carta.</li>
 * </ul>
 */
public class CreateLocationUseCase {

    private final IdempotencyGuard idempotency;
    private final ImageCreationSteps imageCreation;
    private final EffectReader effectReader;
    private final LocationEffectRegistry effects;
    private final LocationRepository locations;
    private final ImageProcessor imageProcessor;
    private final ImageStorage imageStorage;
    private final Clock clock;

    public CreateLocationUseCase(IdempotencyGuard idempotency, ImageCreationSteps imageCreation,
            EffectReader effectReader, LocationEffectRegistry effects, LocationRepository locations,
            ImageProcessor imageProcessor, ImageStorage imageStorage, Clock clock) {
        this.idempotency = idempotency;
        this.imageCreation = imageCreation;
        this.effectReader = effectReader;
        this.effects = effects;
        this.locations = locations;
        this.imageProcessor = imageProcessor;
        this.imageStorage = imageStorage;
        this.clock = clock;
    }

    /**
     * Crea una ubicación.
     *
     * @param request datos de idempotencia (clave del usuario autorizado y hash del contenido)
     * @param command campos del formulario, el efecto y la imagen
     * @return la ficha de la ubicación, nueva o repetida de una solicitud anterior con la misma clave
     */
    public IdempotentResult<LocationDetails> execute(IdempotentRequest request, CreateLocationCommand command) {
        // Pasos 1 a 3 (mensajes 1 a 5 del diagrama A.2): la interfaz envió el POST, la seguridad
        // validó sesión y rol de administrador, y el controlador validó Idempotency-Key.

        // Paso 4 (mensajes 6 y 7): solicitud previa con la clave de idempotencia.
        Optional<IdempotentResult<LocationDetails>> previous = idempotency.findPrevious(request, LocationDetails.class);
        if (previous.isPresent()) {
            return previous.get();
        }

        // Paso 5 (mensaje 8): nombre, descripción, límite de cartas y energía extra según su modalidad.
        LocationFields fields = LocationFieldRules.validate(command.name(), command.description(),
                command.cardLimit(), command.extraEnergyMode(), command.extraEnergyAmount());

        // Paso 6 (mensajes 9 y 10): se lee el texto JSON del efecto y el registro de efectos valida su
        // tipo y sus parámetros. El efecto es opcional.
        Optional<EffectInput> input = effectReader.read(command.effect());
        Optional<LocationEffect> effect = effects.validate(input.map(EffectInput::code).orElse(null),
                input.map(EffectInput::parameters).orElse(null));

        // Paso 7 (mensajes 11 y 12): formato real, peso, proporción 4:3 y resolución mínima.
        if (command.image() == null || command.image().length == 0) {
            throw ValidationException.missingField(LocationFieldRules.IMAGE_FIELD,
                    "Debe subir la imagen de la ubicación.");
        }
        ValidatedImage image = imageProcessor.validate(command.image(), ImageRules.LOCATION);

        // Paso 8 (mensajes 13 y 14): miniatura de 320 x 240, síncrona (ADR-002). Si falla, null:
        // la ubicación se guardará no disponible y con PENDING_RETRY.
        byte[] thumbnail = imageCreation.thumbnailOrPending(image, ImageRules.LOCATION);

        // Paso 9 (mensajes 15 y 16): guardar la versión grande y la pequeña, fuera de la transacción.
        StoredImages stored = imageStorage.save(ImageRules.LOCATION, image, thumbnail);

        // Paso 10 (mensajes 17 a 20): una sola transacción con la ubicación y la clave de idempotencia.
        // Si falla, se revierte y se borran los archivos; si otra solicitud igual ganó, se repite su respuesta.
        // Paso 11 (mensajes 21 y 22): con el resultado, la API responde 201 con la ubicación.
        return imageCreation.commitOrCompensate(request, stored, LocationDetails.class, () -> {
            Location location = Location.create(fields, effect.orElse(null), stored.largeUrl(), stored.smallUrl(),
                    clock.now());
            long id = locations.save(location);
            return LocationDetails.of(id, location);
        });
    }
}
