package cr.ac.tec.pokearena.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import cr.ac.tec.pokearena.domain.rule.DeckRules;
import cr.ac.tec.pokearena.domain.rule.TurnStartRules;

/**
 * Propósito: verificar las invariantes del agregado listo para persistirse.
 * Responsabilidades: cubrir ubicación única, dos asientos activos y consistencia del asiento bot.
 * Referencia: sección 6.2; sección 7.3, paso 6; Anexo A.14.
 */
@DisplayName("Modelo de partida nueva")
class NewMatchTest {

    private static final Instant NOW = Instant.parse("2026-11-24T12:00:00Z");
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    @DisplayName("Acepta dos asientos activos y tres ubicaciones distintas en el turno inicial")
    void acceptsValidInitialState() {
        NewMatch match = match(locations(), List.of(humanSeat(), botSeat()), true);

        assertThat(match.status()).isEqualTo(MatchStatus.ACTIVE);
        assertThat(match.turn()).isEqualTo(TurnStartRules.FIRST_TURN);
        assertThat(match.locations()).hasSize(MatchLocation.LAST_SLOT);
        assertThat(match.participants()).hasSize(MatchParticipant.LAST_SEAT);
        assertThat(match.participants().getFirst().cards())
                .filteredOn(card -> card.zone() == MatchZone.HAND).hasSize(4);
    }

    @Test
    @DisplayName("Rechaza ubicaciones repetidas aunque ocupen espacios diferentes")
    void rejectsDuplicateLocationIds() {
        List<MatchLocation> duplicates = List.of(
                new MatchLocation(1, availableLocation(20)),
                new MatchLocation(2, availableLocation(20)),
                new MatchLocation(3, availableLocation(21)));

        assertThatThrownBy(() -> match(duplicates, List.of(humanSeat(), botSeat()), true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Rechaza inconsistencias entre el contexto contra bot y los participantes")
    void rejectsBotContextMismatch() {
        assertThatThrownBy(() -> match(locations(), List.of(humanSeat(), botSeat()), false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> match(locations(), List.of(humanSeat(), humanSeat(2)), true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Rechaza un asiento inactivo al crear la partida")
    void rejectsInactiveInitialSeat() {
        MatchParticipant inactive = new MatchParticipant(2, null, true, null, 2, false, initialCards());

        assertThatThrownBy(() -> match(locations(), List.of(humanSeat(), inactive), true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Un participante humano necesita un id de deck positivo")
    void rejectsInvalidDeckId() {
        assertThatThrownBy(() -> new MatchParticipant(1, PLAYER, false, 0L, 2, true, initialCards()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static NewMatch match(List<MatchLocation> locations, List<MatchParticipant> participants,
            boolean vsBot) {
        return new NewMatch(MatchContext.RANKED, vsBot, MatchStatus.ACTIVE, 123L, 4,
                TurnStartRules.FIRST_TURN, NOW, 30, NOW, locations, participants);
    }

    private static List<MatchLocation> locations() {
        return List.of(
                new MatchLocation(1, availableLocation(20)),
                new MatchLocation(2, availableLocation(21)),
                new MatchLocation(3, availableLocation(22)));
    }

    private static AvailableLocation availableLocation(long id) {
        LocationFields fields = new LocationFields("Location " + id, "Description " + id, 3, ExtraEnergy.none());
        return new AvailableLocation(id, Location.create(fields, null, "large", "small", NOW));
    }

    private static MatchParticipant humanSeat() {
        return new MatchParticipant(1, PLAYER, false, 30L, 2, true, initialCards());
    }

    private static MatchParticipant humanSeat(int seat) {
        return new MatchParticipant(seat, UUID.fromString("00000000-0000-0000-0000-000000000002"),
                false, 31L, 2, true, initialCards());
    }

    private static MatchParticipant botSeat() {
        return new MatchParticipant(2, null, true, null, 2, true, initialCards());
    }

    private static List<MatchCard> initialCards() {
        return IntStream.range(0, DeckRules.DECK_SIZE)
                .mapToObj(position -> new MatchCard(position, "PKA-FUE-%03d".formatted(position + 1),
                        position < 4 ? MatchZone.HAND : MatchZone.DECK))
                .toList();
    }
}
