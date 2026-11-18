package cr.ac.tec.pokearena.application.usecase;

import java.util.Arrays;
import java.util.List;

import cr.ac.tec.pokearena.application.dto.CreationOptions;
import cr.ac.tec.pokearena.application.dto.ValueRange;
import cr.ac.tec.pokearena.domain.effect.LocationEffectRegistry;
import cr.ac.tec.pokearena.domain.model.ExtraEnergy;
import cr.ac.tec.pokearena.domain.model.ImageFormat;
import cr.ac.tec.pokearena.domain.repository.AbilityRepository;
import cr.ac.tec.pokearena.domain.rule.CardFieldRules;
import cr.ac.tec.pokearena.domain.rule.ImageRules;
import cr.ac.tec.pokearena.domain.rule.LocationFieldRules;

/**
 * Propósito: opciones de los formularios Crear carta (2.1.1) y Crear ubicación (2.2.1), que la interfaz
 * pide antes del POST.
 * Responsabilidades: listar las habilidades activas y los efectos registrados, y tomar los rangos, las
 * modalidades de energía y los requisitos de imagen de las mismas clases que validan la creación.
 * Referencia: Anexos A.1 y A.2 (mensajes 1 a 9: la interfaz pide las opciones antes del POST); secciones
 * 7.1 y 7.2.
 *
 * <h2>Misma plantilla que los demás casos de uso (corrección 7)</h2>
 * <ol>
 *   <li><b>Autorizar:</b> ya ocurrió en la API ({@code @RequiresRole(ADMIN)} en {@code OptionsController}):
 *       solo el administrador crea cartas y ubicaciones.</li>
 *   <li><b>Idempotencia:</b> no aplica; es una lectura (GET).</li>
 *   <li><b>Validar:</b> no hay parámetros que validar.</li>
 *   <li><b>Ejecutar:</b> una lectura de habilidades activas, sin {@code UnitOfWork}: no hay nada que
 *       confirmar ni revertir. El resto son constantes del dominio y el registro de efectos.</li>
 *   <li><b>Responder:</b> 200 con las opciones.</li>
 * </ol>
 *
 * <h2>Decisiones</h2>
 * <ul>
 *   <li>Una sola fuente: {@link CardFieldRules}, {@link LocationFieldRules}, {@link ExtraEnergy}, los
 *       plug-ins de efecto (por {@link LocationEffectRegistry}) e {@link ImageRules} validan la creación y
 *       también alimentan estas opciones. Un efecto nuevo aparece aquí sin cambiar este caso de uso.</li>
 *   <li>Habilidades por código y efectos por código; modalidades y formatos en el orden de su enum: el
 *       orden es estable entre solicitudes.</li>
 * </ul>
 */
public class GetCreationOptionsUseCase {

    private final AbilityRepository abilities;
    private final LocationEffectRegistry effects;

    public GetCreationOptionsUseCase(AbilityRepository abilities, LocationEffectRegistry effects) {
        this.abilities = abilities;
        this.effects = effects;
    }

    /** Opciones de los formularios de creación. */
    public CreationOptions execute() {
        // Mensajes 1 a 5 de A.1 y A.2: la interfaz pidió las opciones antes del POST y la seguridad validó el
        // rol de administrador. Mensajes 6 a 9: leer las opciones y responder.
        return new CreationOptions(
                // Mensajes 6 y 7 de A.1: habilidades activas, las mismas que acepta el paso 6 de Crear carta.
                abilities.findAllActive(),
                new ValueRange(CardFieldRules.MIN_COST, CardFieldRules.MAX_COST),
                new ValueRange(CardFieldRules.MIN_POWER, CardFieldRules.MAX_POWER),
                new ValueRange(LocationFieldRules.MIN_CARD_LIMIT, LocationFieldRules.MAX_CARD_LIMIT),
                ExtraEnergy.DEFAULT_MODE,
                List.of(ExtraEnergy.Mode.values()),
                // Mensajes 6 y 7 de A.2: efectos registrados con la descripción de sus parámetros.
                effects.types(),
                new CreationOptions.ImageOptions(allowedFormats(), ImageRules.MAX_FILE_BYTES, ImageRules.CARD,
                        ImageRules.LOCATION));
    }

    /** Formatos permitidos en el orden del enum ({@code ALLOWED_FORMATS} es un conjunto sin orden). */
    private static List<ImageFormat> allowedFormats() {
        return Arrays.stream(ImageFormat.values()).filter(ImageRules.ALLOWED_FORMATS::contains).toList();
    }
}
