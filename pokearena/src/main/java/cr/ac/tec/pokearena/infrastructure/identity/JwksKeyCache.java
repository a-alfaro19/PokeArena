package cr.ac.tec.pokearena.infrastructure.identity;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;

import cr.ac.tec.pokearena.application.port.Clock;
import cr.ac.tec.pokearena.domain.error.IdentityProviderException;

/**
 * Propósito: llaves públicas de un proveedor de identidad (JWKS), guardadas en memoria (sección 8.1).
 * Responsabilidades: descargar el JWKS, guardarlo el tiempo que indica {@code Cache-Control: max-age},
 * encontrar una llave por su {@code kid} y, si el {@code kid} no está, recargar UNA vez (el proveedor
 * pudo haber rotado sus llaves), como mucho una vez por intervalo mínimo. Si no se pueden obtener las
 * llaves: IDENTITY_PROVIDER_UNAVAILABLE.
 * Referencia: sección 8.1 ("las llaves se guardan en memoria respetando su vencimiento; un token con una
 * llave desconocida fuerza una recarga una sola vez").
 *
 * <p>Decisiones:
 * <ul>
 *   <li>Se descarga con el {@code HttpClient} de Java y nimbus solo interpreta el JSON: así el vencimiento,
 *       la recarga y los errores son decisiones visibles de esta clase y no de la biblioteca.</li>
 *   <li>Si la respuesta no trae {@code max-age}, se guarda {@code app.identity.google.jwks-default-ttl}.
 *       Con {@code no-store} o {@code no-cache} no se guarda (se vuelve a pedir en el siguiente uso).</li>
 *   <li>Una sola descarga a la vez ({@code synchronized}): si varios hilos ven las llaves vencidas o un
 *       {@code kid} desconocido al mismo tiempo, el primero descarga y los demás usan su resultado.</li>
 *   <li>El vencimiento se compara con el puerto {@link Clock}, así las pruebas pueden adelantar el reloj.</li>
 * </ul>
 */
final class JwksKeyCache {

    private static final Logger LOG = LoggerFactory.getLogger(JwksKeyCache.class);

    private static final int HTTP_OK = 200;
    private static final String MAX_AGE = "max-age=";
    private static final String UNAVAILABLE =
            "El proveedor de identidad no responde en este momento. Intente de nuevo en unos minutos.";

    /** Llaves descargadas y hasta cuándo valen. */
    private record Snapshot(JWKSet keys, Instant expiresAt) {
    }

    private final HttpClient http;
    private final URI jwksUri;
    private final Duration timeout;
    private final Duration defaultTtl;
    private final Duration minReloadInterval;
    private final Clock clock;

    // null hasta la primera descarga. volatile: se lee sin bloqueo y se reemplaza bajo el bloqueo.
    private volatile Snapshot snapshot;

    // Último intento de descarga, exitoso o no: un proveedor caído tampoco se consulta sin límite.
    // Solo se lee y se escribe con el bloqueo tomado. Instant.MIN: todavía no hubo ningún intento.
    private Instant lastDownloadAttempt = Instant.MIN;

    JwksKeyCache(URI jwksUri, Duration timeout, Duration defaultTtl, Duration minReloadInterval, Clock clock) {
        if (minReloadInterval == null || minReloadInterval.isNegative()) {
            throw new IllegalStateException("app.identity.google.jwks-min-reload-interval no puede ser negativo");
        }
        this.http = HttpClient.newBuilder().connectTimeout(timeout).build();
        this.jwksUri = jwksUri;
        this.timeout = timeout;
        this.defaultTtl = defaultTtl;
        this.minReloadInterval = minReloadInterval;
        this.clock = clock;
    }

    /**
     * Llave RSA de firma con ese {@code kid}.
     *
     * @return la llave, o vacío si el proveedor no la publica ni después de recargar
     * @throws IdentityProviderException IDENTITY_PROVIDER_UNAVAILABLE si hubo que descargar y no se pudo
     */
    Optional<RSAKey> find(String keyId) {
        Snapshot current = snapshot;
        boolean reloaded = false;
        if (current == null || !clock.now().isBefore(current.expiresAt())) {
            // Primera descarga o llaves vencidas: siempre se descarga, sin mirar el intervalo mínimo.
            current = reload(current);
            reloaded = true;
        }
        Optional<RSAKey> key = lookup(current, keyId);
        if (key.isPresent() || reloaded) {
            return key;
        }
        // kid desconocido con llaves vigentes: el proveedor pudo rotarlas. Se recarga UNA sola vez, y solo
        // si pasó el intervalo mínimo desde el último intento de descarga.
        return forcedReload(current).flatMap(fresh -> lookup(fresh, keyId));
    }

