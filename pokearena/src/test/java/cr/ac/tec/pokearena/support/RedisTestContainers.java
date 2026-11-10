package cr.ac.tec.pokearena.support;

import java.time.Duration;

import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Propósito: Redis real para las pruebas de la caché del catálogo (secciones 9 y 13).
 * Responsabilidades: crear contenedores de Redis con la misma imagen que Docker Compose y conectarles un
 * {@link StringRedisTemplate} con tiempos de espera cortos, como en la aplicación.
 * Referencia: sección 13 ("Testcontainers: PostgreSQL y Redis").
 *
 * <p>Cada prueba crea su propio contenedor (no hay uno compartido como el de PostgreSQL), porque algunas
 * lo detienen a propósito para comprobar que "Redis caído no rompe nada".
 */
public final class RedisTestContainers {

    /** Misma imagen que {@code docker-compose.yml}. */
    public static final DockerImageName REDIS_IMAGE = DockerImageName.parse("redis:7.4-alpine");

    /** Puerto de Redis dentro del contenedor. */
    public static final int REDIS_PORT = 6379;

    /** Tiempo de espera de los comandos en las pruebas: corto, igual que el valor por defecto de la aplicación. */
    public static final Duration COMMAND_TIMEOUT = Duration.ofMillis(250);

    private RedisTestContainers() {
    }

    /** Un contenedor de Redis nuevo, sin arrancar. */
    @SuppressWarnings("resource")
    public static GenericContainer<?> newRedis() {
        return new GenericContainer<>(REDIS_IMAGE).withExposedPorts(REDIS_PORT);
    }

    /** Conexión a un contenedor ya arrancado. Quien la pide la cierra con {@code destroy()}. */
    public static LettuceConnectionFactory connect(GenericContainer<?> redis) {
        LettuceConnectionFactory factory = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration(redis.getHost(), redis.getMappedPort(REDIS_PORT)),
                LettuceClientConfiguration.builder().commandTimeout(COMMAND_TIMEOUT).build());
        factory.afterPropertiesSet();
        factory.start();
        return factory;
    }
}
