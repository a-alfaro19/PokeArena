package cr.ac.tec.pokearena.domain.rule;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Propósito: barajar listas de forma determinista a partir de una semilla.
 * Responsabilidades: crear una permutación reproducible sin mutar la entrada ni usar java.util.Random.
 * Referencia: sección 7.3; decisiones D-S3-05 y Anexo A.14.
 */
public final class SeededShuffle {

    private SeededShuffle() {
    }

    public static <T> List<T> shuffle(List<T> values, long seed) {
        Objects.requireNonNull(values, "La lista para barajar es obligatoria");
        List<T> shuffled = new ArrayList<>(values);
        SplitMix64 random = new SplitMix64(seed);
        for (int index = shuffled.size() - 1; index > 0; index--) {
            int other = random.nextInt(index + 1);
            T value = shuffled.get(index);
            shuffled.set(index, shuffled.get(other));
            shuffled.set(other, value);
        }
        return List.copyOf(shuffled);
    }
}
