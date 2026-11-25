package cr.ac.tec.pokearena.domain.rule;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Propósito: derivar subsemillas reproducibles de la semilla de una partida.
 * Responsabilidades: separar el azar por propósito sin incorporar datos del cliente.
 * Referencia: sección 7.3; Anexo A.14.
 */
public final class SeedMixer {

    private static final long FNV_OFFSET_BASIS = 0xCBF29CE484222325L;
    private static final long FNV_PRIME = 0x100000001B3L;

    private SeedMixer() {
    }

    public static long derive(long seed, String purpose) {
        Objects.requireNonNull(purpose, "El propósito de la semilla es obligatorio");
        if (purpose.isBlank()) {
            throw new IllegalArgumentException("El propósito de la semilla no puede estar vacío");
        }
        long purposeHash = FNV_OFFSET_BASIS;
        for (byte value : purpose.getBytes(StandardCharsets.UTF_8)) {
            purposeHash = (purposeHash ^ Byte.toUnsignedLong(value)) * FNV_PRIME;
        }
        return SplitMix64.mix(seed ^ purposeHash);
    }
}
