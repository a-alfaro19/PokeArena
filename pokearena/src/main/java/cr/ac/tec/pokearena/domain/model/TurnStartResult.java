package cr.ac.tec.pokearena.domain.model;

import java.util.List;

/**
 * Propósito: resultado puro de aplicar las reglas de inicio de un turno.
 * Responsabilidades: entregar la energía no acumulable y el estado de cartas actualizado.
 * Referencia: sección 7.3; Anexo A.14.
 */
public record TurnStartResult(int energy, List<MatchCard> cards) {

    public TurnStartResult {
        if (energy < 0) {
            throw new IllegalArgumentException("La energía no puede ser negativa");
        }
        cards = List.copyOf(cards);
    }
}
