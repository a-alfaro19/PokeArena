package cr.ac.tec.pokearena.application.dto;

import java.util.UUID;

/** Identidad de la sesión e identificador de la búsqueda de la ruta (A.11, mensaje 5). */
public record GetMatchSearchCommand(UUID playerId, String searchId) {
}
