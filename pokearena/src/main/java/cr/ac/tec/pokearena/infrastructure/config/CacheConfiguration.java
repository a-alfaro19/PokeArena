package cr.ac.tec.pokearena.infrastructure.config;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import cr.ac.tec.pokearena.application.port.CatalogCache;
import cr.ac.tec.pokearena.infrastructure.cache.NoCatalogCache;
import cr.ac.tec.pokearena.infrastructure.cache.RedisCatalogCache;
import tools.jackson.databind.json.JsonMapper;

/**
 * Propósito: elegir la implementación de {@link CatalogCache} (sección 9).
 * Responsabilidades: con Redis configurado ({@code app.cache.redis-host}, variable {@code REDIS_HOST}),
 * crear {@link RedisCatalogCache} con su vencimiento y su pausa tras una falla; sin Redis, usar
 * {@link NoCatalogCache}, que no guarda nada, para que la aplicación arranque y funcione igual.
 * Referencia: sección 9 ("Política de caché" y "Falla de Redis").
 *
 * <p>Decisión: un solo método que decide según la propiedad, en lugar de dos beans con condiciones: así
 * se lee en un lugar qué caché queda activa y por qué. El {@link StringRedisTemplate} se pide con
 * {@link ObjectProvider} para no usarlo cuando no hay Redis. Spring Boot crea igual su conexión (con
 * {@code localhost}, porque no admite un host vacío), pero es perezosa: sin comandos, nunca se conecta.
 */
@Configuration
public class CacheConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(CacheConfiguration.class);

    @Bean
    public CatalogCache catalogCache(@Value("${app.cache.redis-host:}") String redisHost,
            ObjectProvider<StringRedisTemplate> redis, JsonMapper json,
            @Value("${app.cache.catalog-ttl}") Duration ttl,
            @Value("${app.cache.failure-backoff}") Duration failureBackoff) {
        if (redisHost == null || redisHost.isBlank()) {
            LOG.info("Sin REDIS_HOST: la caché del catálogo está desactivada y se consulta siempre la base");
            return new NoCatalogCache();
        }
        LOG.info("Caché del catálogo en Redis ({}), con vencimiento {}", redisHost, ttl);
        return new RedisCatalogCache(redis.getObject(), json, ttl, failureBackoff);
    }
}
