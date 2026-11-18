package cr.ac.tec.pokearena.domain.rule;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.function.BiFunction;
import java.util.function.Function;

import cr.ac.tec.pokearena.domain.error.ValidationException;

/**
 * Propósito: lectura común de los parámetros de consulta de la búsqueda del administrador (7.3) y del
 * catálogo del jugador (8.3).
 * Responsabilidades: detectar nombres desconocidos y valores repetidos o vacíos, leer un texto sin
 * espacios, leer un entero acotado, leer los dos extremos de un rango (por ejemplo {@code costMin} y
 * {@code costMax}), detectar un rango con el mínimo mayor que el máximo y comprobar que un código exista
 * en su catálogo (por ejemplo el tipo).
 * Referencia: secciones 7.3 y 8.3 (corrección 7: una sola lógica para las historias).
 *
 * <p>Decisión: aquí está solo lo que las dos historias hacen igual. El <b>tipo</b> de error y su
 * <b>mensaje</b> los conserva cada una ({@code INVALID_SEARCH_CRITERIA} en {@link CardSearchRules},
 * {@code INVALID_FIELD} con valores permitidos en {@link CatalogRules}), porque así lo pide la
 * especificación para cada historia. Por eso las funciones informan el problema y quien llama arma el error.
 */
final class QueryParameters {

    /** Problema de los valores de un parámetro. */
    enum ValueProblem {
        /** El parámetro aparece más de una vez. */
        REPEATED,
        /** El parámetro está, pero sin valor o solo con espacios. */
        BLANK
    }

    /** Problema de un parámetro que debe ser un entero acotado. */
    enum IntegerProblem {
        /** Texto, decimal o un número que no cabe en un {@code int}. */
        NOT_AN_INTEGER,
        /** Entero fuera de los límites. */
        OUT_OF_RANGE
    }

    /**
     * Extremos de un rango leídos de dos parámetros; un extremo {@code null} es un rango abierto por ese lado.
     *
     * @param min mínimo inclusivo, o {@code null}
     * @param max máximo inclusivo, o {@code null}
     */
    record Bounds(Integer min, Integer max) {

        /** {@code true} si los dos extremos están y el mínimo es mayor que el máximo. */
        boolean inverted() {
            return QueryParameters.inverted(min, max);
        }
    }

    private QueryParameters() {
    }

    /**
     * Primer parámetro desconocido en orden alfabético, para que el error sea predecible si hay varios.
     */
    static Optional<String> firstUnknown(Map<String, List<String>> parameters, Collection<String> known) {
        return new TreeSet<>(parameters.keySet()).stream().filter(name -> !known.contains(name)).findFirst();
    }

    /** Problema de los valores de un parámetro presente, o vacío si tiene exactamente un valor con texto. */
    static Optional<ValueProblem> valueProblem(List<String> values) {
        if (values != null && values.size() > 1) {
            return Optional.of(ValueProblem.REPEATED);
        }
        if (values == null || values.isEmpty() || values.getFirst() == null || values.getFirst().isBlank()) {
            return Optional.of(ValueProblem.BLANK);
        }
        return Optional.empty();
    }

    /** Valor del parámetro sin espacios al inicio ni al final, o {@code null} si no se indicó. */
    static String text(Map<String, List<String>> parameters, String name) {
        List<String> values = parameters.get(name);
        return values == null ? null : values.getFirst().strip();
    }

    /**
     * Entero entre {@code lowest} y {@code highest} (inclusive), o {@code null} si el parámetro no se indicó.
     *
     * @param onProblem arma el error de la historia según el problema encontrado
     */
    static Integer boundedInteger(Map<String, List<String>> parameters, String name, int lowest, int highest,
            Function<IntegerProblem, ValidationException> onProblem) {
        String value = text(parameters, name);
        if (value == null) {
            return null;
        }
        int number;
        try {
            number = Integer.parseInt(value);
        } catch (NumberFormatException notAnInteger) {
            throw onProblem.apply(IntegerProblem.NOT_AN_INTEGER);
        }
        if (number < lowest || number > highest) {
            throw onProblem.apply(IntegerProblem.OUT_OF_RANGE);
        }
        return number;
    }

    /**
     * Lee los dos extremos de un rango, cada uno como entero entre {@code lowest} y {@code highest}, primero
     * el mínimo y después el máximo. No comprueba el orden de los extremos: cada historia decide cuándo y con
     * qué error rechazar un rango invertido ({@link Bounds#inverted()}).
     *
     * @param onProblem arma el error de la historia para el parámetro y el problema encontrados
     */
    static Bounds bounds(Map<String, List<String>> parameters, String minName, String maxName, int lowest,
            int highest, BiFunction<String, IntegerProblem, ValidationException> onProblem) {
        Integer min = boundedInteger(parameters, minName, lowest, highest, problem -> onProblem.apply(minName, problem));
        Integer max = boundedInteger(parameters, maxName, lowest, highest, problem -> onProblem.apply(maxName, problem));
        return new Bounds(min, max);
    }

    /**
     * Código de un catálogo (tipo o rareza) que debe existir, o {@code null} si el parámetro no se indicó.
     *
     * @param onUnknown arma el error de la historia con el valor que no existe
     */
    static String existingCode(Map<String, List<String>> parameters, String name, Collection<String> allowed,
            Function<String, ValidationException> onUnknown) {
        String value = text(parameters, name);
        if (value != null && !allowed.contains(value)) {
            throw onUnknown.apply(value);
        }
        return value;
    }

    /** {@code true} si los dos extremos están y el mínimo es mayor que el máximo. */
    static boolean inverted(Integer min, Integer max) {
        return min != null && max != null && min > max;
    }
}
