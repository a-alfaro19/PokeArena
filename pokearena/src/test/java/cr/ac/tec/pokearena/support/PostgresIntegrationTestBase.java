package cr.ac.tec.pokearena.support;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
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

    static {
        POSTGRES.start();
    }
}
