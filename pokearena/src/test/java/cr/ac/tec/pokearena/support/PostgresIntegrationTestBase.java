package cr.ac.tec.pokearena.support;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Propósito: base común de las pruebas de integración que necesitan PostgreSQL real.
 * Responsabilidades: levantar UN contenedor de PostgreSQL compartido por todas las clases
 * de prueba y conectarlo a Spring sin configurar URLs a mano.
 * Referencia: secciones 3 y 13 de la especificación (Testcontainers).
 *
 * <p>Decisiones:
 * <ul>
 *   <li>Contenedor "singleton": se arranca una sola vez en el bloque estático y vive hasta que
 *       termina la JVM (Testcontainers lo borra al final). Así cada clase de prueba no paga
 *       otro arranque, y Spring puede reutilizar el mismo contexto en caché.</li>
 *   <li>{@code @ServiceConnection} hace que Spring Boot tome del contenedor la URL, el usuario
 *       y la contraseña, en lugar de las propiedades de {@code application.properties}.</li>
 *   <li>Flyway aplica la migración al arrancar el contexto, igual que en producción.</li>
 *   <li>También entrega el secreto JWT, sin el cual la aplicación no arranca (sección 5.2).</li>
 *   <li>Y aleja la tarea programada de reintentos de miniaturas, para que no actúe sola en medio de
 *       una prueba.</li>
 *   <li>Reduce el pool de conexiones de cada contexto, porque todos los contextos en caché comparten
 *       este contenedor y su límite de conexiones.</li>
 * </ul>
 *
 * <p>Uso: la prueba extiende esta clase y declara su propio {@code @SpringBootTest}, porque
 * cada una decide si necesita servidor web o no.
 */
public abstract class PostgresIntegrationTestBase {

    // Versión de PostgreSQL de las pruebas, con nombre para no repetirla.
    private static final DockerImageName POSTGRES_IMAGE = DockerImageName.parse("postgres:17-alpine");

    @ServiceConnection
    protected static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(POSTGRES_IMAGE);

    /**
     * Secreto de firma de las pruebas: aleatorio en cada ejecución, así no hay un secreto escrito en
     * el repositorio y las pruebas no dependen de que la máquina tenga APP_JWT_SECRET definida.
     */
    protected static final String TEST_JWT_SECRET = TestSecrets.randomJwtSecret();

    /** Intervalo más largo que cualquier ejecución de las pruebas. */
    private static final String SCHEDULED_TASKS_NEVER_DURING_TESTS = "PT24H";

    /**
     * Conexiones máximas de cada contexto de Spring de las pruebas. Spring guarda en caché un contexto
     * por cada configuración distinta (perfiles, propiedades, beans de prueba; hoy unos 13), y cada uno
     * conserva su propio pool de HikariCP contra este MISMO contenedor. Con el valor por defecto de
     * HikariCP (10) se llegaba a 130 conexiones, más que el máximo de PostgreSQL (100), y el CI fallaba
     * con "too many clients already" según el orden de las clases. Con 5, el peor caso es 13 × 5 = 65.
     * No es menor para que las pruebas de concurrencia sigan teniendo varias transacciones a la vez
     * (por ejemplo, una fila bloqueada mientras otra transacción la salta con SKIP LOCKED).
     * Solo afecta a las pruebas: producción conserva la configuración por defecto.
     */
    private static final String TEST_POOL_MAX_SIZE = "5";

    /**
     * Conexiones inactivas mínimas: ninguna. Así un pool no abre conexiones por adelantado hasta su
     * máximo al arrancar el contexto, sino solo las que la prueba usa.
     */
    private static final String TEST_POOL_MIN_IDLE = "0";

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void securityProperties(DynamicPropertyRegistry registry) {
        registry.add("app.security.jwt-secret", () -> TEST_JWT_SECRET);
        // La tarea programada de reintentos no corre sola durante las pruebas: podría tomar las cartas
        // PENDING_RETRY que otra prueba está revisando. Las pruebas de reintentos invocan el caso de uso.
        registry.add("app.images.retry-interval", () -> SCHEDULED_TASKS_NEVER_DURING_TESTS);
        // Todos los contextos en caché comparten el contenedor: pools pequeños para no agotar sus
        // conexiones (ver TEST_POOL_MAX_SIZE).
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> TEST_POOL_MAX_SIZE);
        registry.add("spring.datasource.hikari.minimum-idle", () -> TEST_POOL_MIN_IDLE);
    }
}
