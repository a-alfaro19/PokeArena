package cr.ac.tec.pokearena.domain.effect;

import java.util.Objects;

/**
 * Propósito: descripción de un parámetro de un efecto de ubicación, para que la interfaz arme el
 * formulario sin tener los nombres ni los rangos escritos a mano (tarea "opciones desde la API").
 * Responsabilidades: decir el nombre del parámetro en el JSON, su texto para el formulario, qué clase de
 * valor espera, si es obligatorio y, cuando aplica, su rango.
 * Referencia: sección 7.2 ("Formato del campo effect") y {@code GET /api/v1/opciones/creacion}.
 *
 * <p>Cada plug-in declara su lista con las MISMAS constantes que usa al validar, y
 * {@link EffectParameters#allowOnly} toma de esa lista los nombres permitidos: la descripción y la
 * validación no pueden diferir.
 *
 * @param name     clave dentro de {@code parameters} del JSON del efecto
 * @param label    texto en español para el formulario
 * @param kind     clase de valor que espera
 * @param required {@code true} si el efecto no se acepta sin él
 * @param min      mínimo inclusivo, o {@code null} si no aplica ({@link Kind#CARD_TYPE})
 * @param max      máximo inclusivo, o {@code null} si no aplica
 */
public record EffectParameterSpec(String name, String label, Kind kind, boolean required, Integer min, Integer max) {

    /** Clase de valor de un parámetro. */
    public enum Kind {
        /** Un entero en {@code [min, max]}. */
        INTEGER,
        /** Un código de tipo de carta existente (los valores vienen del catálogo, no del parámetro). */
        CARD_TYPE,
        /** Una lista no vacía de enteros en {@code [min, max]}, sin repetidos. */
        INTEGER_SET
    }

    public EffectParameterSpec {
        Objects.requireNonNull(name, "El nombre del parámetro es obligatorio");
        Objects.requireNonNull(label, "El texto del parámetro es obligatorio");
        Objects.requireNonNull(kind, "La clase del parámetro es obligatoria");
        if (kind == Kind.CARD_TYPE ? min != null || max != null : min == null || max == null || min > max) {
            throw new IllegalArgumentException("Rango inválido para el parámetro " + name + " (" + kind + ")");
        }
    }

    /** Entero en {@code [min, max]}. */
    public static EffectParameterSpec integer(String name, String label, boolean required, int min, int max) {
        return new EffectParameterSpec(name, label, Kind.INTEGER, required, min, max);
    }

    /** Código de tipo de carta existente. */
    public static EffectParameterSpec cardType(String name, String label, boolean required) {
        return new EffectParameterSpec(name, label, Kind.CARD_TYPE, required, null, null);
    }

    /** Lista obligatoria y no vacía de enteros en {@code [min, max]}, sin repetidos. */
    public static EffectParameterSpec integerSet(String name, String label, int min, int max) {
        return new EffectParameterSpec(name, label, Kind.INTEGER_SET, true, min, max);
    }
}
