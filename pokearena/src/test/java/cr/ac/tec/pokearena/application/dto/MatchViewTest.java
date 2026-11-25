package cr.ac.tec.pokearena.application.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import cr.ac.tec.pokearena.support.MatchFixtures;

/**
 * Propósito: verificar que la proyección de una partida respeta los límites de visibilidad.
 * Responsabilidades: comprobar que solo expone la mano propia y cantidades, nunca el orden de los decks ni la semilla.
 * Referencia: sección 7.3; criterios C.10-A2; Anexo A.15.
 */
@DisplayName("Vista de inicio de partida")
class MatchViewTest {

    private static final UUID MATCH_ID = UUID.fromString("00000000-0000-0000-0000-000000000301");
    private static final UUID PLAYER_ID = UUID.fromString("00000000-0000-0000-0000-000000000302");
    private static final Instant SERVER_TIME = Instant.parse("2026-11-24T12:00:05Z");

    @Nested
    @DisplayName("Alternos")
    class Alternatives {

        @Test
        @DisplayName("C.10-A2: muestra la mano propia y oculta mano rival, decks y semilla")
        void hidesPrivateMatchState() {
            MatchView view = MatchView.forSeat(MatchFixtures.initial(MATCH_ID, PLAYER_ID), PLAYER_ID, SERVER_TIME);

            assertThat(view.you().hand()).extracting(MatchView.CardView::code)
                    .containsExactly("PKA-FUE-001", "PKA-FUE-002", "PKA-FUE-003", "PKA-FUE-004");
            assertThat(view.you().deckRemaining()).isEqualTo(8);
            assertThat(view.rival().handCount()).isEqualTo(4);
            assertThat(view.rival().deckRemaining()).isEqualTo(8);
            assertThat(view.toString()).doesNotContain("PKA-AGU-", "PKA-FUE-005", "928374");
        }
    }
}
