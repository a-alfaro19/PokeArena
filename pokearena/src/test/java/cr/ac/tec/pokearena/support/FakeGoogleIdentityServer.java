package cr.ac.tec.pokearena.support;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * Propósito: proveedor de identidad simulado para las pruebas del inicio de sesión (sección 13, fila
 * "Identidad"; sección 8.1: "llaves RSA generadas en la prueba y un servidor local que sirve el JWKS").
 * Responsabilidades: generar llaves RSA, publicar las públicas en un JWKS servido por un
 * {@code com.sun.net.httpserver.HttpServer} local (del JDK, sin dependencias nuevas), firmar tokens con la
 * forma de los de Google, contar las descargas del JWKS y simular que el proveedor no responde.
 * Referencia: secciones 8.1 y 13.
 */
public final class FakeGoogleIdentityServer implements AutoCloseable {

    /** Emisor con el que firma Google. */
    public static final String ISSUER = "https://accounts.google.com";

    /** Tamaño de las llaves RSA de prueba (el mínimo recomendado para RS256). */
    private static final int RSA_KEY_BITS = 2048;

    /** Validez por defecto de los tokens de prueba, en segundos (los de Google duran una hora). */
    private static final long TOKEN_VALIDITY_SECONDS = 3600;

    private static final String JWKS_PATH = "/oauth2/v3/certs";
    private static final int HTTP_OK = 200;
    private static final int HTTP_UNAVAILABLE = 503;

    private final HttpServer server;
    private final AtomicInteger jwksRequests = new AtomicInteger();
    private volatile List<RSAKey> published = List.of();
    private volatile boolean down;
    private volatile String cacheControl = "public, max-age=3600";

    private FakeGoogleIdentityServer(HttpServer server) {
        this.server = server;
    }

    /** Arranca el servidor en un puerto libre de la máquina local. */
    public static FakeGoogleIdentityServer start() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            FakeGoogleIdentityServer fake = new FakeGoogleIdentityServer(server);
            server.createContext(JWKS_PATH, fake::serveJwks);
            server.setExecutor(Executors.newCachedThreadPool());
            server.start();
            return fake;
        } catch (IOException failure) {
            throw new IllegalStateException("No se pudo arrancar el servidor JWKS de prueba", failure);
        }
    }

    /** URL del JWKS, para {@code app.identity.google.jwks-uri}. */
    public URI jwksUri() {
        return URI.create("http://" + server.getAddress().getHostString() + ":" + server.getAddress().getPort()
                + JWKS_PATH);
    }

    /** Genera una llave RSA nueva con un {@code kid} único. No la publica. */
    public static RSAKey newKey() {
        try {
            return new RSAKeyGenerator(RSA_KEY_BITS).keyID(UUID.randomUUID().toString()).keyUse(KeyUse.SIGNATURE)
                    .generate();
        } catch (JOSEException failure) {
            throw new IllegalStateException(failure);
        }
    }

    /** Reemplaza las llaves publicadas (solo su parte pública sale en el JWKS), como una rotación. */
    public void publish(RSAKey... keys) {
        published = List.of(keys);
    }

    /** {@code true}: el JWKS responde 503, como un proveedor caído. */
    public void setDown(boolean down) {
        this.down = down;
    }

    /** Encabezado {@code Cache-Control} del JWKS. */
    public void setCacheControl(String cacheControl) {
        this.cacheControl = cacheControl;
    }

    /** Cuántas veces se pidió el JWKS. */
    public int jwksRequests() {
        return jwksRequests.get();
    }

    /** Un token con valores válidos firmado con {@code key}; cada valor se puede cambiar. */
    public static TokenBuilder token(RSAKey key, String audience, Instant now) {
        return new TokenBuilder(key, audience, now);
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private void serveJwks(HttpExchange exchange) throws IOException {
        jwksRequests.incrementAndGet();
        if (down) {
            exchange.sendResponseHeaders(HTTP_UNAVAILABLE, -1);
            exchange.close();
            return;
        }
        List<JWK> publicKeys = published.stream().map(key -> (JWK) key.toPublicJWK()).toList();
        byte[] body = new JWKSet(publicKeys).toString().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.getResponseHeaders().add("Cache-Control", cacheControl);
        exchange.sendResponseHeaders(HTTP_OK, body.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(body);
        }
    }

    /** Arma y firma un token con la forma de los de Google. */
    public static final class TokenBuilder {

        private final RSAKey key;
        private String keyId;
        private RSAKey signingKey;
        private final JWTClaimsSet.Builder claims;

        private TokenBuilder(RSAKey key, String audience, Instant now) {
            this.key = key;
            this.keyId = key.getKeyID();
            this.signingKey = key;
            this.claims = new JWTClaimsSet.Builder()
                    .issuer(ISSUER)
                    .audience(audience)
                    .subject("sub-" + UUID.randomUUID())
                    .claim("email", "jugador@example.com")
                    .claim("name", "Jugador de prueba")
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(now.plusSeconds(TOKEN_VALIDITY_SECONDS)));
        }

        public TokenBuilder subject(String subject) {
            claims.subject(subject);
            return this;
        }

        public TokenBuilder issuer(String issuer) {
            claims.issuer(issuer);
            return this;
        }

        public TokenBuilder audience(String audience) {
            claims.audience(audience);
            return this;
        }

        public TokenBuilder expiresAt(Instant expiresAt) {
            claims.expirationTime(Date.from(expiresAt));
            return this;
        }

        public TokenBuilder email(String email) {
            claims.claim("email", email);
            return this;
        }

        public TokenBuilder name(String name) {
            claims.claim("name", name);
            return this;
        }

        /** Sin nombre ni correo, como algunos tokens de Google. */
        public TokenBuilder withoutProfile() {
            claims.claim("email", null).claim("name", null);
            return this;
        }

        /** Firma con otra llave privada, pero declara el {@code kid} de la llave publicada: firma inválida. */
        public TokenBuilder signedWithForeignKey(RSAKey foreign) {
            this.signingKey = foreign;
            return this;
        }

        public TokenBuilder keyId(String keyId) {
            this.keyId = keyId;
            return this;
        }

        public String sign() {
            try {
                SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(keyId).build(),
                        claims.build());
                jwt.sign(new RSASSASigner(signingKey));
                return jwt.serialize();
            } catch (JOSEException failure) {
                throw new IllegalStateException(failure);
            }
        }

        /** El mismo contenido firmado con HS256 usando un secreto: un algoritmo que no es de Google. */
        public String signHs256(byte[] secret) {
            try {
                SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.HS256).keyID(key.getKeyID()).build(),
                        claims.build());
                jwt.sign(new MACSigner(secret));
                return jwt.serialize();
            } catch (JOSEException failure) {
                throw new IllegalStateException(failure);
            }
        }
    }
}
