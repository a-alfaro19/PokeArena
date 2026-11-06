package cr.ac.tec.pokearena.application.usecase;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Propósito: hash estable del contenido de una solicitud, para la idempotencia (sección 5.4).
 * Responsabilidades: calcular un SHA-256 a partir de los campos y del contenido de los archivos,
 * de forma que el mismo contenido dé siempre el mismo hash y un contenido distinto, otro.
 * Referencia: sección 5.4 (columna request_hash; "misma clave con contenido distinto").
 *
 * <p>Se arma con un Builder (sección 4.4): {@code RequestHash.builder().field("cost", 3)
 * .file("image", bytes).build()}. Decisiones para que sea estable y sin ambigüedades:
 * <ul>
 *   <li>Los campos se ordenan por nombre: el orden en que se agregan no cambia el hash.</li>
 *   <li>Cada nombre y cada valor se escriben con su largo delante, así {@code "ab"+"c"} y
 *       {@code "a"+"bc"} dan hashes distintos.</li>
 *   <li>Un campo ausente ({@code null}) se distingue de uno vacío.</li>
 *   <li>Los archivos entran por su contenido (sus bytes), no por su nombre.</li>
 * </ul>
 */
public final class RequestHash {

    private static final String ALGORITHM = "SHA-256";

    // Marcas de tipo de cada parte, para que un campo y un archivo con el mismo contenido difieran.
    private static final byte TEXT = 'T';
    private static final byte FILE = 'F';
    private static final byte ABSENT = 'N';

    private RequestHash() {
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Arma el hash campo por campo. */
    public static final class Builder {

        private final Map<String, byte[]> parts = new TreeMap<>();

        private Builder() {
        }

        /** Agrega un campo de texto o número; {@code null} cuenta como ausente. */
        public Builder field(String name, Object value) {
            return add(name, value == null ? null : tagged(TEXT, String.valueOf(value).getBytes(StandardCharsets.UTF_8)));
        }

        /** Agrega un archivo por su contenido; {@code null} cuenta como ausente. */
        public Builder file(String name, byte[] content) {
            return add(name, content == null ? null : tagged(FILE, content));
        }

        /** SHA-256 en hexadecimal (64 caracteres). */
        public String build() {
            MessageDigest digest = sha256();
            parts.forEach((name, value) -> {
                writeWithLength(digest, name.getBytes(StandardCharsets.UTF_8));
                if (value == null) {
                    digest.update(ABSENT);
                } else {
                    writeWithLength(digest, value);
                }
            });
            return HexFormat.of().formatHex(digest.digest());
        }

        private Builder add(String name, byte[] value) {
            Objects.requireNonNull(name, "El nombre del campo es obligatorio");
            if (parts.containsKey(name)) {
                throw new IllegalArgumentException("El campo '" + name + "' se agregó dos veces al hash");
            }
            parts.put(name, value);
            return this;
        }
    }

    private static byte[] tagged(byte tag, byte[] content) {
        byte[] result = new byte[content.length + 1];
        result[0] = tag;
        System.arraycopy(content, 0, result, 1, content.length);
        return result;
    }

    private static void writeWithLength(MessageDigest digest, byte[] bytes) {
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance(ALGORITHM);
        } catch (NoSuchAlgorithmException exception) {
            // Toda JVM debe traer SHA-256: si falta, el entorno está roto.
            throw new IllegalStateException("La JVM no tiene " + ALGORITHM, exception);
        }
    }
}
