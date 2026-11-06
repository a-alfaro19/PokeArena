package cr.ac.tec.pokearena.infrastructure.identity;

import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import cr.ac.tec.pokearena.application.port.AuthenticatedUser;
import cr.ac.tec.pokearena.application.port.Clock;
import cr.ac.tec.pokearena.application.port.TokenIssuer;
import cr.ac.tec.pokearena.application.port.TokenVerifier;
import cr.ac.tec.pokearena.domain.error.AuthenticationException;
import cr.ac.tec.pokearena.domain.model.Role;

/**
 * Propósito: credenciales propias de PokéArena como JWT firmados con HS256 (sección 5.2).
 * Responsabilidades: emitir tokens con {@code sub} (id del jugador), {@code role}, emisor y
 * vencimiento, y verificarlos (algoritmo, firma, emisor y vencimiento). La biblioteca
 * nimbus-jose-jwt solo firma y verifica; qué se firma y qué se acepta lo decide esta clase.
 * Referencia: sección 5.2.
 *
 * <p>Decisiones:
 * <ul>
 *   <li>HS256 (secreto compartido): la misma aplicación emite y verifica, y las 2 instancias
 *       comparten {@code APP_JWT_SECRET}. No hace falta un par de llaves.</li>
 *   <li>El secreto debe tener al menos 32 bytes: HS256 exige una llave del tamaño del hash
 *       (256 bits, RFC 7518, sección 3.2). Si falta o es más corto, la aplicación no arranca.</li>
 *   <li>Solo se acepta HS256 en el encabezado: un token con otro algoritmo (incluido {@code none})
 *       se rechaza antes de mirar su contenido.</li>
 *   <li>El emisor {@code pokearena} distingue estas credenciales de cualquier otro JWT, como el
 *       token de Google, que además está firmado con otro algoritmo.</li>
 *   <li>El vencimiento se compara con el puerto {@link Clock}, sin margen: la misma aplicación
 *       emite y verifica. Así una prueba puede emitir un token ya vencido con un reloj fijo.</li>
 *   <li>Cualquier falla de verificación es el mismo error NOT_AUTHENTICATED, sin decir cuál
 *       comprobación falló, para no orientar a quien intente falsificar un token.</li>
 * </ul>
 */
@Component
public class JwtTokenService implements TokenIssuer, TokenVerifier {

    /** Tamaño mínimo del secreto para HS256: 256 bits. */
    public static final int MIN_SECRET_BYTES = 32;

    /** Emisor de las credenciales propias. */
    static final String ISSUER = "pokearena";

    /** Claim con el rol del jugador. */
    static final String ROLE_CLAIM = "role";

    private static final String INVALID_TOKEN = "La sesión no es válida o venció. Inicie sesión de nuevo.";

    private final MACSigner signer;
    private final MACVerifier verifier;
    private final Duration accessTokenTtl;
    private final Clock clock;

    public JwtTokenService(@Value("${app.security.jwt-secret:}") String secret,
            @Value("${app.security.access-token-ttl}") Duration accessTokenTtl, Clock clock) {
        byte[] key = validSecret(secret);
        if (accessTokenTtl == null || accessTokenTtl.isNegative() || accessTokenTtl.isZero()) {
            throw new IllegalStateException("app.security.access-token-ttl debe ser una duración positiva");
        }
        try {
            this.signer = new MACSigner(key);
            this.verifier = new MACVerifier(key);
        } catch (JOSEException exception) {
            throw new InvalidJwtSecretException("APP_JWT_SECRET no sirve como llave HS256: " + exception.getMessage());
        }
        this.accessTokenTtl = accessTokenTtl;
        this.clock = clock;
    }

    @Override
    public IssuedToken issue(AuthenticatedUser user) {
        // JWT guarda las fechas en segundos: se truncan antes para que expiresAt sea exacto.
        Instant issuedAt = clock.now().truncatedTo(ChronoUnit.SECONDS);
        Instant expiresAt = issuedAt.plus(accessTokenTtl);
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .subject(user.playerId().toString())
                .claim(ROLE_CLAIM, user.role().name())
                .issueTime(Date.from(issuedAt))
                .expirationTime(Date.from(expiresAt))
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        try {
            jwt.sign(signer);
        } catch (JOSEException exception) {
            throw new IllegalStateException("No se pudo firmar el token", exception);
        }
        return new IssuedToken(jwt.serialize(), accessTokenTtl, expiresAt);
    }

    @Override
    public AuthenticatedUser verify(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);

            // 1. Algoritmo: solo HS256. 2. Firma: con nuestro secreto.
            if (!JWSAlgorithm.HS256.equals(jwt.getHeader().getAlgorithm()) || !jwt.verify(verifier)) {
                throw invalid();
            }

            // 3. Emisor y 4. vencimiento.
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            Date expiration = claims.getExpirationTime();
            if (!ISSUER.equals(claims.getIssuer()) || expiration == null
                    || !clock.now().isBefore(expiration.toInstant())) {
                throw invalid();
            }

            // 5. Contenido: el sub es un UUID y el rol uno de los conocidos.
            return new AuthenticatedUser(UUID.fromString(claims.getSubject()),
                    Role.valueOf(claims.getStringClaim(ROLE_CLAIM)));
        } catch (ParseException | JOSEException | IllegalArgumentException | NullPointerException exception) {
            // Texto que no es un JWT, firma que no se puede calcular, sub o rol con formato inválido.
            throw invalid();
        }
    }

    private static byte[] validSecret(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new InvalidJwtSecretException(
                    "Falta el secreto de firma de las credenciales: la variable de entorno APP_JWT_SECRET no está definida.");
        }
        byte[] key = secret.getBytes(StandardCharsets.UTF_8);
        if (key.length < MIN_SECRET_BYTES) {
            throw new InvalidJwtSecretException("APP_JWT_SECRET es muy corto: tiene " + key.length
                    + " bytes y HS256 necesita al menos " + MIN_SECRET_BYTES + ".");
        }
        return key;
    }

    private static AuthenticationException invalid() {
        return AuthenticationException.notAuthenticated(INVALID_TOKEN);
    }
}
