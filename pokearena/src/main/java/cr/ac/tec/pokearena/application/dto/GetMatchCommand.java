package cr.ac.tec.pokearena.application.dto;

import java.util.Objects;
import java.util.UUID;

/**
 * Propósito: solicitud de consulta del inicio de una partida.
 * Responsabilidades: llevar el jugador autenticado y el id tal como llegó en la ruta.
 * Referencia: sección 7.3 y Anexo A.15, mensaje 5.
 *
 * @param playerId jugador autenticado
 * @param matchId identificador textual recibido en la ruta
 */
public record GetMatchCommand(UUID playerId, String matchId) {

    public GetMatchCommand {
        Objects.requireNonNull(playerId, "El jugador autenticado es obligatorio");
    }
}
