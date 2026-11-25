package cr.ac.tec.pokearena.domain.rule;

/**
 * Propósito: generador determinista SplitMix64 para las reglas de azar reproducible.
 * Responsabilidades: producir valores pseudoaleatorios sin depender de java.util.Random.
 * Referencia: sección 7.3; decisiones D-S3-05 y Anexo A.14.
 */
final class SplitMix64 {

    private static final long GOLDEN_GAMMA = 0x9E3779B97F4A7C15L;
    private static final long MIX_MULTIPLIER_1 = 0xBF58476D1CE4E5B9L;
    private static final long MIX_MULTIPLIER_2 = 0x94D049BB133111EBL;
    private static final int SHIFT_1 = 30;
    private static final int SHIFT_2 = 27;
    private static final int SHIFT_3 = 31;

    private long state;

    SplitMix64(long seed) {
        this.state = seed;
    }

    static long mix(long value) {
        long mixed = (value ^ (value >>> SHIFT_1)) * MIX_MULTIPLIER_1;
        mixed = (mixed ^ (mixed >>> SHIFT_2)) * MIX_MULTIPLIER_2;
        return mixed ^ (mixed >>> SHIFT_3);
    }

    long nextLong() {
        state += GOLDEN_GAMMA;
        return mix(state);
    }

    int nextInt(int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("El límite debe ser positivo");
        }
        long mask = bound - 1L;
        long random = nextLong() >>> 1;
        if ((bound & mask) == 0) {
            return (int) (random & mask);
        }
        long candidate = random % bound;
        while (random + mask - candidate < 0) {
            random = nextLong() >>> 1;
            candidate = random % bound;
        }
        return (int) candidate;
    }
}
