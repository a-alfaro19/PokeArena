package cr.ac.tec.pokearena.infrastructure.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;

import cr.ac.tec.pokearena.application.dto.CardDetails;
import cr.ac.tec.pokearena.application.dto.CardDetails.AbilitySummary;
import cr.ac.tec.pokearena.application.dto.CardDetails.CatalogValue;
import cr.ac.tec.pokearena.application.dto.Page;
import cr.ac.tec.pokearena.application.port.CatalogCacheKey;
import cr.ac.tec.pokearena.support.RedisTestContainers;
import tools.jackson.databind.json.JsonMapper;

/**
 * Propósito: pruebas de {@link RedisCatalogCache} contra un Redis real (sección 9).
 * Responsabilidades: verificar guardar y leer una página, el vencimiento, la invalidación por versión, que
 * el catálogo del jugador y la búsqueda del administrador usan claves separadas, y que con Redis caído
 * ninguna operación falla (la caché se comporta como vacía).
 * Referencia: sección 9 ("Política de caché") y sección 13 ("Redis caído no rompe nada").
 */
@DisplayName("Caché del catálogo en Redis")
class RedisCatalogCacheIT {

    private static final Duration TTL = Duration.ofSeconds(30);
    private static final Duration SHORT_TTL = Duration.ofSeconds(1);
    private static final Duration BACKOFF = Duration.ofSeconds(5);
    private static final Duration MAX_WAIT = Duration.ofSeconds(10);
    /** Con Redis caído, una operación no debe tardar más que unos pocos tiempos de espera. */
    private static final Duration MAX_DOWN_LATENCY = Duration.ofSeconds(2);
    private static final String HASH = "hash-de-filtros";
    private static final int LIMIT = 20;

    private static final GenericContainer<?> REDIS = RedisTestContainers.newRedis();
    private static LettuceConnectionFactory connection;
    private static StringRedisTemplate redis;

    private final JsonMapper json = JsonMapper.builder().build();

    @BeforeAll
    static void startRedis() {
        REDIS.start();
        connection = RedisTestContainers.connect(REDIS);
        redis = new StringRedisTemplate(connection);
    }

    @AfterAll
    static void stopRedis() {
        connection.destroy();
        REDIS.stop();
    }

    @BeforeEach
    void emptyRedis() {
        redis.execute(callback -> {
            callback.serverCommands().flushAll();
            return null;
        }, true);
    }

    @Test
    @DisplayName("9: una página guardada se lee igual, con sus cartas y su cursor")
    void putAndGet() {
        RedisCatalogCache cache = new RedisCatalogCache(redis, json, TTL, BACKOFF);
        CatalogCacheKey key = key(CatalogCacheKey.PLAYER_CATALOG);
        Page<CardDetails> page = new Page<>(List.of(card("PKA-FUE-001")), "Mg", LIMIT);

        assertThat(cache.get(key, CardDetails.class)).isEmpty();
        cache.put(key, page);

        assertThat(cache.get(key, CardDetails.class)).contains(page);
        assertThat(redis.keys("catalog:v0:" + CatalogCacheKey.PLAYER_CATALOG + ":*")).hasSize(1);
    }

    @Test
    @DisplayName("9: cada página se guarda con el vencimiento configurado y desaparece al vencer")
    void expires() {
        RedisCatalogCache cache = new RedisCatalogCache(redis, json, SHORT_TTL, BACKOFF);
        CatalogCacheKey key = key(CatalogCacheKey.PLAYER_CATALOG);
        cache.put(key, new Page<>(List.of(card("PKA-FUE-001")), null, LIMIT));

        String stored = redis.keys("catalog:v*").iterator().next();
        assertThat(redis.getExpire(stored)).isBetween(0L, SHORT_TTL.toSeconds());
        await().atMost(MAX_WAIT).until(() -> cache.get(key, CardDetails.class).isEmpty());
    }

