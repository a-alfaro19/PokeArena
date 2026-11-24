package cr.ac.tec.pokearena.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import cr.ac.tec.pokearena.domain.rule.DeckRules;

/**
 * Propósito: comprobar el modelo inmutable de los asientos que entran a una partida.
 * Responsabilidades: validar la identidad del jugador o bot y que cada asiento aporte un deck completo de
 * cartas distintas.
 * Referencia: secciones 6.2 y 7.3; Anexo A.14; criterio C.10-A3.
 */
@DisplayName("Asiento de una partida")
class MatchSeatTest {

    private static final UUID PLAYER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final long DECK_ID = 17L;

    @Test
    @DisplayName("C.10-A3: el asiento de jugador y el asiento del bot conservan sus decks sin confundir identidad")
    void distinguishesPlayerAndBotSeats() {
        MatchSeat player = new MatchSeat(MatchSeat.FIRST_SEAT, PLAYER_ID, DECK_ID, codes());
        MatchSeat bot = new MatchSeat(MatchSeat.SECOND_SEAT, null, null, codes());

        assertThat(player.isBot()).isFalse();
        assertThat(player.playerId()).isEqualTo(PLAYER_ID);
        assertThat(player.deckId()).isEqualTo(DECK_ID);
        assertThat(bot.isBot()).isTrue();
        assertThat(bot.deckCodes()).containsExactlyElementsOf(codes());
    }

    @Test
    @DisplayName("El asiento rechaza identidades incompletas, decks de tamaño incorrecto y cartas repetidas")
    void rejectsInvalidSeatData() {
        assertThatThrownBy(() -> new MatchSeat(MatchSeat.FIRST_SEAT, PLAYER_ID, null, codes()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MatchSeat(MatchSeat.FIRST_SEAT, PLAYER_ID, DECK_ID, codes().subList(1, codes().size())))
                .isInstanceOf(IllegalArgumentException.class);

        List<String> duplicate = new java.util.ArrayList<>(codes());
        duplicate.set(1, duplicate.getFirst());
        assertThatThrownBy(() -> new MatchSeat(MatchSeat.FIRST_SEAT, PLAYER_ID, DECK_ID, duplicate))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static List<String> codes() {
        return IntStream.range(0, DeckRules.DECK_SIZE)
                .mapToObj(index -> "PKA-FUE-%03d".formatted(index + 1)).toList();
    }
}
