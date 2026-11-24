package cr.ac.tec.pokearena.api.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import cr.ac.tec.pokearena.domain.error.ErrorType;

/**
 * Propósito: comprobar la traducción HTTP de los errores agregados en la base compartida del Sprint 3.
 * Responsabilidades: fijar los status de validación, conflicto, falta de recursos y recursos inexistentes.
 * Referencia: sección 5.3.
 */
@DisplayName("Status HTTP de errores compartidos de partida")
class ApiErrorCatalogTest {

    @Test
    @DisplayName("Los errores de deck inválido, conflicto, cubos insuficientes y partida inexistente conservan su status")
    void matchErrorsUseSpecifiedStatuses() {
        assertThat(ApiErrorCatalog.of(ErrorType.INVALID_DECK).status())
                .isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST);
        assertThat(ApiErrorCatalog.of(ErrorType.ACTIVE_MATCH_EXISTS).status())
                .isEqualTo(org.springframework.http.HttpStatus.CONFLICT);
        assertThat(ApiErrorCatalog.of(ErrorType.INSUFFICIENT_CUBES).status())
                .isEqualTo(org.springframework.http.HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(ApiErrorCatalog.of(ErrorType.MATCH_PREPARATION_FAILED).status())
                .isEqualTo(org.springframework.http.HttpStatus.CONFLICT);
        assertThat(ApiErrorCatalog.of(ErrorType.MATCH_NOT_FOUND).status())
                .isEqualTo(org.springframework.http.HttpStatus.NOT_FOUND);
    }
}
