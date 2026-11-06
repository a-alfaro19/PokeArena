package cr.ac.tec.pokearena.domain.rule;

import java.util.List;

import cr.ac.tec.pokearena.domain.error.ValidationException;

/**
 * Propósito: comprobaciones de campos que comparten las reglas de cartas y de ubicaciones.
 * Responsabilidades: exigir un texto no vacío (MISSING_FIELD) y convertir un texto en un entero
 * dentro de un rango (INVALID_FIELD con el rango como valor permitido), con los mismos mensajes en
 * todas las historias.
 * Referencia: sección 5.3 (INVALID_FIELD y MISSING_FIELD con field y allowedValues).
 *
 * <p>Los números llegan como texto, tal como vienen del formulario, para que un valor mal escrito
 * ("abc") tenga el mismo error y los mismos valores permitidos que uno fuera de rango.
 */
final class FieldChecks {

    private FieldChecks() {
    }

    /** El texto, si no es nulo ni está vacío; si no, MISSING_FIELD con {@code message}. */
    static String requireText(String field, String value, String message) {
        if (value == null || value.isBlank()) {
            throw ValidationException.missingField(field, message);
        }
        return value;
    }

    /**
     * Entero entre {@code min} y {@code max}, ambos incluidos.
     *
     * @param label sujeto del mensaje, por ejemplo "El costo"
     */
    static int integerInRange(String field, String label, String value, int min, int max) {
        List<String> allowed = List.of(min + ".." + max);
        String text = requireText(field, value, label + " es obligatorio.").strip();
        int number;
        try {
            number = Integer.parseInt(text);
        } catch (NumberFormatException notAnInteger) {
            throw ValidationException.invalidField(field,
                    label + " debe ser un número entero entre " + min + " y " + max + ".", allowed);
        }
        if (number < min || number > max) {
            throw ValidationException.invalidField(field,
                    label + " debe estar entre " + min + " y " + max + ".", allowed);
        }
        return number;
    }
}
