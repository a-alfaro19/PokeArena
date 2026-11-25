package cr.ac.tec.pokearena.application.dto;

import java.util.Objects;
import java.util.UUID;

/**
 * Propósito: transportar la identidad autenticada y el id de la ruta para consultar una partida.
 * Responsabilidades: mantener la API fuera del caso de uso y exigir una identidad autenticada.
 * Referencia: sección 7.3; Anexo A.15, mensaje 5.
 */
public record GetMatchCommand(UUID playerId, String matchId) {

    public GetMatchCommand {
        Objects.requireNonNull(playerId, "El jugador es obligatorio");
    }
}
