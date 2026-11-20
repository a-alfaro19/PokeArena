package cr.ac.tec.pokearena.application.dto;

import java.util.Objects;
import java.util.UUID;

/**
 * Propósito: solicitud de Consultar un deck (historia 3.1.3).
 * Responsabilidades: llevar el jugador de la sesión y el identificador tal como llegó en la ruta.
 * Referencia: sección 7.1; Anexo A.8, mensaje 18 ({@code execute(GetDeckCommand)}).
 *
 * @param playerId jugador autenticado
 * @param deckId   identificador del deck tal como llegó en la ruta; si no es un número, el deck no existe
 */
public record GetDeckCommand(UUID playerId, String deckId) {

    public GetDeckCommand {
        Objects.requireNonNull(playerId, "El jugador es obligatorio");
    }
}
