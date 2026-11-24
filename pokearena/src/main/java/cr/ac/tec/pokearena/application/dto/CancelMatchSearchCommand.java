package cr.ac.tec.pokearena.application.dto;

import java.util.UUID;

/** Identidad autenticada y búsqueda de la que el jugador desea salir (A.12, mensaje 5). */
public record CancelMatchSearchCommand(UUID playerId, String searchId) {
}