    /**
     * Descarga el JWKS (primera vez o llaves vencidas), salvo que otro hilo lo haya hecho mientras este
     * esperaba el bloqueo.
     *
     * @param observed las llaves que el llamador vio (vencidas o ninguna)
     */
    private synchronized Snapshot reload(Snapshot observed) {
        if (snapshot != observed) {
            return snapshot;
        }
        return download();
    }

    /**
     * Recarga pedida por un {@code kid} desconocido. El endpoint de inicio de sesión es público: sin
     * límite, cualquiera podría forzar descargas ilimitadas a Google con {@code kid} inventados. Por eso
     * solo se descarga si pasó {@code app.identity.google.jwks-min-reload-interval} desde el último
     * intento (exitoso o no); si no, el token se rechaza sin descargar.
     *
     * @return las llaves recargadas (o las que otro hilo acaba de descargar), o vacío si el intervalo no lo permite
     */
    private synchronized Optional<Snapshot> forcedReload(Snapshot observed) {
        if (snapshot != observed) {
            return Optional.of(snapshot);
        }
        if (clock.now().isBefore(lastDownloadAttempt.plus(minReloadInterval))) {
            LOG.debug("kid desconocido: recarga del JWKS omitida porque no pasó el intervalo mínimo");
            return Optional.empty();
        }
        return Optional.of(download());
    }

    /** Descarga e interpreta el JWKS. Debe llamarse con el bloqueo tomado. */
    private Snapshot download() {
        lastDownloadAttempt = clock.now();
        HttpResponse<String> response = fetch();
        try {
            JWKSet keys = JWKSet.parse(response.body());
            Duration ttl = cacheDuration(response);
            snapshot = new Snapshot(keys, clock.now().plus(ttl));
            return snapshot;
        } catch (ParseException failure) {
            LOG.warn("El JWKS del proveedor de identidad ({}) no es un conjunto de llaves válido", jwksUri);
            throw IdentityProviderException.unavailable(UNAVAILABLE);
        }
    }

    private HttpResponse<String> fetch() {
        HttpRequest request = HttpRequest.newBuilder(jwksUri).timeout(timeout).GET().build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != HTTP_OK) {
                LOG.warn("El proveedor de identidad respondió {} al pedir sus llaves ({})", response.statusCode(),
                        jwksUri);
                throw IdentityProviderException.unavailable(UNAVAILABLE);
            }
            return response;
        } catch (IOException failure) {
            LOG.warn("No se pudieron obtener las llaves del proveedor de identidad ({}): {}", jwksUri,
                    failure.toString());
            throw IdentityProviderException.unavailable(UNAVAILABLE);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw IdentityProviderException.unavailable(UNAVAILABLE);
        }
    }

    /** Cuánto se guardan las llaves según {@code Cache-Control}. */
    private Duration cacheDuration(HttpResponse<String> response) {
        Optional<String> header = response.headers().firstValue("Cache-Control");
        if (header.isEmpty()) {
            return defaultTtl;
        }
        for (String directive : header.get().split(",")) {
            String value = directive.trim().toLowerCase(Locale.ROOT);
            if (value.equals("no-store") || value.equals("no-cache")) {
                return Duration.ZERO;
            }
            if (value.startsWith(MAX_AGE)) {
                try {
                    return Duration.ofSeconds(Math.max(0, Long.parseLong(value.substring(MAX_AGE.length()))));
                } catch (NumberFormatException ignored) {
                    return defaultTtl;
                }
            }
        }
        return defaultTtl;
    }

    /** Solo llaves RSA que sirven para firmar (sin {@code use}, o {@code use = sig}). */
    private static Optional<RSAKey> lookup(Snapshot current, String keyId) {
        JWK key = current.keys().getKeyByKeyId(keyId);
        if (key instanceof RSAKey rsa && (rsa.getKeyUse() == null || KeyUse.SIGNATURE.equals(rsa.getKeyUse()))) {
            return Optional.of(rsa);
        }
        return Optional.empty();
    }
}
