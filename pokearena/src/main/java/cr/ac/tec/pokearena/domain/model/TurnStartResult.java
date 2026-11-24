package cr.ac.tec.pokearena.domain.model;

import java.util.List;
import java.util.Objects;

/**
 * Propósito: resultado puro de iniciar un turno para un asiento.
 * Responsabilidades: reunir las zonas actualizadas de las cartas y la energía disponible para este turno.
 * Referencia: sección 7.3, paso 5; criterios C.10-E2 a C.10-E5.
 *
 * @param cards  cartas con su zona al finalizar el inicio del turno
 * @param energy energía disponible para este turno, sin acumulación
 */
public record TurnStartResult(List<MatchCard> cards, int energy) {

    public TurnStartResult {
        Objects.requireNonNull(cards, "Las cartas del turno son obligatorias");
        cards = List.copyOf(cards);
        if (energy < 0) {
            throw new IllegalArgumentException("La energía no puede ser negativa.");
        }
    }
}
