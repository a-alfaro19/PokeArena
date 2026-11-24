package cr.ac.tec.pokearena.domain.model;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import cr.ac.tec.pokearena.domain.rule.DeckRules;

/**
 * Propósito: estado inicial de un asiento persistido en una partida.
 * Responsabilidades: reunir participante, energía y todas sus cartas en el orden barajado y por zona.
 * Referencia: sección 6.2 y sección 7.3, pasos 4 a 6.
 *
 * @param seat asiento del participante
 * @param playerId jugador, o nulo para el bot
 * @param isBot indica si el asiento es del bot
 * @param deckId deck de entrada, o nulo para el bot
 * @param energy energía disponible al inicio del turno
 * @param active indica si el asiento sigue activo en la partida
 * @param cards cartas en orden barajado y con su zona inicial
 */
public record MatchParticipant(int seat, UUID playerId, boolean isBot, Long deckId, int energy, boolean active,
        List<MatchCard> cards) {

    public MatchParticipant {
        if (seat != MatchSeat.FIRST_SEAT && seat != MatchSeat.SECOND_SEAT) {
            throw new IllegalArgumentException("El asiento debe ser uno o dos.");
        }
        if (isBot != (playerId == null) || isBot != (deckId == null)) {
            throw new IllegalArgumentException("El jugador y su deck deben coincidir con el tipo de asiento.");
        }
        if (deckId != null && deckId <= 0) {
            throw new IllegalArgumentException("El id del deck debe ser positivo.");
        }
        if (energy < 0) {
            throw new IllegalArgumentException("La energía no puede ser negativa.");
        }
        cards = cards.stream().sorted(Comparator.comparingInt(MatchCard::position)).toList();
        if (cards.size() != DeckRules.DECK_SIZE
                || cards.stream().map(MatchCard::code).distinct().count() != cards.size()) {
            throw new IllegalArgumentException("El asiento debe contener las cartas del deck en posiciones únicas.");
        }
        for (int position = 0; position < cards.size(); position++) {
            if (cards.get(position).position() != position) {
                throw new IllegalArgumentException("Las posiciones del deck deben ser consecutivas desde cero.");
            }
        }
    }
}
