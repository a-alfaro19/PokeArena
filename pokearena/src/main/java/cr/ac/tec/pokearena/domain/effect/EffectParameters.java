package cr.ac.tec.pokearena.domain.effect;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import cr.ac.tec.pokearena.domain.error.ValidationException;
import cr.ac.tec.pokearena.domain.rule.LocationFieldRules;

/**
 * Propósito: lectura de los parámetros de un efecto, común a todos los plug-ins.
 * Responsabilidades: rechazar parámetros desconocidos, exigir enteros en rango, tipos de carta
 * existentes y listas de enteros sin repetidos, siempre con INVALID_EFFECT sobre el campo
 * {@code effect} y los valores permitidos.
 * Referencia: sección 7.2 ("Los parámetros del efecto se validan según su tipo").
 *
 * <p>Los parámetros llegan como JSON ya convertido a mapa: un número entero es {@code Integer},
 * {@code Long} o {@code BigInteger}. Un número con decimales (3.5, o incluso 3.0) o un texto ("3")
 * no se acepta como entero: el criterio pide montos enteros.
 */
final class EffectParameters {

    private EffectParameters() {
    }

    /** Rechaza los parámetros que el efecto no conoce, para detectar errores de escritura. */
    static void allowOnly(String code, Map<String, Object> parameters, Set<String> known) {
        for (String name : parameters.keySet()) {
            if (!known.contains(name)) {
                throw invalid("El efecto " + code + " no tiene el parámetro '" + name + "'.", new TreeSet<>(known));
            }
        }
    }

    /** Entero obligatorio entre {@code min} y {@code max}. */
    static int requireInteger(String code, Map<String, Object> parameters, String name, int min, int max) {
        Object value = parameters.get(name);
        if (value == null) {
            throw invalid("El efecto " + code + " necesita el parámetro '" + name + "'.", List.of(range(min, max)));
        }
        return integerInRange(code, name, value, min, max);
    }

    /** Código de tipo de carta obligatorio y existente. */
    static String requireType(String code, Map<String, Object> parameters, String name, Collection<String> types) {
        return optionalType(code, parameters, name, types).orElseThrow(() -> invalid(
                "El efecto " + code + " necesita el parámetro '" + name + "' (tipo de carta).", sorted(types)));
    }

    /** Código de tipo de carta opcional; si está, debe existir. */
    static Optional<String> optionalType(String code, Map<String, Object> parameters, String name,
            Collection<String> types) {
        Object value = parameters.get(name);
        if (value == null) {
            return Optional.empty();
        }
        if (!(value instanceof String type) || !types.contains(type)) {
            throw invalid("El parámetro '" + name + "' del efecto " + code + " no es un tipo de carta existente.",
                    sorted(types));
        }
        return Optional.of(type);
    }

    /** Lista obligatoria, no vacía, de enteros entre {@code min} y {@code max}, sin repetidos. */
    static List<Integer> requireDistinctIntegers(String code, Map<String, Object> parameters, String name,
            int min, int max) {
        String allowed = range(min, max);
        if (!(parameters.get(name) instanceof List<?> values) || values.isEmpty()) {
            throw invalid("El efecto " + code + " necesita '" + name + "': una lista no vacía de enteros entre "
                    + min + " y " + max + ".", List.of(allowed));
        }
        List<Integer> result = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();
        for (Object value : values) {
            int number = integerInRange(code, name, value, min, max);
            if (!seen.add(number)) {
                throw invalid("El parámetro '" + name + "' del efecto " + code + " repite el valor " + number + ".",
                        List.of(allowed));
            }
            result.add(number);
        }
        return result;
    }

    private static int integerInRange(String code, String name, Object value, int min, int max) {
        Long number = switch (value) {
            case Integer i -> i.longValue();
            case Long l -> l;
            case BigInteger big when big.bitLength() < Long.SIZE -> big.longValue();
            default -> null;
        };
        if (number == null || number < min || number > max) {
            throw invalid("El parámetro '" + name + "' del efecto " + code + " debe ser un entero entre " + min
                    + " y " + max + ".", List.of(range(min, max)));
        }
        return number.intValue();
    }

    static ValidationException invalid(String message, Collection<String> allowedValues) {
        return ValidationException.invalidEffect(LocationFieldRules.EFFECT_FIELD, message, List.copyOf(allowedValues));
    }

    private static String range(int min, int max) {
        return min + ".." + max;
    }

    private static List<String> sorted(Collection<String> values) {
        return new ArrayList<>(new TreeSet<>(values));
    }
}
