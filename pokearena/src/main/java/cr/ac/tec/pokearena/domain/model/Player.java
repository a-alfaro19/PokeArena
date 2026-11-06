package cr.ac.tec.pokearena.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Propósito: jugador de PokéArena, con los datos que necesita la autorización (sección 6).
 * Responsabilidades: identificar al jugador y su rol. Las historias de colección y cubos
 * agregarán sus propios datos cuando se implementen.
 * Referencia: sección 6, tabla player.
 *
 * @param id          identificador del jugador; es el {@code sub} de sus credenciales
 * @param displayName nombre visible; puede ser nulo porque el proveedor no siempre lo envía
 * @param email       correo; puede ser nulo por la misma razón
 * @param role        rol del jugador
 */
public record Player(UUID id, String displayName, String email, Role role) {

    public Player {
        Objects.requireNonNull(id, "El id del jugador es obligatorio");
        Objects.requireNonNull(role, "El rol del jugador es obligatorio");
    }
}
