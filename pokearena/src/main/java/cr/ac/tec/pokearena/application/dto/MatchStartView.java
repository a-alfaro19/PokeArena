package cr.ac.tec.pokearena.application.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import cr.ac.tec.pokearena.domain.model.ExtraEnergy;
import cr.ac.tec.pokearena.domain.model.MatchContext;
import cr.ac.tec.pokearena.domain.model.MatchStatus;
import cr.ac.tec.pokearena.domain.model.MatchViewResult;

/**
 * Propósito: resultado de Consultar el inicio de una partida.
 * Responsabilidades: transportar a la API la proyección segura producida por el dominio.
 * Referencia: sección 7.3 y Anexo A.15, mensajes 12 y 13.
 *
 * @param id identificador de la partida
 * @param status estado de la partida
 * @param context origen de la partida
 * @param vsBot indica si juega contra el bot
 * @param turn turno actual
 * @param turnDurationSeconds duración del turno
 * @param turnEndsAt hora de finalización del turno
 * @param serverTime hora actual del servidor
 * @param bet apuesta registrada
 * @param locations ubicaciones visibles y ordenadas por espacio
 * @param you asiento del jugador autenticado
 * @param rival datos visibles del otro asiento
 */
public record MatchStartView(UUID id, MatchStatus status, MatchContext context, boolean vsBot, int turn,
        int turnDurationSeconds, Instant turnEndsAt, Instant serverTime, int bet, List<Location> locations,
        OwnSeat you, RivalSeat rival) {

    public MatchStartView {
        locations = List.copyOf(locations);
    }

    public static MatchStartView from(MatchViewResult result) {
        return new MatchStartView(result.id(), result.status(), result.context(), result.vsBot(), result.turn(),
                result.turnDurationSeconds(), result.turnEndsAt(), result.serverTime(), result.bet(),
                result.locations().stream().map(location -> new Location(location.slot(), location.id(),
                        location.name(), location.description(), location.cardLimit(), location.extraEnergyMode(),
                        location.extraEnergyAmount(), location.imageLargeUrl())).toList(),
                new OwnSeat(result.you().seat(), result.you().energy(), cards(result.you().hand()),
                        result.you().deckRemaining(), cards(result.you().graveyard())),
                new RivalSeat(result.rival().isBot(), result.rival().handCount(), result.rival().deckRemaining(),
                        cards(result.rival().graveyard())));
    }

    private static List<Card> cards(List<MatchViewResult.Card> cards) {
        return cards.stream()
                .map(card -> new Card(card.code(), card.name(), card.cost(), card.power(), card.imageSmallUrl()))
                .toList();
    }

    public record Location(int slot, long id, String name, String description, int cardLimit,
            ExtraEnergy.Mode extraEnergyMode, int extraEnergyAmount, String imageLargeUrl) {
    }

    public record Card(String code, String name, int cost, int power, String imageSmallUrl) {
    }

    public record OwnSeat(int seat, int energy, List<Card> hand, int deckRemaining, List<Card> graveyard) {
        public OwnSeat {
            hand = List.copyOf(hand);
            graveyard = List.copyOf(graveyard);
        }
    }

    public record RivalSeat(boolean isBot, int handCount, int deckRemaining, List<Card> graveyard) {
        public RivalSeat {
            graveyard = List.copyOf(graveyard);
        }
    }
}
