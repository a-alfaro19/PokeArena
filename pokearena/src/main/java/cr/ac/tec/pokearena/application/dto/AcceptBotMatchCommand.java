package cr.ac.tec.pokearena.application.dto;

/** Identificador recibido en la ruta. La identidad autenticada viene de IdempotentRequest. */
public record AcceptBotMatchCommand(String searchId) {
}
