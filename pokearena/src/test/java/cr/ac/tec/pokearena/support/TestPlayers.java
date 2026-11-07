package cr.ac.tec.pokearena.support;

import java.util.Collection;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;

import cr.ac.tec.pokearena.application.usecase.PlayerAccountService;
import cr.ac.tec.pokearena.domain.model.Player;
import cr.ac.tec.pokearena.domain.model.ProviderIdentity;

/**
 * Propósito: jugadores reales para las pruebas de integración.
 * Responsabilidades: crearlos por la misma vía que el inicio de sesión (sección 5.2), para que
 * cumplan las claves foráneas (por ejemplo idempotency_key.user_id), y borrarlos al terminar.
 * Referencia: secciones 5.2 y 6.
 */
public final class TestPlayers {

    /** Proveedor de las identidades de prueba; no está en app.admin.identities, así que son PLAYER. */
    public static final String TEST_PROVIDER = "test";

    private TestPlayers() {
    }

    /** Crea un jugador nuevo con una identidad de prueba única. */
    public static Player create(PlayerAccountService accounts) {
        return accounts.findOrCreate(new ProviderIdentity(TEST_PROVIDER, "jugador-" + UUID.randomUUID()),
                "Jugador de prueba", null);
    }

    /** Borra los jugadores y todo lo que depende de ellos en las tablas que usan estas pruebas. */
    public static void delete(JdbcTemplate jdbc, Collection<UUID> playerIds) {
        for (UUID id : playerIds) {
            // Colección y cubos (historia 3.1.1): van primero porque apuntan al jugador y a las cartas.
            jdbc.update("DELETE FROM player_card WHERE player_id = ?", id);
            jdbc.update("DELETE FROM cube_movement WHERE player_id = ?", id);
            jdbc.update("DELETE FROM idempotency_key WHERE user_id = ?", id);
            jdbc.update("DELETE FROM player_identity WHERE player_id = ?", id);
            jdbc.update("DELETE FROM player WHERE id = ?", id);
        }
    }
}
