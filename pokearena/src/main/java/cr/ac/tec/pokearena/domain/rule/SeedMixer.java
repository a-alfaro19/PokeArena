package cr.ac.tec.pokearena.domain.rule;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Propósito: derivar semillas estables para cada uso de azar de una partida.
 * Responsabilidades: mezclar la semilla del servidor con un propósito fijo, sin recibir datos del cliente.
 * Referencia: sección 7.3 ("Semillas"); Anexo A.14; criterio C.10-R2.
 */
public final class SeedMixer {

    private static final long FNV_OFFSET_BASIS = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;
    private static final long SPLIT_MIX_GAMMA = 0x9e3779b97f4a7c15L;
    private static final long SPLIT_MIX_MULTIPLIER_1 = 0xbf58476d1ce4e5b9L;
    private static final long SPLIT_MIX_MULTIPLIER_2 = 0x94d049bb133111ebL;

    private SeedMixer() {
    }

    /**
     * Deriva una semilla determinista a partir de una semilla de partida y un propósito estable.
     *
     * @param matchSeed semilla generada por el servidor
     * @param purpose   nombre fijo del uso, por ejemplo {@code locations} o {@code seat-1}
     * @return semilla derivada
     */
    public static long derive(long matchSeed, String purpose) {
        Objects.requireNonNull(purpose, "El propósito de la semilla es obligatorio");
        if (purpose.isBlank()) {
            throw new IllegalArgumentException("El propósito de la semilla no puede estar vacío.");
        }

        long purposeHash = FNV_OFFSET_BASIS;
        for (byte value : purpose.getBytes(StandardCharsets.UTF_8)) {
            purposeHash = (purposeHash ^ Byte.toUnsignedLong(value)) * FNV_PRIME;
        }
        return splitMix64(matchSeed ^ purposeHash);
    }

    private static long splitMix64(long value) {
        long mixed = value + SPLIT_MIX_GAMMA;
        mixed = (mixed ^ (mixed >>> 30)) * SPLIT_MIX_MULTIPLIER_1;
        mixed = (mixed ^ (mixed >>> 27)) * SPLIT_MIX_MULTIPLIER_2;
        return mixed ^ (mixed >>> 31);
    }
}
