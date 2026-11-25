package cr.ac.tec.pokearena.domain.model;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import cr.ac.tec.pokearena.domain.rule.DeckRules;

/**
 * Propósito: estado inicial o persistido de un asiento de la partida.
 * Responsabilidades: asociar identidad, deck, energía y cartas ordenadas por zona.
 * Referencia: sección 6.2; sección 7.3; Anexo A.14.
 */
public record MatchParticipant(int seat, UUID playerId, boolean isBot, Long deckId, int energy, boolean active,
        List<MatchCard> cards) {

    public static final int FIRST_SEAT = 1;
    public static final int LAST_SEAT = 2;

    public MatchParticipant {
        cards = List.copyOf(cards);
        if (seat < FIRST_SEAT || seat > LAST_SEAT || energy < 0
                || isBot != (playerId == null) || isBot != (deckId == null)
                || (!isBot && deckId <= 0)) {
            throw new IllegalArgumentException("El asiento, la energía o la identidad del participante no son válidos");
        }
        if (cards.size() != DeckRules.DECK_SIZE) {
            throw new IllegalArgumentException("Cada participante debe conservar todas las cartas de su deck");
        }
        Set<Integer> positions = new HashSet<>();
        Set<String> codes = new HashSet<>();
        for (MatchCard card : cards) {
            Objects.requireNonNull(card, "El estado de cada carta es obligatorio");
            if (card.position() >= DeckRules.DECK_SIZE
                    || !positions.add(card.position()) || !codes.add(card.cardCode())) {
                throw new IllegalArgumentException("Las posiciones y cartas del participante deben ser únicas");
            }
        }
    }
}
