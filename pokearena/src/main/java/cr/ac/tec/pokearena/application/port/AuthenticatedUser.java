package cr.ac.tec.pokearena.application.port;

import java.util.Objects;
import java.util.UUID;

import cr.ac.tec.pokearena.domain.model.Role;

/**
 * Propósito: quién hace la solicitud, según sus credenciales propias de PokéArena (sección 5.2).
 * Responsabilidades: transportar el id del jugador y su rol desde la seguridad de la API hasta
 * los casos de uso, sin que estos conozcan HTTP ni JWT.
 * Referencia: sección 5.2.
 *
 * @param playerId id del jugador (claim {@code sub} del token)
 * @param role     rol del jugador (claim {@code role} del token)
 */
public record AuthenticatedUser(UUID playerId, Role role) {

    public AuthenticatedUser {
        Objects.requireNonNull(playerId, "El id del jugador es obligatorio");
        Objects.requireNonNull(role, "El rol es obligatorio");
    }
}
