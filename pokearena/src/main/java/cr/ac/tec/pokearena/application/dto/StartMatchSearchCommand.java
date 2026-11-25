package cr.ac.tec.pokearena.application.dto;

/** Campos del alta (A.9). La identidad procede de la clave asociada a la sesión autenticada. */
public record StartMatchSearchCommand(Long deckId, String context) {
}
