package cr.ac.tec.pokearena.infrastructure.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import com.nimbusds.jose.jwk.RSAKey;

import cr.ac.tec.pokearena.application.port.Clock;
import cr.ac.tec.pokearena.application.port.IdentityProviderRegistry;
import cr.ac.tec.pokearena.application.port.VerifiedIdentity;
import cr.ac.tec.pokearena.domain.error.DomainException;
import cr.ac.tec.pokearena.domain.error.ErrorType;
import cr.ac.tec.pokearena.support.FakeGoogleIdentityServer;

/**
 * Propósito: pruebas unitarias del verificador de tokens de Google (historia 1.1.1, criterio C.4).
 * Responsabilidades: comprobar cada verificación (algoritmo, firma, emisor, audiencia y vencimiento), que
 * todas fallan con el mismo error sin decir cuál, el guardado de las llaves según {@code Cache-Control},
 * la recarga única ante un {@code kid} desconocido y el proveedor caído. Sin Spring: un servidor JWKS
 * local con llaves RSA generadas en la prueba y un reloj que la prueba controla.
 * Referencia: sección 8.1, pasos 3 y 4.
 */
@DisplayName("Verificador de tokens de Google")
class GoogleIdentityProviderTest {

    private static final String CLIENT_ID = "pokearena-pruebas.apps.googleusercontent.com";
    private static final Instant NOW = Instant.parse("2026-11-07T12:00:00Z");
    private static final Duration TIMEOUT = Duration.ofSeconds(2);
    private static final Duration DEFAULT_TTL = Duration.ofMinutes(5);
    private static final long ONE_HOUR_SECONDS = 3600;
    private static final Duration MIN_RELOAD_INTERVAL = Duration.ofMinutes(1);
    private static final int INVENTED_KIDS = 5;

    private final FakeGoogleIdentityServer google = FakeGoogleIdentityServer.start();
    private final RSAKey key = FakeGoogleIdentityServer.newKey();
    private final AtomicReference<Instant> now = new AtomicReference<>(NOW);
    private final Clock clock = now::get;

    private final GoogleIdentityProvider provider = providerFor(google.jwksUri(), CLIENT_ID);

    {
        google.publish(key);
    }

    @AfterEach
    void stopServer() {
        google.close();
    }

    private GoogleIdentityProvider providerFor(URI jwksUri, String clientId) {
        return new GoogleIdentityProvider(clientId, jwksUri, TIMEOUT, DEFAULT_TTL, MIN_RELOAD_INTERVAL, clock);
    }

    private FakeGoogleIdentityServer.TokenBuilder token() {
        return FakeGoogleIdentityServer.token(key, CLIENT_ID, now.get());
    }

    private static void assertInvalidToken(Executable verification) {
        assertThatThrownBy(verification::execute)
                .isInstanceOfSatisfying(DomainException.class, error -> {
                    assertThat(error.type()).isEqualTo(ErrorType.INVALID_IDENTITY_TOKEN);
                    // El mismo mensaje para todas las fallas: no dice cuál comprobación falló.
                    assertThat(error.getMessage())
                            .isEqualTo("El token de Google no es válido o venció. Vuelva a iniciar sesión con Google.");
                });
    }

    @Nested
    @DisplayName("Éxito")
    class Success {

        @Test
        @DisplayName("C.4: un token válido emitido por Google para PokéArena devuelve la identidad verificada")
        void validToken() {
            VerifiedIdentity identity = provider.verify(token().subject("1234567890").email("ana@example.com")
                    .name("Ana").sign());

            assertThat(identity.provider()).isEqualTo("google");
            assertThat(identity.subject()).isEqualTo("1234567890");
            assertThat(identity.email()).isEqualTo("ana@example.com");
            assertThat(identity.displayName()).isEqualTo("Ana");
        }

