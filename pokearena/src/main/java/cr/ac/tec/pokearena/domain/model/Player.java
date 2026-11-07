package cr.ac.tec.pokearena.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Propósito: jugador de PokéArena, con los datos que necesita la autorización (sección 6).
 * Responsabilidades: identificar al jugador y su rol, y dar el nombre que se le muestra, con un nombre
 * de respaldo cuando el proveedor no envió uno. Las historias de colección y cubos agregarán sus
 * propios datos cuando se implementen.
 * Referencia: sección 6, tabla player.
 *
 * @param id          identificador del jugador; es el {@code sub} de sus credenciales
 * @param displayName nombre visible; puede ser nulo porque el proveedor no siempre lo envía
 * @param email       correo; puede ser nulo por la misma razón
 * @param role        rol del jugador
 */
public record Player(UUID id, String displayName, String email, Role role) {

    /** Inicio del nombre de respaldo (sección 6, "Reglas del modelo"). */
    public static final String FALLBACK_NAME_PREFIX = "Jugador";

    /** Cuántos caracteres del final del id se agregan al nombre de respaldo. */
    public static final int FALLBACK_SUFFIX_LENGTH = 6;

    public Player {
        Objects.requireNonNull(id, "El id del jugador es obligatorio");
        Objects.requireNonNull(role, "El rol del jugador es obligatorio");
    }

    /**
     * Nombre que se le muestra al jugador. Si el proveedor no envió uno (Google no siempre lo hace),
     * se usa "Jugador" más los últimos caracteres del id, por ejemplo {@code Jugador 3f9a1c}. El
     * respaldo no se guarda en la base: la columna conserva el nulo, que es lo que informó el proveedor.
     * Se toma el id del jugador y no el {@code sub} del proveedor, para no mostrar un dato del proveedor.
     */
    public String visibleName() {
        if (displayName != null && !displayName.isBlank()) {
            return displayName;
        }
        String text = id.toString();
        return FALLBACK_NAME_PREFIX + " " + text.substring(text.length() - FALLBACK_SUFFIX_LENGTH);
    }
}
