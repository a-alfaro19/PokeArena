package cr.ac.tec.pokearena.infrastructure.cache;

import java.time.Duration;
import java.util.Optional;
import java.util.function.LongSupplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import cr.ac.tec.pokearena.application.dto.Page;
import cr.ac.tec.pokearena.application.port.CatalogCache;
import cr.ac.tec.pokearena.application.port.CatalogCacheKey;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.json.JsonMapper;

/**
 * Propósito: caché de páginas del catálogo y de la búsqueda en Redis (sección 9, cache-aside).
 * Responsabilidades: guardar y leer páginas como JSON con vencimiento, con la clave
 * {@code catalog:v<versión>:<espacio>:<hash de filtros>:<cursor>:<limit>}, e invalidarlas todas
 * incrementando {@code catalog:version}. Nunca lanza errores al caso de uso.
 * Referencia: sección 9 ("Política de caché") y Anexos A.3 y A.6.
 *
 * <p>Decisiones:
 * <ul>
 *   <li><b>Invalidación por versión:</b> en lugar de buscar y borrar claves (costoso y no atómico con varias
 *       instancias), cada clave lleva la versión vigente. Crear o cambiar una carta incrementa la versión
 *       ({@code INCR}, atómico); las claves viejas ya no se leen y vencen solas por su TTL.</li>
 *   <li><b>Falla de Redis = caché vacía:</b> cualquier excepción (conexión, tiempo de espera, datos que no
 *       se pueden leer) se registra como aviso y se trata como "la página no está"; el caso de uso consulta la
 *       base. Los tiempos de espera de Redis son cortos y configurables ({@code REDIS_TIMEOUT}).</li>
 *   <li><b>Pausa tras una falla:</b> después de un error, {@link #get} y {@link #put} no vuelven a intentar
 *       durante {@code app.cache.failure-backoff}. Así, con Redis caído, solo una solicitud cada tanto paga el
 *       tiempo de espera, en lugar de todas. {@link #invalidate} sí intenta siempre: es la que evita mostrar
 *       datos viejos.</li>
 *   <li><b>Carrera conocida y aceptada:</b> si una carta se crea entre que una solicitud lee la página de la
 *       base y la guarda, esa página puede quedar sin la carta nueva hasta su vencimiento (30 s por
 *       defecto). Es lo que permite el criterio ("una carta recién creada puede tardar algunos segundos").</li>
 * </ul>
 */
public class RedisCatalogCache implements CatalogCache {

    /** Clave con la versión vigente del catálogo (sección 9). */
    public static final String VERSION_KEY = "catalog:version";

    /** Prefijo de las claves de página; le sigue la versión. */
    public static final String PAGE_KEY_PREFIX = "catalog:v";

    /** Versión cuando {@code catalog:version} todavía no existe. */
    private static final String INITIAL_VERSION = "0";

    private static final Logger LOG = LoggerFactory.getLogger(RedisCatalogCache.class);

    private final StringRedisTemplate redis;
    private final JsonMapper json;
    private final Duration ttl;
    private final long failureBackoffNanos;
    private final LongSupplier nanoTime;

    /** Momento (en {@code nanoTime}) hasta el que no se usa Redis tras una falla, si {@code paused}. */
    private volatile long pausedUntil;
    private volatile boolean paused;

    /**
     * @param ttl            vencimiento de cada página ({@code app.cache.catalog-ttl})
     * @param failureBackoff pausa tras una falla ({@code app.cache.failure-backoff})
     */
    public RedisCatalogCache(StringRedisTemplate redis, JsonMapper json, Duration ttl, Duration failureBackoff) {
        this(redis, json, ttl, failureBackoff, System::nanoTime);
    }

    RedisCatalogCache(StringRedisTemplate redis, JsonMapper json, Duration ttl, Duration failureBackoff,
            LongSupplier nanoTime) {
        if (ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("app.cache.catalog-ttl debe ser positivo; recibió " + ttl);
        }
        this.redis = redis;
        this.json = json;
        this.ttl = ttl;
        this.failureBackoffNanos = failureBackoff.toNanos();
        this.nanoTime = nanoTime;
    }

    @Override
    public <T> Optional<Page<T>> get(CatalogCacheKey key, Class<T> itemType) {
        if (isPaused()) {
            return Optional.empty();
        }
        try {
            String text = redis.opsForValue().get(pageKey(key));
            if (text == null) {
                return Optional.empty();
            }
            JavaType type = json.getTypeFactory().constructParametricType(Page.class, itemType);
            return Optional.of(json.readValue(text, type));
        } catch (RuntimeException failure) {
            pauseAfter("leer", failure);
            return Optional.empty();
        }
    }

    @Override
    public void put(CatalogCacheKey key, Page<?> page) {
        if (isPaused()) {
            return;
        }
        try {
            redis.opsForValue().set(pageKey(key), json.writeValueAsString(page), ttl);
        } catch (RuntimeException failure) {
            pauseAfter("guardar", failure);
        }
    }

    @Override
    public void invalidate() {
        try {
            redis.opsForValue().increment(VERSION_KEY);
        } catch (RuntimeException failure) {
            // Las páginas viejas vencen solas por su TTL: a lo sumo se ven hasta ese tiempo.
            pauseAfter("invalidar", failure);
        }
    }

    /** Clave completa de la página con la versión vigente. */
    String pageKey(CatalogCacheKey key) {
        String version = redis.opsForValue().get(VERSION_KEY);
        return PAGE_KEY_PREFIX + (version == null ? INITIAL_VERSION : version) + ":" + key.asText();
    }

    private boolean isPaused() {
        if (paused && nanoTime.getAsLong() - pausedUntil >= 0) {
            paused = false;
        }
        return paused;
    }

    private void pauseAfter(String operation, RuntimeException failure) {
        // Un solo aviso por pausa: con Redis caído no se llena el log con una línea por solicitud.
        LOG.warn("No se pudo {} en la caché de Redis ({}); se consulta la base y no se usa la caché por {} ms",
                operation, failure.toString(), Duration.ofNanos(failureBackoffNanos).toMillis());
        pausedUntil = nanoTime.getAsLong() + failureBackoffNanos;
        paused = true;
    }
}
