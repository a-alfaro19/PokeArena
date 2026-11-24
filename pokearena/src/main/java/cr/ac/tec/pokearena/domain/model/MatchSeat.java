package cr.ac.tec.pokearena.domain.model;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import cr.ac.tec.pokearena.domain.rule.DeckRules;

/**
 * Propósito: identificar el jugador o bot y el deck con que entra a una partida.
 * Responsabilidades: conservar los códigos del deck ya validado y distinguir el asiento del bot, que no tiene
 * jugador ni deck persistido.
 * Referencia: secciones 6.2 y 7.3; Anexo A.14; criterio C.10-A3.
 *
 * @param seat      posición del jugador en la partida
 * @param playerId  identificador del jugador, o {@code null} para el bot
 * @param deckId    deck del jugador, o {@code null} para el bot
 * @param deckCodes códigos distintos que componen el deck al entrar a la partida
 */
public record MatchSeat(int seat, UUID playerId, Long deckId, List<String> deckCodes) {

    public static final int FIRST_SEAT = 1;
    public static final int SECOND_SEAT = 2;
    public static final int SEAT_COUNT = SECOND_SEAT - FIRST_SEAT + 1;

    public MatchSeat {
        if (seat != FIRST_SEAT && seat != SECOND_SEAT) {
            throw new IllegalArgumentException("El asiento debe ser uno o dos.");
        }
        if ((playerId == null) != (deckId == null)) {
            throw new IllegalArgumentException("El jugador y su deck deben estar presentes juntos.");
        }
        if (deckId != null && deckId <= 0) {
            throw new IllegalArgumentException("El id del deck debe ser positivo.");
        }
        Objects.requireNonNull(deckCodes, "Los códigos del deck son obligatorios");
        deckCodes = List.copyOf(deckCodes);
        if (deckCodes.size() != DeckRules.DECK_SIZE
                || deckCodes.stream().anyMatch(String::isBlank)
                || new HashSet<>(deckCodes).size() != deckCodes.size()) {
            throw new IllegalArgumentException("El deck de cada asiento debe tener códigos distintos y válidos.");
        }
    }

    public boolean isBot() {
        return playerId == null;
    }
}
