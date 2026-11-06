package cr.ac.tec.pokearena.infrastructure.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.diagnostics.FailureAnalysis;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import cr.ac.tec.pokearena.application.port.AuthenticatedUser;
import cr.ac.tec.pokearena.application.port.TokenIssuer.IssuedToken;
import cr.ac.tec.pokearena.domain.error.AuthenticationException;
import cr.ac.tec.pokearena.domain.error.ErrorType;
import cr.ac.tec.pokearena.domain.model.Role;
import cr.ac.tec.pokearena.support.TestSecrets;

/**
 * Propósito: pruebas unitarias de las credenciales propias firmadas con HS256 (sección 5.2).
 * Responsabilidades: comprobar que un token válido se acepta y que uno vencido, con otra firma,
 * con el rol manipulado, de otro emisor, sin firma o mal formado se rechaza como NOT_AUTHENTICATED;
 * y que un secreto ausente o corto impide crear el servicio con un mensaje claro.
 * Referencia: sección 5.2.
 */
@DisplayName("Credenciales propias JWT (HS256)")
class JwtTokenServiceTest {

    private static final Duration TTL = Duration.ofMinutes(15);
    private static final Instant T0 = Instant.parse("2026-11-05T12:00:00Z");

    private final String secret = TestSecrets.randomJwtSecret();
    private final AtomicReference<Instant> now = new AtomicReference<>(T0);
    private final JwtTokenService service = new JwtTokenService(secret, TTL, now::get);
    private final AuthenticatedUser admin = new AuthenticatedUser(UUID.randomUUID(), Role.ADMIN);

    @Test
    @DisplayName("Un token válido devuelve el mismo jugador y rol con que se emitió")
    void validTokenIsAccepted() {
        IssuedToken token = service.issue(admin);

        assertThat(service.verify(token.value())).isEqualTo(admin);
        assertThat(token.validity()).isEqualTo(TTL);
        assertThat(token.expiresAt()).isEqualTo(T0.plus(TTL));
    }

    @Test
    @DisplayName("Un token sigue válido un segundo antes de vencer")
    void tokenIsValidUntilExpiration() {
        IssuedToken token = service.issue(admin);
        now.set(T0.plus(TTL).minusSeconds(1));

        assertThat(service.verify(token.value())).isEqualTo(admin);
    }

    @Test
    @DisplayName("Un token vencido es rechazado como no autenticado")
    void expiredTokenIsRejected() {
        IssuedToken token = service.issue(admin);
        now.set(T0.plus(TTL));

        assertNotAuthenticated(token.value());
    }

    @Test
    @DisplayName("Un token firmado con otro secreto es rechazado")
    void tokenWithOtherSignatureIsRejected() {
        JwtTokenService otherApplication = new JwtTokenService(TestSecrets.randomJwtSecret(), TTL, now::get);

        assertNotAuthenticated(otherApplication.issue(admin).value());
    }

    @Test
    @DisplayName("Un token con el rol manipulado (PLAYER cambiado a ADMIN) es rechazado")
    void tamperedRoleIsRejected() {
        String token = service.issue(new AuthenticatedUser(UUID.randomUUID(), Role.PLAYER)).value();
        String[] parts = token.split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        String tampered = payload.replace("\"PLAYER\"", "\"ADMIN\"");
        assertThat(tampered).isNotEqualTo(payload);

        // Se cambia el contenido pero se conserva la firma original: ya no coincide.
        String forged = parts[0] + "." + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(tampered.getBytes(StandardCharsets.UTF_8)) + "." + parts[2];

        assertNotAuthenticated(forged);
    }

    @Test
    @DisplayName("Un token sin firma (algoritmo none) es rechazado")
    void unsignedTokenIsRejected() {
        String header = base64Url("{\"alg\":\"none\"}");
        String payload = base64Url("{\"iss\":\"pokearena\",\"sub\":\"" + admin.playerId()
                + "\",\"role\":\"ADMIN\",\"exp\":" + T0.plus(TTL).getEpochSecond() + "}");

        assertNotAuthenticated(header + "." + payload + ".");
    }

    @Test
    @DisplayName("Un token bien firmado pero de otro emisor es rechazado")
    void tokenFromOtherIssuerIsRejected() throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder().issuer("otro-sistema").subject(admin.playerId().toString())
                .claim(JwtTokenService.ROLE_CLAIM, "ADMIN").expirationTime(Date.from(T0.plus(TTL))).build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jwt.sign(new MACSigner(secret.getBytes(StandardCharsets.UTF_8)));

        assertNotAuthenticated(jwt.serialize());
    }

    @Test
    @DisplayName("Un texto que no es un token, vacío o nulo es rechazado")
    void malformedTokensAreRejected() {
        assertNotAuthenticated("esto-no-es-un-jwt");
        assertNotAuthenticated("");
        assertNotAuthenticated(null);
    }

    @Test
    @DisplayName("Sin APP_JWT_SECRET el servicio no se puede crear, con un mensaje claro")
    void missingSecretIsRejected() {
        assertThatThrownBy(() -> new JwtTokenService("", TTL, now::get))
                .isInstanceOf(InvalidJwtSecretException.class)
                .hasMessageContaining("APP_JWT_SECRET");
        assertThatThrownBy(() -> new JwtTokenService(null, TTL, now::get))
                .isInstanceOf(InvalidJwtSecretException.class);
    }

    @Test
    @DisplayName("Un APP_JWT_SECRET de menos de 32 bytes es rechazado, indicando el mínimo")
    void shortSecretIsRejected() {
        assertThatThrownBy(() -> new JwtTokenService("corto", TTL, now::get))
                .isInstanceOf(InvalidJwtSecretException.class)
                .hasMessageContaining("APP_JWT_SECRET")
                .hasMessageContaining(String.valueOf(JwtTokenService.MIN_SECRET_BYTES));
    }

    @Test
    @DisplayName("El análisis de la falla de arranque explica la causa y cómo definir el secreto")
    void failureAnalysisIsClear() {
        InvalidJwtSecretException cause = new InvalidJwtSecretException("Falta APP_JWT_SECRET");

        FailureAnalysis analysis = new JwtSecretFailureAnalyzer().analyze(new IllegalStateException(cause));

        assertThat(analysis).isNotNull();
        assertThat(analysis.getDescription()).contains("APP_JWT_SECRET");
        assertThat(analysis.getAction()).contains("$env:APP_JWT_SECRET");
    }

    private void assertNotAuthenticated(String token) {
        assertThatThrownBy(() -> service.verify(token))
                .isInstanceOfSatisfying(AuthenticationException.class,
                        error -> assertThat(error.type()).isEqualTo(ErrorType.NOT_AUTHENTICATED));
    }

    private static String base64Url(String json) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }
}
