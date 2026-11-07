package cr.ac.tec.pokearena.infrastructure.identity;

import java.net.URI;
import java.text.ParseException;
import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import cr.ac.tec.pokearena.application.port.Clock;
import cr.ac.tec.pokearena.application.port.IdentityProvider;
import cr.ac.tec.pokearena.application.port.VerifiedIdentity;
import cr.ac.tec.pokearena.domain.error.AuthenticationException;
import cr.ac.tec.pokearena.domain.error.IdentityProviderException;

/**
 * Propósito: verificador de los tokens de identidad de Google (participante "Verificador de Google" del
 * diagrama A.4).
 * Responsabilidades: comprobar que el token lo emitió Google para PokéArena y no venció: algoritmo RS256,
 * firma con las llaves públicas de Google (JWKS), emisor, vencimiento y audiencia; y devolver la identidad
 * verificada ({@code sub}, correo y nombre). nimbus-jose-jwt solo interpreta el token y calcula la firma;
 * qué se acepta lo decide esta clase (RES-01).
 * Referencia: sección 8.1, pasos 3 y 4, y criterio C.4.
 *
 * <p>Decisiones:
 * <ul>
 *   <li>Solo RS256, el algoritmo con el que firma Google. Cualquier otro (incluido {@code none} o HS256,
 *       con el que alguien podría intentar firmar usando la llave pública como secreto) se rechaza antes
 *       de buscar la llave.</li>
 *   <li>Orden: primero la firma y después los claims. Así nunca se confía en el contenido de un token sin
 *       firma válida.</li>
 *   <li>Emisor: {@code accounts.google.com} o {@code https://accounts.google.com}, los dos valores que
 *       documenta Google. Audiencia: exactamente el {@code client id} configurado ({@code APP_GOOGLE_CLIENT_ID}).</li>
 *   <li>Vencimiento con el puerto {@link Clock}, sin margen de reloj: el token se verifica al recibirlo y
 *       solo se valida {@code exp} (se acepta si el momento actual es anterior). Así una prueba puede
 *       comprobar el borde exacto con un reloj fijo.</li>
 *   <li>Un {@code kid} desconocido fuerza una recarga del JWKS como mucho una vez por
 *       {@code app.identity.google.jwks-min-reload-interval}: el endpoint es público y, sin límite,
 *       cualquiera podría provocar descargas ilimitadas con {@code kid} inventados.</li>
 *   <li>Cualquier falla de verificación es el mismo INVALID_IDENTITY_TOKEN con el mismo mensaje, sin decir
 *       cuál comprobación falló, para no orientar a quien intente falsificar un token. La causa queda en el
 *       log en nivel DEBUG, sin el token.</li>
 *   <li>Si las llaves no se pueden obtener, IDENTITY_PROVIDER_UNAVAILABLE (503). Como la verificación
 *       ocurre antes de la transacción del caso de uso, no se crea ninguna cuenta.</li>
 *   <li>Sin {@code client id} configurado, el proveedor queda deshabilitado ({@link #isEnabled()} es
 *       {@code false}): el registro responde UNSUPPORTED_PROVIDER y la aplicación arranca normalmente, por
 *       ejemplo en desarrollo con el perfil dev.</li>
 * </ul>
 */
@Component
public class GoogleIdentityProvider implements IdentityProvider {

    /** Nombre del proveedor en {@code POST /api/v1/sesiones} y en {@code player_identity.provider}. */
    public static final String NAME = "google";

    /** Emisores válidos de los tokens de Google. */
    static final Set<String> ISSUERS = Set.of("accounts.google.com", "https://accounts.google.com");

    static final String EMAIL_CLAIM = "email";
    static final String NAME_CLAIM = "name";

    private static final Logger LOG = LoggerFactory.getLogger(GoogleIdentityProvider.class);

    private static final String INVALID_TOKEN =
            "El token de Google no es válido o venció. Vuelva a iniciar sesión con Google.";

