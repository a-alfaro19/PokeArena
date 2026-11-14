package cr.ac.tec.pokearena.application.usecase;

import java.util.Optional;

import cr.ac.tec.pokearena.application.dto.CardDetails;
import cr.ac.tec.pokearena.application.dto.CreateCardCommand;
import cr.ac.tec.pokearena.application.dto.IdempotentRequest;
import cr.ac.tec.pokearena.application.dto.IdempotentResult;
import cr.ac.tec.pokearena.application.port.CatalogCache;
import cr.ac.tec.pokearena.application.port.Clock;
import cr.ac.tec.pokearena.application.port.ImageProcessor;
import cr.ac.tec.pokearena.application.port.ImageStorage;
import cr.ac.tec.pokearena.application.port.StoredImages;
import cr.ac.tec.pokearena.application.port.ValidatedImage;
import cr.ac.tec.pokearena.domain.error.ValidationException;
import cr.ac.tec.pokearena.domain.model.Ability;
import cr.ac.tec.pokearena.domain.model.Card;
import cr.ac.tec.pokearena.domain.model.CardCode;
import cr.ac.tec.pokearena.domain.model.CardFields;
import cr.ac.tec.pokearena.domain.repository.AbilityRepository;
import cr.ac.tec.pokearena.domain.repository.CardCatalogRepository;
import cr.ac.tec.pokearena.domain.repository.CardRepository;
import cr.ac.tec.pokearena.domain.rule.CardFieldRules;
import cr.ac.tec.pokearena.domain.rule.EvolutionRules;
import cr.ac.tec.pokearena.domain.rule.ImageRules;

/**
 * Propósito: caso de uso de la historia 2.1.1 "Crear carta".
 * Responsabilidades: orquestar idempotencia, validaciones, imagen, código único y persistencia, en el
 * orden del diagrama de secuencia.
 * Referencia: sección 7.1 (tabla de pasos) y Anexo A.1 (diagrama de secuencia).
 *
 * <h2>Plantilla de los casos de uso (sección 4.4)</h2>
 * Crear ubicación y Buscar carta siguen esta misma estructura:
 * <ol>
 *   <li><b>Autorizar</b> (pasos 1 a 3): ya ocurrió en la API. El interceptor verificó la sesión y el
 *       rol ({@code @RequiresRole}) y el controlador validó {@code Idempotency-Key}. El caso de uso
 *       recibe un usuario autorizado y no vuelve a comprobarlo.</li>
 *   <li><b>Idempotencia</b> (paso 4): si la clave ya tiene respuesta, se devuelve y no se hace nada más.</li>
 *   <li><b>Validar</b> (pasos 5 a 7): todos los campos, la habilidad y la imagen, cada uno con su error
 *       tipado. <b>Nada se guarda antes de que todo sea válido</b>: una solicitud inválida no deja
 *       archivos ni filas.</li>
 *   <li><b>Efectos externos</b> (pasos 8 y 9): miniatura y archivos, FUERA de la transacción (sección 4.3).</li>
 *   <li><b>Ejecutar en UnitOfWork</b> (paso 10): consecutivo, reglas que necesitan el código, entidad y
 *       registro de la clave, en una sola transacción. Si falla, se revierte todo y se compensan los
 *       efectos externos (se borran los archivos).</li>
 *   <li><b>Responder</b> (paso 11): la API responde con el status y la ficha.</li>
 * </ol>
 * Cada paso lleva un comentario con su número en la tabla de 7.1 y sus mensajes en el diagrama.
 * Lo que es idéntico en todas las historias que crean una entidad con imagen (la miniatura que puede
 * fallar y la transacción con la clave, la compensación y el conflicto) vive en
 * {@link ImageCreationSteps}; el caso de uso lo llama en el paso que corresponde.
 *
 * <h2>Decisiones</h2>
 * <ul>
 *   <li>Evolución: el diagrama verifica la habilidad en el mensaje 9, pero la regla de evolución
 *       necesita el código que va a recibir la carta, que solo existe dentro de la transacción. Por
 *       decisión del equipo, en el paso 6 se valida que la habilidad exista y {@link EvolutionRules}
 *       se aplica en el paso 10, después de obtener el consecutivo y antes de guardar. Si falla, la
 *       transacción se revierte (el consecutivo no se consume) y se borran los archivos.</li>
 *   <li>Miniatura (ADR-002, síncrona): si falla, la carta se guarda igual, no disponible y con
 *       {@code PENDING_RETRY}; la reintentará la tarea programada (otra tarea).</li>
 * </ul>
 */
public class CreateCardUseCase {

    private final IdempotencyGuard idempotency;
    private final ImageCreationSteps imageCreation;
    private final CardCatalogRepository catalog;
    private final AbilityRepository abilities;
    private final CardRepository cards;
    private final ImageProcessor imageProcessor;
    private final ImageStorage imageStorage;
    private final Clock clock;
    private final CatalogCache catalogCache;