    @Test
    @DisplayName("9: invalidar incrementa catalog:version y las páginas anteriores ya no se leen")
    void invalidate() {
        RedisCatalogCache cache = new RedisCatalogCache(redis, json, TTL, BACKOFF);
        CatalogCacheKey key = key(CatalogCacheKey.ADMIN_SEARCH);
        cache.put(key, new Page<>(List.of(card("PKA-FUE-001")), null, LIMIT));

        cache.invalidate();

        assertThat(redis.opsForValue().get(RedisCatalogCache.VERSION_KEY)).isEqualTo("1");
        assertThat(cache.get(key, CardDetails.class)).isEmpty();
        // La página vieja sigue en Redis con su vencimiento: no se borra, solo deja de usarse.
        assertThat(redis.keys("catalog:v0:*")).hasSize(1);
        cache.put(key, new Page<>(List.of(card("PKA-FUE-002")), null, LIMIT));
        assertThat(cache.get(key, CardDetails.class).orElseThrow().items().getFirst().code()).isEqualTo("PKA-FUE-002");
    }

    @Test
    @DisplayName("9: el catálogo del jugador y la búsqueda del administrador usan claves separadas")
    void separateSpaces() {
        RedisCatalogCache cache = new RedisCatalogCache(redis, json, TTL, BACKOFF);
        cache.put(key(CatalogCacheKey.ADMIN_SEARCH), new Page<>(List.of(card("PKA-ADM-001")), null, LIMIT));
        cache.put(key(CatalogCacheKey.PLAYER_CATALOG), new Page<>(List.of(card("PKA-JUG-001")), null, LIMIT));

        assertThat(cache.get(key(CatalogCacheKey.ADMIN_SEARCH), CardDetails.class).orElseThrow().items().getFirst()
                .code()).isEqualTo("PKA-ADM-001");
        assertThat(cache.get(key(CatalogCacheKey.PLAYER_CATALOG), CardDetails.class).orElseThrow().items()
                .getFirst().code()).isEqualTo("PKA-JUG-001");
        assertThat(redis.keys("catalog:*")).containsExactlyInAnyOrder(
                "catalog:v0:" + key(CatalogCacheKey.ADMIN_SEARCH).asText(),
                "catalog:v0:" + key(CatalogCacheKey.PLAYER_CATALOG).asText());
    }

    @Test
    @DisplayName("13: con Redis caído nada falla: leer da vacío (rápido), guardar e invalidar no lanzan errores")
    void redisDownIsNotAnError() {
        GenericContainer<?> doomed = RedisTestContainers.newRedis();
        doomed.start();
        LettuceConnectionFactory doomedConnection = RedisTestContainers.connect(doomed);
        try {
            RedisCatalogCache cache = new RedisCatalogCache(new StringRedisTemplate(doomedConnection), json, TTL,
                    BACKOFF);
            CatalogCacheKey key = key(CatalogCacheKey.PLAYER_CATALOG);
            cache.put(key, new Page<>(List.of(card("PKA-FUE-001")), null, LIMIT));
            assertThat(cache.get(key, CardDetails.class)).isPresent();

            doomed.stop();

            long start = System.nanoTime();
            assertThat(cache.get(key, CardDetails.class)).isEmpty();
            assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(MAX_DOWN_LATENCY);
            // Después de la falla, la caché se pausa: la siguiente lectura no espera a Redis.
            start = System.nanoTime();
            assertThat(cache.get(key, CardDetails.class)).isEmpty();
            assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(RedisTestContainers.COMMAND_TIMEOUT);
            assertThatCode(() -> cache.put(key, new Page<>(List.of(), null, LIMIT))).doesNotThrowAnyException();
            assertThatCode(cache::invalidate).doesNotThrowAnyException();
        } finally {
            doomedConnection.destroy();
        }
    }

    private static CatalogCacheKey key(String space) {
        return new CatalogCacheKey(space, HASH, CatalogCacheKey.FIRST_PAGE, LIMIT);
    }

    private static CardDetails card(String code) {
        return new CardDetails(code, "Carta " + code, 3, -2, new CatalogValue("FUE", "Fuego"),
                new CatalogValue("COMMON", "Común"), "/images/cards/x/large.png", "/images/cards/x/small.png",
                "READY", true, new AbilitySummary("HAB-000", "Sin Habilidad", null, null, null, Map.of()));
    }
}