    private final String clientId;
    private final JwksKeyCache keys;
    private final Clock clock;

    /**
     * @param clientId   {@code client id} de PokéArena en Google; vacío deshabilita el proveedor
     * @param jwksUri    URL de las llaves públicas de Google (en las pruebas, un servidor local)
     * @param timeout    tiempo máximo para obtener las llaves
     * @param defaultTtl cuánto guardar las llaves si la respuesta no trae {@code max-age}
     * @param minReloadInterval tiempo mínimo entre dos recargas forzadas por un {@code kid} desconocido
     */
    public GoogleIdentityProvider(@Value("${app.identity.google.client-id:}") String clientId,
            @Value("${app.identity.google.jwks-uri}") URI jwksUri,
            @Value("${app.identity.google.jwks-timeout}") Duration timeout,
            @Value("${app.identity.google.jwks-default-ttl}") Duration defaultTtl,
            @Value("${app.identity.google.jwks-min-reload-interval}") Duration minReloadInterval, Clock clock) {
        this.clientId = clientId == null ? "" : clientId.trim();
        this.keys = new JwksKeyCache(jwksUri, timeout, defaultTtl, minReloadInterval, clock);
        this.clock = clock;
        if (this.clientId.isEmpty()) {
            LOG.warn("El inicio de sesión con Google está deshabilitado: defina APP_GOOGLE_CLIENT_ID para habilitarlo");
        }
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean isEnabled() {
        return !clientId.isEmpty();
    }

    @Override
    public VerifiedIdentity verify(String idToken) {
        if (!isEnabled()) {
            // El registro no ofrece un proveedor deshabilitado; esto solo protege un uso directo.
            throw IdentityProviderException.unsupported("El inicio de sesión con Google no está configurado.",
                    List.of());
        }
        try {
            SignedJWT jwt = SignedJWT.parse(idToken);

            // 1. Algoritmo: solo RS256, y con el identificador de la llave.
            JWSHeader header = jwt.getHeader();
            if (!JWSAlgorithm.RS256.equals(header.getAlgorithm()) || header.getKeyID() == null) {
                throw invalid("algoritmo distinto de RS256 o sin kid");
            }

            // 2. Firma con la llave pública de Google. Un kid desconocido fuerza UNA recarga del JWKS;
            //    si Google no responde, IDENTITY_PROVIDER_UNAVAILABLE sale de aquí.
            RSAKey key = keys.find(header.getKeyID()).orElseThrow(() -> invalid("kid desconocido"));
            if (!jwt.verify(new RSASSAVerifier(key))) {
                throw invalid("firma inválida");
            }

            // 3. Emisor, 4. audiencia y 5. vencimiento.
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            if (claims.getIssuer() == null || !ISSUERS.contains(claims.getIssuer())) {
                throw invalid("emisor");
            }
            if (!List.of(clientId).equals(claims.getAudience())) {
                throw invalid("audiencia");
            }
            Date expiration = claims.getExpirationTime();
            if (expiration == null || !clock.now().isBefore(expiration.toInstant())) {
                throw invalid("vencido");
            }

            // 6. Identidad: el sub es obligatorio; correo y nombre pueden faltar.
            String subject = claims.getSubject();
            if (subject == null || subject.isBlank()) {
                throw invalid("sin sub");
            }
            return new VerifiedIdentity(NAME, subject, claims.getStringClaim(EMAIL_CLAIM),
                    claims.getStringClaim(NAME_CLAIM));
        } catch (ParseException | JOSEException failure) {
            // Texto que no es un JWT, claims con un tipo inesperado o una llave que no sirve para verificar.
            throw invalid(failure.getClass().getSimpleName());
        }
    }

    private static AuthenticationException invalid(String reason) {
        LOG.debug("Token de Google rechazado: {}", reason);
        return AuthenticationException.invalidIdentityToken(INVALID_TOKEN);
    }
}
