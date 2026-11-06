package cr.ac.tec.pokearena.api.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import cr.ac.tec.pokearena.domain.error.ErrorType;
import cr.ac.tec.pokearena.support.OpenApiContract;

/**
 * Propósito: prueba de contrato del formato único de errores (sección 5.3; RNF-API-03).
 * Responsabilidades: comprobar que el esquema {@code ApiError} del contrato enumera exactamente los
 * tipos de error que la aplicación puede producir (los de dominio y los de protocolo), y que cada
 * {@code x-error-types} del contrato nombra un tipo existente.
 * Referencia: secciones 5.3 y 10.
 *
 * <p>Vive en el paquete {@code api.error} porque los tipos de protocolo
 * ({@code ApiErrorCatalog.HttpErrorType}) son internos de ese paquete.
 */
@DisplayName("Contrato OpenAPI: formato único de errores")
class ApiErrorContractTest {

    private final OpenApiContract contract = OpenApiContract.load();

    private static Set<String> applicationErrorTypes() {
        Set<String> types = new LinkedHashSet<>();
        Arrays.stream(ErrorType.values()).map(Enum::name).forEach(types::add);
        Arrays.stream(ApiErrorCatalog.HttpErrorType.values()).map(Enum::name).forEach(types::add);
        return types;
    }

    @Test
    @DisplayName("El esquema ApiError enumera exactamente los tipos de dominio y de protocolo de la aplicación")
    void errorSchemaMatchesCatalog() {
        assertThat(contract.errorTypes()).containsExactlyInAnyOrderElementsOf(applicationErrorTypes());
    }

    @Test
    @DisplayName("Cada x-error-types del contrato nombra un tipo de error existente")
    void referencedTypesExist() {
        assertThat(contract.referencedErrorTypes()).isNotEmpty().isSubsetOf(applicationErrorTypes());
    }
}