        @Test
        @DisplayName("Acepta los dos emisores de Google: accounts.google.com y https://accounts.google.com")
        void bothIssuers() {
            assertThat(provider.verify(token().issuer("accounts.google.com").sign()).provider()).isEqualTo("google");
            assertThat(provider.verify(token().issuer("https://accounts.google.com").sign()).provider())
                    .isEqualTo("google");
        }

        @Test
        @DisplayName("Un token sin nombre ni correo es válido: ambos quedan nulos")
        void tokenWithoutProfile() {
            VerifiedIdentity identity = provider.verify(token().withoutProfile().sign());

            assertThat(identity.email()).isNull();
            assertThat(identity.displayName()).isNull();
        }
    }

    @Nested
    @DisplayName("Errores: INVALID_IDENTITY_TOKEN, siempre con el mismo mensaje")
    class InvalidTokens {

        @Test
        @DisplayName("C.4: un token que no fue emitido por el proveedor (otro emisor) es rechazado")
        void otherIssuer() {
            assertInvalidToken(() -> provider.verify(token().issuer("https://emisor-falso.example.com").sign()));
        }

        @Test
        @DisplayName("C.4: un token expirado es rechazado, incluso en el instante exacto de su vencimiento")
        void expired() {
            assertInvalidToken(() -> provider.verify(token().expiresAt(NOW.minusSeconds(1)).sign()));
            assertInvalidToken(() -> provider.verify(token().expiresAt(NOW).sign()));
        }

        @Test
        @DisplayName("C.4: un token con la firma inválida es rechazado")
        void invalidSignature() {
            RSAKey attacker = FakeGoogleIdentityServer.newKey();

            assertInvalidToken(() -> provider.verify(token().signedWithForeignKey(attacker).sign()));
        }

        @Test
        @DisplayName("C.4: un token emitido para otra aplicación (otra audiencia) es rechazado")
        void otherAudience() {
            assertInvalidToken(() -> provider.verify(token().audience("otra-aplicacion.apps.googleusercontent.com")
                    .sign()));
        }

        @Test
        @DisplayName("Solo se acepta RS256: un token HS256 (por ejemplo, firmado con la llave pública como secreto) es rechazado")
        void onlyRs256() {
            byte[] secret = key.toPublicJWK().toJSONString().getBytes(StandardCharsets.UTF_8);

            assertInvalidToken(() -> provider.verify(token().signHs256(secret)));
        }

        @Test
        @DisplayName("Un texto que no es un JWT es rechazado")
        void notAJwt() {
            assertInvalidToken(() -> provider.verify("esto-no-es-un-token"));
        }

        @Test
        @DisplayName("Un token sin sub es rechazado")
        void withoutSubject() {
            assertInvalidToken(() -> provider.verify(token().subject(null).sign()));
        }
    }

    @Nested
    @DisplayName("Llaves del proveedor (JWKS)")
    class Keys {

        @Test
        @DisplayName("Las llaves se guardan en memoria mientras dura su max-age: dos ingresos, una descarga")
        void keysAreCached() {
            provider.verify(token().sign());
            provider.verify(token().sign());

            assertThat(google.jwksRequests()).isEqualTo(1);
        }

        @Test
        @DisplayName("Al vencer el max-age las llaves se vuelven a descargar")
        void keysExpireWithMaxAge() {
            provider.verify(token().sign());
            now.set(NOW.plusSeconds(ONE_HOUR_SECONDS));

            provider.verify(token().sign());

            assertThat(google.jwksRequests()).isEqualTo(2);
        }

        @Test
        @DisplayName("Sin max-age, las llaves se guardan app.identity.google.jwks-default-ttl")
        void defaultTtlWithoutMaxAge() {
            google.setCacheControl("public");
            provider.verify(token().sign());
            now.set(NOW.plus(DEFAULT_TTL).minusSeconds(1));
            provider.verify(token().sign());
            assertThat(google.jwksRequests()).isEqualTo(1);

            now.set(NOW.plus(DEFAULT_TTL));
            provider.verify(token().sign());
            assertThat(google.jwksRequests()).isEqualTo(2);
        }

