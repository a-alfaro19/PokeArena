package cr.ac.tec.pokearena.domain.model;

import java.util.Objects;

/**
 * Propósito: estado de una carta dentro del deck de un asiento.
 * Responsabilidades: conservar el orden barajado y la zona actual de la carta.
 * Referencia: sección 6.2; sección 7.3, pasos 4 y 5; Anexo A.14.
 *
 * @param position posición inmutable del deck barajado, desde cero
 * @param cardCode código de catálogo de la carta
 * @param zone     zona actual
 */
public record MatchCard(int position, String cardCode, MatchZone zone) {

    public MatchCard {
        Objects.requireNonNull(cardCode, "El código de carta es obligatorio");
        Objects.requireNonNull(zone, "La zona de carta es obligatoria");
        if (position < 0 || cardCode.isBlank()) {
            throw new IllegalArgumentException("La carta necesita posición no negativa y código no vacío");
        }
    }
}
