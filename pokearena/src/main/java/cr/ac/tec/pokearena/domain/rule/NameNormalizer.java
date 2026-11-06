package cr.ac.tec.pokearena.domain.rule;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Propósito: forma normalizada del nombre de una carta, para la búsqueda (sección 6, name_normalized).
 * Responsabilidades: pasar el nombre a minúsculas y quitarle las tildes, para que "Pikachú" y
 * "PIKACHU" coincidan en la búsqueda parcial de la historia 2.1.2.
 * Referencia: sección 6 ("name_normalized lo calcula el dominio, no una función SQL").
 *
 * <p>Cómo: la forma Unicode NFD separa cada letra de su marca (á pasa a "a" + tilde) y luego se
 * borran las marcas. También quita la virgulilla de la ñ (queda "n"), lo que conviene para buscar.
 */
public final class NameNormalizer {

    // Marcas diacríticas Unicode (tildes, diéresis, virgulilla, ...).
    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");

    private NameNormalizer() {
    }

    public static String normalize(String name) {
        String decomposed = Normalizer.normalize(name, Normalizer.Form.NFD);
        return DIACRITICS.matcher(decomposed).replaceAll("").toLowerCase(Locale.ROOT);
    }
}
