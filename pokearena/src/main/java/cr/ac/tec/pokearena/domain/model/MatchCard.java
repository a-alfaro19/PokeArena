package cr.ac.tec.pokearena.domain.model;

import java.util.Objects;

/**
 * Propósito: carta de un asiento con su posición reproducible y zona actual.
 * Responsabilidades: conservar el orden barajado sin exponerlo como parte de la vista del jugador.
 * Referencia: sección 6.2; sección 7.3, pasos 4 a 6; criterio C.10-A1.
 *
 * @param code     código de carta
 * @param position posición en el deck barajado, desde cero
 * @param zone     zona actual
 */
public record MatchCard(String code, int position, MatchZone zone) {

    public MatchCard {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("El código de la carta es obligatorio.");
        }
        if (position < 0) {
            throw new IllegalArgumentException("La posición de la carta no puede ser negativa.");
        }
        Objects.requireNonNull(zone, "La zona de la carta es obligatoria");
    }

    public MatchCard inZone(MatchZone nextZone) {
        return new MatchCard(code, position, nextZone);
    }
}
