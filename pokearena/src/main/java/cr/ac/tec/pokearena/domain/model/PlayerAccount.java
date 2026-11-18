package cr.ac.tec.pokearena.domain.model;

import java.util.Objects;

/**
 * Propósito: resultado de buscar o crear la cuenta de una identidad (sección 8.1, paso 5).
 * Responsabilidades: decir qué jugador quedó, si esta llamada fue la que creó la cuenta y en qué estado
 * está su colección inicial, para que el inicio de sesión informe {@code firstLogin} y decida si invoca
 * la entrega sin una segunda consulta.
 * Referencia: sección 8.1, pasos 5, 6 y 8.
 *
 * <p>Si dos ingresos de una identidad nueva llegan a la vez, solo uno recibe {@code created = true}:
 * lo decide la restricción única de {@code player_identity}, no una lectura previa.
 *
 * <p>{@code initialCollection} es el estado guardado <b>en el momento de leer la cuenta</b>, sin bloqueo.
 * Solo sirve para evitar trabajo: si ya es {@code DELIVERED}, nunca volverá a ser {@code PENDING} (la
 * entrega es exactamente una vez), así que el ingreso puede informar {@code ALREADY_DELIVERED} sin invocar
 * la entrega. Si es {@code PENDING}, la decisión real la toma la entrega con la fila bloqueada (8.2, paso 1).
 *
 * @param player            jugador de la identidad
 * @param created           {@code true} solo si la cuenta se creó en esta llamada
 * @param initialCollection estado guardado de la colección inicial al leer la cuenta
 */
public record PlayerAccount(Player player, boolean created, InitialCollectionState initialCollection) {

    public PlayerAccount {
        Objects.requireNonNull(player, "El jugador es obligatorio");
        Objects.requireNonNull(initialCollection, "El estado de la colección inicial es obligatorio");
    }

    /** {@code true} si la colección inicial ya estaba entregada al leer la cuenta. */
    public boolean initialCollectionDelivered() {
        return initialCollection == InitialCollectionState.DELIVERED;
    }

    /** La misma cuenta con otro rol: lo usa el ingreso cuando la configuración de administradores cambió. */
    public PlayerAccount withRole(Role role) {
        return new PlayerAccount(new Player(player.id(), player.displayName(), player.email(), role), created,
                initialCollection);
    }
}
