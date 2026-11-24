package cr.ac.tec.pokearena.domain.rule;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Propósito: barajar listas de forma reproducible sin usar {@code java.util.Random}.
 * Responsabilidades: aplicar Fisher-Yates con un generador SplitMix64 local, usando la semilla del servidor.
 * Referencia: sección 4.2 (azar por puerto), sección 7.3; Anexo A.14; criterios C.10-E1, E2 y A1.
 */
public final class SeededShuffle {

    private SeededShuffle() {
    }

    /**
     * Devuelve una permutación determinista de los elementos recibidos.
     *
     * @param values elementos a barajar
     * @param seed   semilla derivada de la semilla de partida
     * @param <T>    tipo de elemento
     * @return copia inmutable en orden barajado
     */
    public static <T> List<T> shuffle(List<T> values, long seed) {
        Objects.requireNonNull(values, "La lista a barajar es obligatoria");
        ArrayList<T> shuffled = new ArrayList<>(values);
        SplitMix64 random = new SplitMix64(seed);
        for (int index = shuffled.size() - 1; index > 0; index--) {
            int other = random.nextInt(index + 1);
            T value = shuffled.get(index);
            shuffled.set(index, shuffled.get(other));
            shuffled.set(other, value);
        }
        return List.copyOf(shuffled);
    }

    private static final class SplitMix64 {

        private static final long GAMMA = 0x9e3779b97f4a7c15L;
        private static final long MULTIPLIER_1 = 0xbf58476d1ce4e5b9L;
        private static final long MULTIPLIER_2 = 0x94d049bb133111ebL;
        private static final int NON_NEGATIVE_SHIFT = 1;

        private long state;

        private SplitMix64(long seed) {
            state = seed;
        }

        private int nextInt(int bound) {
            long mask = bound - 1L;
            long value = nextLong() >>> NON_NEGATIVE_SHIFT;
            if ((bound & (bound - 1)) == 0) {
                return (int) (value & mask);
            }
            long remainder = value % bound;
            while (value + mask - remainder < 0) {
                value = nextLong() >>> NON_NEGATIVE_SHIFT;
                remainder = value % bound;
            }
            return (int) remainder;
        }

        private long nextLong() {
            long value = (state += GAMMA);
            value = (value ^ (value >>> 30)) * MULTIPLIER_1;
            value = (value ^ (value >>> 27)) * MULTIPLIER_2;
            return value ^ (value >>> 31);
        }
    }
}
