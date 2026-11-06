package cr.ac.tec.pokearena.domain.model;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Propósito: código único e inmutable de una carta, {@code PKA-<TIPO>-<NNN>} (criterio C.1).
 * Responsabilidades: armar el código a partir del tipo y del consecutivo, con un MÍNIMO de 3 dígitos
 * rellenos con ceros y sin máximo: PKA-FUE-001 ... PKA-FUE-999, PKA-FUE-1000.
 * Referencia: sección 7.1 ("Código único").
 *
 * <p>Sin máximo (decisión del equipo): el catálogo puede llegar a unas 10.000 cartas y, con un tope
 * de 999 por tipo, solo cabrían 5.994 entre los 6 tipos.
 *
 * @param value código, por ejemplo {@code PKA-FUE-001}
 */
public record CardCode(String value) {

    /** Primer consecutivo de cada tipo. */
    public static final int FIRST_NUMBER = 1;

    /** Dígitos mínimos del consecutivo; los que faltan se rellenan con ceros a la izquierda. */
    public static final int MIN_DIGITS = 3;

    private static final String PREFIX = "PKA";
    private static final Pattern FORMAT = Pattern.compile(PREFIX + "-[A-Z]{3}-[0-9]{" + MIN_DIGITS + ",}");
    private static final String NUMBER_FORMAT = "%0" + MIN_DIGITS + "d";

    public CardCode {
        Objects.requireNonNull(value, "El código es obligatorio");
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("Código de carta con formato inválido: " + value);
        }
    }

    /** Código de la carta número {@code number} del tipo {@code typeCode}. */
    public static CardCode of(String typeCode, int number) {
        if (number < FIRST_NUMBER) {
            throw new IllegalArgumentException("El consecutivo debe empezar en " + FIRST_NUMBER + ": " + number);
        }
        return new CardCode(PREFIX + "-" + typeCode + "-" + String.format(NUMBER_FORMAT, number));
    }

    @Override
    public String toString() {
        return value;
    }
}
