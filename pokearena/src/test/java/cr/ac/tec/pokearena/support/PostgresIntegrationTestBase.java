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

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void securityProperties(DynamicPropertyRegistry registry) {
        registry.add("app.security.jwt-secret", () -> TEST_JWT_SECRET);
        // La tarea programada de reintentos no corre sola durante las pruebas: podría tomar las cartas
        // PENDING_RETRY que otra prueba está revisando. Las pruebas de reintentos invocan el caso de uso.
        registry.add("app.images.retry-interval", () -> SCHEDULED_TASKS_NEVER_DURING_TESTS);
    }
}
