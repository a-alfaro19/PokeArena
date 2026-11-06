package cr.ac.tec.pokearena.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Propósito: pruebas unitarias del hash de solicitudes para la idempotencia (sección 5.4).
 * Responsabilidades: comprobar que el hash es estable para el mismo contenido y distinto para
 * cualquier diferencia de campos o archivos, sin ambigüedades.
 * Referencia: sección 5.4.
 */
@DisplayName("Hash de la solicitud (SHA-256)")
class RequestHashTest {

    private static final byte[] IMAGE = "contenido-de-imagen".getBytes(StandardCharsets.UTF_8);

    @Test
    @DisplayName("El mismo contenido da siempre el mismo hash, de 64 caracteres hexadecimales")
    void sameContentSameHash() {
        String first = RequestHash.builder().field("name", "Pikachu").field("cost", 3).file("image", IMAGE).build();
        String second = RequestHash.builder().field("name", "Pikachu").field("cost", 3)
                .file("image", IMAGE.clone()).build();

        assertThat(first).isEqualTo(second).matches("[0-9a-f]{64}");
    }

    @Test
    @DisplayName("El orden en que se agregan los campos no cambia el hash")
    void orderDoesNotMatter() {
        String ordered = RequestHash.builder().field("cost", 3).field("name", "Pikachu").build();
        String reversed = RequestHash.builder().field("name", "Pikachu").field("cost", 3).build();

        assertThat(ordered).isEqualTo(reversed);
    }

    @Test
    @DisplayName("Un campo con otro valor da otro hash")
    void differentFieldDifferentHash() {
        assertThat(RequestHash.builder().field("cost", 3).build())
                .isNotEqualTo(RequestHash.builder().field("cost", 4).build());
    }

    @Test
    @DisplayName("Un archivo con otro contenido da otro hash, aunque los campos sean iguales")
    void differentFileDifferentHash() {
        byte[] otherImage = "otra-imagen".getBytes(StandardCharsets.UTF_8);

        assertThat(RequestHash.builder().field("name", "Pikachu").file("image", IMAGE).build())
                .isNotEqualTo(RequestHash.builder().field("name", "Pikachu").file("image", otherImage).build());
    }

    @Test
    @DisplayName("Nombre y valor no se confunden al juntarse: a=bc es distinto de ab=c")
    void noConcatenationAmbiguity() {
        assertThat(RequestHash.builder().field("a", "bc").build())
                .isNotEqualTo(RequestHash.builder().field("ab", "c").build());
    }

    @Test
    @DisplayName("Un campo ausente es distinto de un campo vacío")
    void absentDiffersFromEmpty() {
        assertThat(RequestHash.builder().field("ability", null).build())
                .isNotEqualTo(RequestHash.builder().field("ability", "").build());
    }

    @Test
    @DisplayName("Un campo de texto y un archivo con el mismo contenido dan hashes distintos")
    void fieldDiffersFromFile() {
        assertThat(RequestHash.builder().field("x", "contenido-de-imagen").build())
                .isNotEqualTo(RequestHash.builder().file("x", IMAGE).build());
    }

    @Test
    @DisplayName("Agregar dos veces el mismo campo es un error de programación")
    void duplicateFieldIsRejected() {
        assertThatThrownBy(() -> RequestHash.builder().field("cost", 3).field("cost", 4))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
