package cr.ac.tec.pokearena.domain.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import cr.ac.tec.pokearena.support.SampleDomainErrors;

/**
 * Propósito: pruebas unitarias de la jerarquía de errores de dominio (sección 5.3).
 * Responsabilidades: comprobar que cada fábrica produce su tipo, mensaje, campo y valores
 * permitidos, y que la excepción es inmutable. Java puro, sin Spring.
 * Referencia: sección 5.3.
 */
@DisplayName("Jerarquía de errores de dominio")
class DomainExceptionTest {

    @ParameterizedTest(name = "{0}")
    @EnumSource(ErrorType.class)
    @DisplayName("Cada tipo de la sección 5.3 tiene una fábrica que lo produce con un mensaje")
    void everyTypeHasAFactory(ErrorType type) {
        DomainException error = SampleDomainErrors.of(type);

        assertThat(error.type()).isEqualTo(type);
        assertThat(error.getMessage()).isNotBlank();
        assertThat(error).isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("El catálogo tiene exactamente los 18 tipos de la tabla de la sección 5.3")
    void catalogMatchesSpecification() {
        assertThat(ErrorType.values()).extracting(Enum::name).containsExactlyInAnyOrder(
                "NOT_AUTHENTICATED", "FORBIDDEN", "INVALID_IDENTITY_TOKEN", "IDENTITY_PROVIDER_UNAVAILABLE",
                "UNSUPPORTED_PROVIDER", "INVALID_FIELD", "MISSING_FIELD", "UNKNOWN_ABILITY", "INVALID_EVOLUTION",
                "INVALID_EFFECT", "INVALID_SEARCH_CRITERIA", "INVALID_PAGINATION", "INVALID_IMAGE_FORMAT",
                "IMAGE_TOO_LARGE", "INVALID_IMAGE_RATIO", "IMAGE_RESOLUTION_TOO_LOW", "IDEMPOTENCY_KEY_REQUIRED",
                "IDEMPOTENCY_KEY_REUSED");
    }

    @Test
    @DisplayName("Un campo inválido lleva el campo y los valores permitidos")
    void invalidFieldCarriesFieldAndAllowedValues() {
        ValidationException error = ValidationException.invalidField("cost", "El costo debe estar entre 0 y 10.",
                List.of("0..10"));

        assertThat(error.type()).isEqualTo(ErrorType.INVALID_FIELD);
        assertThat(error.getMessage()).isEqualTo("El costo debe estar entre 0 y 10.");
        assertThat(error.field()).contains("cost");
        assertThat(error.allowedValues()).containsExactly("0..10");
    }

    @Test
    @DisplayName("Un error sin campo ni valores permitidos los informa vacíos, no nulos")
    void optionalDataIsEmptyWhenAbsent() {
        AuthorizationException error = AuthorizationException.forbidden("Se requiere el rol de administrador.");

        assertThat(error.type()).isEqualTo(ErrorType.FORBIDDEN);
        assertThat(error.field()).isEmpty();
        assertThat(error.allowedValues()).isEmpty();
    }

    @Test
    @DisplayName("No autenticado y sin permiso son tipos y familias distintas")
    void authenticationAndAuthorizationAreDistinct() {
        DomainException notAuthenticated = AuthenticationException.notAuthenticated("Debe iniciar sesión.");
        DomainException forbidden = AuthorizationException.forbidden("Se requiere el rol de administrador.");

        assertThat(notAuthenticated.type()).isNotEqualTo(forbidden.type());
        assertThat(notAuthenticated).isInstanceOf(AuthenticationException.class);
        assertThat(forbidden).isInstanceOf(AuthorizationException.class);
    }

    @Test
    @DisplayName("Los errores de imagen siempre indican el campo de la imagen")
    void imageErrorsPointToImageField() {
        assertThat(ImageValidationException.invalidRatio("La imagen debe tener proporción 5:7.").field())
                .contains(ImageValidationException.IMAGE_FIELD);
    }

    @Test
    @DisplayName("Los errores de idempotencia indican el encabezado Idempotency-Key")
    void idempotencyErrorsPointToHeader() {
        assertThat(IdempotencyException.reused("La clave ya se usó con un contenido distinto.").field())
                .contains("Idempotency-Key");
    }

    @Test
    @DisplayName("Los valores permitidos son una copia inmutable de la lista recibida")
    void allowedValuesAreAnImmutableCopy() {
        List<String> original = new ArrayList<>(List.of("0..10"));
        ValidationException error = ValidationException.invalidField("cost", "El costo debe estar entre 0 y 10.",
                original);
        original.add("cambio posterior");

        assertThat(error.allowedValues()).containsExactly("0..10");
        assertThatThrownBy(() -> error.allowedValues().add("otro"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("Un error de dominio exige un mensaje para el usuario")
    void messageIsRequired() {
        assertThatThrownBy(() -> ValidationException.missingField("ability", " "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
