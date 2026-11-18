package cr.ac.tec.pokearena.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import cr.ac.tec.pokearena.support.OpenApiContract;

/**
 * Propósito: pruebas del contrato {@code openapi/pokearena.yaml} que no necesitan la aplicación.
 * Responsabilidades: comprobar que cada etiqueta que usa una operación está declarada en {@code tags}, con
 * su descripción, para que la documentación generada agrupe todas las operaciones.
 * Referencia: sección 10 ("OpenAPI primero").
 */
@DisplayName("Contrato OpenAPI: etiquetas")
class OpenApiContractTest {

    private final OpenApiContract contract = OpenApiContract.load();

    @Test
    @DisplayName("10: cada etiqueta usada por una operación (incluidas Catálogo y Opciones) está declarada en tags")
    void usedTagsAreDeclared() {
        assertThat(contract.usedTags()).contains("Catálogo", "Opciones");
        assertThat(contract.declaredTags()).containsAll(contract.usedTags());
    }
}