        @Test
        @DisplayName("8.1: un kid desconocido fuerza UNA recarga: Google rotó sus llaves y el token nuevo es válido")
        void unknownKidForcesOneReload() {
            provider.verify(token().sign());
            RSAKey rotated = FakeGoogleIdentityServer.newKey();
            google.publish(rotated);
            now.set(NOW.plus(MIN_RELOAD_INTERVAL));

            VerifiedIdentity identity = provider.verify(
                    FakeGoogleIdentityServer.token(rotated, CLIENT_ID, now.get()).subject("rotado").sign());

            assertThat(identity.subject()).isEqualTo("rotado");
            assertThat(google.jwksRequests()).isEqualTo(2);
        }

        @Test
        @DisplayName("8.1: un kid que el proveedor no publica recarga una sola vez y se rechaza, sin repetir la descarga")
        void unknownKidReloadsOnlyOnce() {
            provider.verify(token().sign());
            now.set(NOW.plus(MIN_RELOAD_INTERVAL));

            assertInvalidToken(() -> provider.verify(token().keyId("kid-que-no-existe").sign()));
            assertThat(google.jwksRequests()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("Límite de recargas por kid desconocido (app.identity.google.jwks-min-reload-interval)")
    class ReloadLimit {

        private void verifyInventedKids() {
            for (int i = 0; i < INVENTED_KIDS; i++) {
                String inventedKid = "kid-inventado-" + i;
                assertInvalidToken(() -> provider.verify(token().keyId(inventedKid).sign()));
            }
        }

        @Test
        @DisplayName("Varios tokens seguidos con kids inventados producen UNA sola descarga")
        void inventedKidsProduceOneDownload() {
            provider.verify(token().sign());
            now.set(NOW.plus(MIN_RELOAD_INTERVAL));

            verifyInventedKids();

            assertThat(google.jwksRequests()).isEqualTo(2);
        }

        @Test
        @DisplayName("Dentro del intervalo desde la última descarga, un kid inventado se rechaza sin descargar")
        void withinIntervalNoDownload() {
            provider.verify(token().sign());

            verifyInventedKids();

            assertThat(google.jwksRequests()).isEqualTo(1);
        }

        @Test
        @DisplayName("Pasado el intervalo (reloj de pruebas) se permite otra recarga")
        void afterIntervalAnotherReload() {
            provider.verify(token().sign());
            now.set(NOW.plus(MIN_RELOAD_INTERVAL));
            verifyInventedKids();
            now.set(NOW.plus(MIN_RELOAD_INTERVAL).plus(MIN_RELOAD_INTERVAL).minusSeconds(1));
            verifyInventedKids();
            assertThat(google.jwksRequests()).isEqualTo(2);

            now.set(NOW.plus(MIN_RELOAD_INTERVAL).plus(MIN_RELOAD_INTERVAL));
            verifyInventedKids();

            assertThat(google.jwksRequests()).isEqualTo(3);
        }

        @Test
        @DisplayName("Un kid legítimo nuevo se rechaza dentro del intervalo y se acepta tras la recarga permitida")
        void legitimateNewKidAcceptedAfterAllowedReload() {
            provider.verify(token().sign());
            RSAKey rotated = FakeGoogleIdentityServer.newKey();
            google.publish(key, rotated);
            String rotatedToken = FakeGoogleIdentityServer.token(rotated, CLIENT_ID, NOW).subject("rotado").sign();

            assertInvalidToken(() -> provider.verify(rotatedToken));
            assertThat(google.jwksRequests()).isEqualTo(1);

            now.set(NOW.plus(MIN_RELOAD_INTERVAL));
            assertThat(provider.verify(rotatedToken).subject()).isEqualTo("rotado");
            assertThat(google.jwksRequests()).isEqualTo(2);
        }

        @Test
        @DisplayName("El intervalo no bloquea la primera descarga ni la recarga normal por vencimiento del max-age")
        void doesNotBlockFirstOrExpiryDownload() {
            Duration shortMaxAge = MIN_RELOAD_INTERVAL.dividedBy(2);
            google.setCacheControl("max-age=" + shortMaxAge.toSeconds());

            provider.verify(token().sign());
            assertThat(google.jwksRequests()).isEqualTo(1);

            now.set(NOW.plus(shortMaxAge));
            provider.verify(token().sign());

            assertThat(google.jwksRequests()).isEqualTo(2);
        }

        @Test
        @DisplayName("Un intento fallido también cuenta: con el proveedor caído no se reintenta en cada kid inventado")
        void failedAttemptCounts() {
            provider.verify(token().sign());
            google.setDown(true);
            now.set(NOW.plus(MIN_RELOAD_INTERVAL));

            assertThatThrownBy(() -> provider.verify(token().keyId("kid-inventado").sign()))
                    .isInstanceOfSatisfying(DomainException.class,
                            error -> assertThat(error.type()).isEqualTo(ErrorType.IDENTITY_PROVIDER_UNAVAILABLE));
            verifyInventedKids();

            assertThat(google.jwksRequests()).isEqualTo(2);
        }

        @Test
        @DisplayName("Un token con algoritmo o formato inválido se rechaza sin consultar al proveedor")
        void invalidTokenDoesNotDownloadKeys() {
            assertInvalidToken(() -> provider.verify("esto-no-es-un-token"));

            assertThat(google.jwksRequests()).isZero();
        }
    }

    @Nested
    @DisplayName("Proveedor no disponible")
    class Unavailable {

        @Test
        @DisplayName("C.4: si el proveedor responde con error al pedir sus llaves, IDENTITY_PROVIDER_UNAVAILABLE")
        void providerReturnsError() {
            google.setDown(true);

            assertUnavailable(() -> provider.verify(token().sign()));
        }

        @Test
        @DisplayName("C.4: si el proveedor no acepta conexiones, IDENTITY_PROVIDER_UNAVAILABLE")
        void providerUnreachable() throws Exception {
            GoogleIdentityProvider unreachable = providerFor(closedPortUri(), CLIENT_ID);

            assertUnavailable(() -> unreachable.verify(token().sign()));
        }

        @Test
        @DisplayName("Con las llaves ya guardadas, un proveedor caído no impide verificar mientras no venzan")
        void cachedKeysSurviveOutage() {
            provider.verify(token().sign());
            google.setDown(true);

            assertThat(provider.verify(token().sign()).provider()).isEqualTo("google");
        }

        private void assertUnavailable(Executable verification) {
            assertThatThrownBy(verification::execute)
                    .isInstanceOfSatisfying(DomainException.class,
                            error -> assertThat(error.type()).isEqualTo(ErrorType.IDENTITY_PROVIDER_UNAVAILABLE));
        }

        private URI closedPortUri() throws Exception {
            int port;
            try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
                port = socket.getLocalPort();
            }
            return URI.create("http://127.0.0.1:" + port + "/certs");
        }
    }

    @Nested
    @DisplayName("Configuración")
    class Configuration {

        @Test
        @DisplayName("Sin client id el proveedor queda deshabilitado y el registro responde UNSUPPORTED_PROVIDER")
        void withoutClientIdIsDisabled() {
            GoogleIdentityProvider disabled = providerFor(google.jwksUri(), "  ");
            IdentityProviderRegistry registry = new IdentityProviderRegistry(java.util.List.of(disabled));

            assertThat(disabled.isEnabled()).isFalse();
            assertThatThrownBy(() -> registry.get("google"))
                    .isInstanceOfSatisfying(DomainException.class,
                            error -> assertThat(error.type()).isEqualTo(ErrorType.UNSUPPORTED_PROVIDER));
        }
    }
}