    public CreateCardUseCase(IdempotencyGuard idempotency, ImageCreationSteps imageCreation,
            CardCatalogRepository catalog, AbilityRepository abilities, CardRepository cards,
            ImageProcessor imageProcessor, ImageStorage imageStorage, Clock clock, CatalogCache catalogCache) {
        this.idempotency = idempotency;
        this.imageCreation = imageCreation;
        this.catalog = catalog;
        this.abilities = abilities;
        this.cards = cards;
        this.imageProcessor = imageProcessor;
        this.imageStorage = imageStorage;
        this.clock = clock;
        this.catalogCache = catalogCache;
    }

    /**
     * Crea una carta.
     *
     * @param request datos de idempotencia (clave del usuario autorizado y hash del contenido)
     * @param command campos del formulario y la imagen
     * @return la ficha de la carta, nueva o repetida de una solicitud anterior con la misma clave
     */
    public IdempotentResult<CardDetails> execute(IdempotentRequest request, CreateCardCommand command) {
        // Pasos 1 a 3 (mensajes 1 a 5 del diagrama A.1): la interfaz envió el POST, la seguridad
        // validó sesión y rol de administrador, y el controlador validó Idempotency-Key.

        // Paso 4 (mensajes 6 y 7): solicitud previa con la clave de idempotencia.
        Optional<IdempotentResult<CardDetails>> previous = idempotency.findPrevious(request, CardDetails.class);
        if (previous.isPresent()) {
            return previous.get();
        }

        // Paso 5 (mensaje 8): nombre, costo, poder, tipo y rareza.
        CardFields fields = CardFieldRules.validate(command.name(), command.cost(), command.power(),
                command.typeCode(), command.rarityCode(), catalog.findAllTypes(), catalog.findAllRarities());

        // Paso 6 (mensajes 9 y 10): la habilidad es obligatoria y debe existir. La regla de evolución
        // se aplica en el paso 10, cuando ya se conoce el código de la carta (decisión del equipo).
        Ability ability = validateAbility(command.abilityCode());

        // Paso 7 (mensajes 11 y 12): formato real, peso, proporción 5:7 y resolución mínima.
        if (command.image() == null || command.image().length == 0) {
            throw ValidationException.missingField(CardFieldRules.IMAGE_FIELD, "Debe subir la imagen de la carta.");
        }
        ValidatedImage image = imageProcessor.validate(command.image(), ImageRules.CARD);

        // Paso 8 (mensajes 13 y 14): miniatura de 200 x 280, síncrona (ADR-002). Si falla, null:
        // la carta se guardará no disponible y con PENDING_RETRY.
        byte[] thumbnail = imageCreation.thumbnailOrPending(image, ImageRules.CARD);

        // Paso 9 (mensajes 15 y 16): guardar la versión grande y la pequeña, fuera de la transacción.
        StoredImages stored = imageStorage.save(ImageRules.CARD, image, thumbnail);

        // Paso 10 (mensajes 17 a 20): una sola transacción con la carta y la clave de idempotencia.
        // Si falla, se revierte y se borran los archivos; si otra solicitud igual ganó, se repite su respuesta.
        IdempotentResult<CardDetails> result = imageCreation.commitOrCompensate(request, stored, CardDetails.class, () -> {
            // Consecutivo del tipo: si la transacción se revierte, no se consume.
            CardCode code = CardCode.of(fields.type().code(), cards.nextNumber(fields.type().code()));
            // Evolución contra el código real que va a recibir la carta.
            EvolutionRules.check(code, ability, cards::evolutionTargetOf);
            Card card = Card.create(code, fields, ability, stored.largeUrl(), stored.smallUrl(), clock.now());
            cards.save(card);
            return CardDetails.of(card);
        });

        // Después de confirmar la transacción (sección 9, "Invalidación"): incrementar catalog:version para
        // que la carta nueva aparezca en el catálogo y en la búsqueda sin esperar el vencimiento de las
        // páginas cacheadas. Fuera de la transacción: Redis no participa en ella, y una falla de la caché
        // nunca revierte ni rechaza una carta ya guardada.
        catalogCache.invalidate();

        // Paso 11 (mensajes 21 y 22): con el resultado, la API responde 201 con la ficha.
        return result;
    }

    private Ability validateAbility(String abilityCode) {
        if (abilityCode == null || abilityCode.isBlank()) {
            throw ValidationException.missingField(CardFieldRules.ABILITY_FIELD,
                    "Debe elegir una habilidad para la carta, aunque sea " + Ability.NO_ABILITY_CODE + " (Sin Habilidad).");
        }
        return abilities.findActiveByCode(abilityCode).orElseThrow(() -> ValidationException.unknownAbility(
                CardFieldRules.ABILITY_FIELD, "La habilidad '" + abilityCode + "' no existe."));
    }
}
