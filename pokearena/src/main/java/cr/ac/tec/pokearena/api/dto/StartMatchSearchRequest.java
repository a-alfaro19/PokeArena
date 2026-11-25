package cr.ac.tec.pokearena.api.dto;

/** JSON del alta de búsqueda (A.9). El caso de uso valida los campos ausentes. */
public record StartMatchSearchRequest(Long deckId, String context) {
}
