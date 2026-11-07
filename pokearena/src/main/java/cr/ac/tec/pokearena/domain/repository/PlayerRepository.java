package cr.ac.tec.pokearena.domain.repository;

import java.util.Optional;
import java.util.UUID;

import cr.ac.tec.pokearena.domain.model.NewPlayer;
import cr.ac.tec.pokearena.domain.model.Player;
import cr.ac.tec.pokearena.domain.model.PlayerAccount;
import cr.ac.tec.pokearena.domain.model.ProviderIdentity;
import cr.ac.tec.pokearena.domain.model.Role;

/**
 * Propósito: puerto de persistencia de jugadores (patrón Repository, sección 4.4).
 * Responsabilidades: encontrar un jugador por su identidad del proveedor y crearlo si no existe,
 * garantizando una sola cuenta por identidad aunque dos solicitudes lleguen a la vez.
 * Referencia: sección 8.1, paso 5, y sección 6 (unicidad de player_identity).
 *
 * <p>Las operaciones que escriben deben llamarse dentro de un {@code UnitOfWork}: crear el
 * jugador y su identidad son dos filas que tienen que confirmarse juntas.
 */
public interface PlayerRepository {

    /** Jugador asociado a la identidad, si existe. */
    Optional<Player> findByIdentity(ProviderIdentity identity);

    /**
     * Devuelve el jugador de la identidad; si no existe, lo crea con los datos de {@code candidate}.
     * Si otra solicitud lo crea al mismo tiempo, devuelve ese mismo jugador: nunca hay dos cuentas.
     *
     * @return el jugador y si esta llamada fue la que lo creó (solo una de dos llamadas simultáneas)
     */
    PlayerAccount findOrCreate(ProviderIdentity identity, NewPlayer candidate);

    /** Cambia el rol del jugador. Lo usa el ingreso cuando la configuración de administradores cambió. */
    void updateRole(UUID playerId, Role role);
}
