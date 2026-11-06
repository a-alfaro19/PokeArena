package cr.ac.tec.pokearena.application.port;

import java.util.Map;
import java.util.Optional;

import cr.ac.tec.pokearena.domain.error.ValidationException;

/**
 * Propósito: leer el efecto de una ubicación desde el texto JSON del formulario (sección 7.2: "los
 * parámetros del efecto llegan en el multipart como un campo de texto con JSON").
 * Responsabilidades: convertir el texto en el código del efecto y sus parámetros. No valida el efecto:
 * eso lo hace el registro de efectos. Es un puerto porque la aplicación no puede depender de JSON
 * (sección 4.2); la implementación con Jackson vive en infraestructura.
 * Referencia: sección 7.2, paso 6.
 *
 * <p>Se lee en el paso 6 del caso de uso, y no en el controlador, para respetar el orden de los
 * errores: un efecto mal escrito se informa después de los campos del paso 5.
 */
public interface EffectReader {

    /**
     * Lee {@code text}, con la forma {@code {"code": "TYPE_BONUS", "parameters": {...}}}.
     *
     * @return el efecto leído, o vacío si el texto es nulo o está en blanco (ubicación sin efecto)
     * @throws ValidationException INVALID_EFFECT sobre el campo {@code effect} si no tiene esa forma
     */
    Optional<EffectInput> read(String text);

    /**
     * Efecto tal como llegó, todavía sin validar.
     *
     * @param code       tipo de efecto
     * @param parameters parámetros (vacío si no se enviaron)
     */
    record EffectInput(String code, Map<String, Object> parameters) {
    }
}
