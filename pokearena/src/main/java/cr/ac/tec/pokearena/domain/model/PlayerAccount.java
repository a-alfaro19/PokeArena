package cr.ac.tec.pokearena.domain.model;

import java.util.Objects;

/**
 * Propósito: resultado de buscar o crear la cuenta de una identidad (sección 8.1, paso 5).
 * Responsabilidades: decir qué jugador quedó y si esta llamada fue la que creó la cuenta, para que el
 * inicio de sesión informe {@code firstLogin} sin una segunda consulta.
 * Referencia: sección 8.1, pasos 5 y 8.
 *
 * <p>Si dos ingresos de una identidad nueva llegan a la vez, solo uno recibe {@code created = true}:
 * lo decide la restricción única de {@code player_identity}, no una lectura previa.
 *
 * @param player  jugador de la identidad
 * @param created {@code true} solo si la cuenta se creó en esta llamada
 */
public record PlayerAccount(Player player, boolean created) {

    public PlayerAccount {
        Objects.requireNonNull(player, "El jugador es obligatorio");
    }
}
