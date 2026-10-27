package cr.ac.tec.pokearena;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.core.env.MapPropertySource;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "POKEARENA_REJECTED_DATABASE_URL", matches = ".+")
class DisposableDatabaseSafetyTests {
    private Connection connect(String url) throws Exception {
        // Usa las mismas credenciales externas del clúster de pruebas, sin asumir usuario o contraseña.
        String password = System.getenv("SPRING_DATASOURCE_PASSWORD");
        if (password == null) { password = System.getenv("DB_PASSWORD"); }
        return DriverManager.getConnection(url, System.getenv("SPRING_DATASOURCE_USERNAME"), password);
    }

    @Test
    void allowsOnlyTheRealAuthorizedDatabase() throws Exception {
        // Una conexión comprobada permite operar contra la base desechable autorizada.
        try (var connection = DisposableDatabaseInitializer.verify(connect(System.getenv("POKEARENA_TEST_DATABASE_URL")));
                var statement = connection.createStatement();
                var result = statement.executeQuery("SELECT current_database()")) {
            assertTrue(result.next());
            assertEquals("pokearena_us39_test", result.getString(1));
        }
    }

    @Test
    void rejectsMisleadingUrlBeforeAnySpringSchemaOrDataWrites() throws Exception {
        String url = System.getenv("POKEARENA_REJECTED_DATABASE_URL");
        assertTrue(url.endsWith("ApplicationName=client_test"));
        try (var connection = connect(url); var statement = connection.createStatement()) {
            // Antes de preparar el centinela verifica el nombre exclusivo de la segunda base temporal.
            try (var result = statement.executeQuery("SELECT current_database()")) {
                assertTrue(result.next());
                assertEquals("pokearena_us39_rejected", result.getString(1), "No usar una base de trabajo para esta prueba");
            }
            statement.execute("CREATE TABLE IF NOT EXISTS safety_marker(value TEXT NOT NULL)");
            statement.execute("TRUNCATE safety_marker");
            statement.execute("INSERT INTO safety_marker VALUES ('unchanged')");
            String before = fingerprint(connection);
            var failure = assertThrows(Exception.class, () -> new SpringApplicationBuilder(PokearenaApplication.class)
                    .web(WebApplicationType.NONE).logStartupInfo(false)
                    .properties("spring.main.banner-mode=off", "logging.level.root=OFF")
                    .initializers(new DisposableDatabaseInitializer(), context -> {
                        // La configuración de rechazo tiene prioridad sobre application.properties.
                        String password = System.getenv("SPRING_DATASOURCE_PASSWORD");
                        if (password == null) { password = System.getenv("DB_PASSWORD"); }
                        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("safety-test", Map.of(
                                "spring.datasource.url", url,
                                "spring.datasource.username", System.getenv("SPRING_DATASOURCE_USERNAME"),
                                "spring.datasource.password", password == null ? "" : password,
                                "spring.sql.init.mode", "always")));
                    }).run());
            Throwable cause = failure;
            while (cause.getCause() != null) { cause = cause.getCause(); }
            assertTrue(cause.getMessage().contains("Pruebas destructivas rechazadas"), cause.getMessage());
            assertEquals(before, fingerprint(connection));
            try (var result = statement.executeQuery("SELECT value FROM safety_marker")) {
                assertTrue(result.next());
                assertEquals("unchanged", result.getString(1));
                assertFalse(result.next());
            }
        }
    }

    private String fingerprint(Connection connection) throws Exception {
        // Incluye tablas, columnas y extensiones para detectar incluso una inicialización parcial.
        try (var statement = connection.createStatement(); var result = statement.executeQuery("""
                SELECT COALESCE((SELECT string_agg(table_name || ':' || column_name || ':' || data_type, ','
                    ORDER BY table_name, ordinal_position) FROM information_schema.columns WHERE table_schema='public'), '')
                    || '|' || (SELECT string_agg(extname, ',' ORDER BY extname) FROM pg_extension)
                """)) {
            result.next();
            return result.getString(1);
        }
    }
}
