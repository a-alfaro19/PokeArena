package cr.ac.tec.pokearena.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Propósito: proyección pública y segura de una partida para un asiento.
 * Responsabilidades: llevar solo los datos que la interfaz necesita, excluyendo semilla, orden del deck y mano
 * del rival.
 * Referencia: sección 7.3 y Anexo A.15, mensajes 10 y 11.
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
public record MatchViewResult(UUID id, MatchStatus status, MatchContext context, boolean vsBot, int turn,
        int turnDurationSeconds, Instant turnEndsAt, Instant serverTime, int bet, List<Location> locations,
        OwnSeat you, RivalSeat rival) {

    public MatchViewResult {
        locations = List.copyOf(locations);
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
