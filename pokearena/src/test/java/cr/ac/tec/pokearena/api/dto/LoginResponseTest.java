package cr.ac.tec.pokearena.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.DisplayName;

import cr.ac.tec.pokearena.application.dto.LoginResult;
import cr.ac.tec.pokearena.application.dto.Session;
import cr.ac.tec.pokearena.application.port.InitialCollectionStatus;
import cr.ac.tec.pokearena.application.port.TokenIssuer.IssuedToken;
import cr.ac.tec.pokearena.domain.model.Player;
import cr.ac.tec.pokearena.domain.model.Role;

/**
 * Propósito: comprobar la forma del objeto {@code initialCollection} de la respuesta del inicio de sesión
 * (sección 8.1, paso 8).
 * Responsabilidades: verificar que cada estado viaja con su mensaje: texto si está pendiente y
 * {@code null} si no lo está.
 * Referencia: sección 8.1, paso 8.
 */
@DisplayName("Respuesta del inicio de sesión: objeto initialCollection")
class LoginResponseTest {

    private static final Instant NOW = Instant.parse("2026-11-07T12:00:00Z");
    private static final Duration TTL = Duration.ofMinutes(15);

    @ParameterizedTest(name = "{0}")
    @EnumSource(InitialCollectionStatus.class)
    @DisplayName("Cada estado lleva status y message; message es null salvo en los estados pendientes")
    void initialCollectionObject(InitialCollectionStatus status) {
        Player player = new Player(UUID.randomUUID(), "Ana", null, Role.PLAYER);
        LoginResult result = new LoginResult(new Session(player, new IssuedToken("token", TTL, NOW.plus(TTL))),
                true, status);

        InitialCollectionResponse collection = LoginResponse.from(result).initialCollection();

        assertThat(collection.status()).isEqualTo(status.name());
        if (status.name().startsWith("PENDING_")) {
            assertThat(collection.message()).isNotBlank();
        } else {
            assertThat(collection.message()).isNull();
        }
    }
}
