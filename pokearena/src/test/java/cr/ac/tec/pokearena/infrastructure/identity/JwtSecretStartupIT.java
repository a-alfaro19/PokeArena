package cr.ac.tec.pokearena.infrastructure.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.NestedExceptionUtils;

import cr.ac.tec.pokearena.PokearenaApplication;
import cr.ac.tec.pokearena.support.PostgresIntegrationTestBase;
import cr.ac.tec.pokearena.support.TestSecrets;

/**
 * Propósito: verificar que la aplicación completa NO arranca sin un secreto JWT válido (sección 5.2).
 * Responsabilidades: arrancar la aplicación real contra el PostgreSQL de pruebas, sin secreto o con
 * uno corto, y comprobar que falla por esa causa; y que con un secreto válido sí arranca.
 * Referencia: sección 5.2 ("El secreto de firma viene de APP_JWT_SECRET").
 *
 * <p>No usa {@code @SpringBootTest}: arranca la aplicación a mano para poder pasarle la
 * configuración exacta. El secreto se fija explícitamente como argumento, así la prueba no depende
 * de si la máquina tiene APP_JWT_SECRET definida.
 */
@DisplayName("Arranque de la aplicación y secreto JWT")
class JwtSecretStartupIT extends PostgresIntegrationTestBase {

    @Test
    @DisplayName("Sin APP_JWT_SECRET la aplicación no arranca, y la causa lo indica")
    void doesNotStartWithoutSecret() {
        assertThatThrownBy(() -> start("--app.security.jwt-secret="))
                .satisfies(failure -> assertThat(NestedExceptionUtils.getMostSpecificCause(failure))
                        .isInstanceOf(InvalidJwtSecretException.class)
                        .hasMessageContaining("APP_JWT_SECRET"));
    }

    @Test
    @DisplayName("Con un APP_JWT_SECRET de menos de 32 bytes la aplicación no arranca")
    void doesNotStartWithShortSecret() {
        assertThatThrownBy(() -> start("--app.security.jwt-secret=demasiado-corto"))
                .satisfies(failure -> assertThat(NestedExceptionUtils.getMostSpecificCause(failure))
                        .isInstanceOf(InvalidJwtSecretException.class)
                        .hasMessageContaining("32"));
    }

    @Test
    @DisplayName("Con un APP_JWT_SECRET válido la aplicación arranca")
    void startsWithValidSecret() {
        try (ConfigurableApplicationContext context = start("--app.security.jwt-secret=" + TestSecrets.randomJwtSecret())) {
            assertThat(context.isRunning()).isTrue();
        }
    }

    private static ConfigurableApplicationContext start(String secretArgument) {
        return new SpringApplicationBuilder(PokearenaApplication.class)
                .web(WebApplicationType.NONE)
                .run("--spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                        "--spring.datasource.username=" + POSTGRES.getUsername(),
                        "--spring.datasource.password=" + POSTGRES.getPassword(),
                        secretArgument);
    }
}
