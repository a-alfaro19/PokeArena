package cr.ac.tec.pokearena.application.dto;

import cr.ac.tec.pokearena.application.port.TokenIssuer.IssuedToken;
import cr.ac.tec.pokearena.domain.model.Player;

/**
 * Propósito: resultado de abrir una sesión: el jugador y sus credenciales propias.
 * Responsabilidades: transportar ambos datos hasta la API, que arma la respuesta HTTP.
 * Referencia: sección 5.2 (credenciales de prueba) y sección 8.1, paso 8 (respuesta del inicio de sesión).
 *
 * @param player jugador dueño de la sesión
 * @param token  credenciales emitidas
 */
public record Session(Player player, IssuedToken token) {
}
