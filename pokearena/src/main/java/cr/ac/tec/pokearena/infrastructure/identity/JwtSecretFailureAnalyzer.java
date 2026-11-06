package cr.ac.tec.pokearena.infrastructure.identity;

import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;

/**
 * Propósito: mensaje claro cuando la aplicación no arranca por falta de un secreto JWT válido.
 * Responsabilidades: en lugar de una traza larga, Spring Boot muestra "APPLICATION FAILED TO
 * START" con la causa y qué hacer. Se registra en {@code META-INF/spring.factories}.
 * Referencia: sección 5.2.
 */
public class JwtSecretFailureAnalyzer extends AbstractFailureAnalyzer<InvalidJwtSecretException> {

    @Override
    protected FailureAnalysis analyze(Throwable rootFailure, InvalidJwtSecretException cause) {
        return new FailureAnalysis(cause.getMessage(),
                "Defina la variable de entorno APP_JWT_SECRET con un secreto aleatorio de al menos "
                        + JwtTokenService.MIN_SECRET_BYTES + " bytes antes de arrancar. En PowerShell:\n"
                        + "  $b = New-Object byte[] 48; "
                        + "[Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b); "
                        + "$env:APP_JWT_SECRET = [Convert]::ToBase64String($b)",
                cause);
    }
}
