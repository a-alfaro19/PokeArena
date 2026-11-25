package cr.ac.tec.pokearena.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import cr.ac.tec.pokearena.application.dto.MatchView;

/**
 * Propósito: definir el cuerpo HTTP de la consulta de inicio de partida.
 * Responsabilidades: traducir la vista de aplicación al esquema público sin incluir estado privado.
 * Referencia: sección 7.3; Anexo A.15.
 */
public record MatchResponse(UUID id, String status, String context, boolean vsBot, int turn,
        int turnDurationSeconds, Instant turnEndsAt, Instant serverTime, int bet, List<LocationResponse> locations,
        PlayerViewResponse you, RivalViewResponse rival) {

    public MatchResponse {
        locations = List.copyOf(locations);
    }

    public static MatchResponse from(MatchView view) {
        return new MatchResponse(view.id(), view.status(), view.context(), view.vsBot(), view.turn(),
                view.turnDurationSeconds(), view.turnEndsAt(), view.serverTime(), view.bet(),
                view.locations().stream().map(LocationResponse::from).toList(), PlayerViewResponse.from(view.you()),
                RivalViewResponse.from(view.rival()));
    }

    /** Forma HTTP de una ubicación de la partida. */
    public record LocationResponse(int slot, long id, String name, String description, int cardLimit,
            String extraEnergyMode, int extraEnergyAmount, String imageLargeUrl) {
        private static LocationResponse from(MatchView.LocationView location) {
            return new LocationResponse(location.slot(), location.id(), location.name(), location.description(),
                    location.cardLimit(), location.extraEnergyMode(), location.extraEnergyAmount(),
                    location.imageLargeUrl());
        }
    }

    /** Forma HTTP de una carta que el jugador puede ver. */
    public record CardResponse(String code, String name, int cost, int power, String imageSmallUrl) {
        private static CardResponse from(MatchView.CardView card) {
            return new CardResponse(card.code(), card.name(), card.cost(), card.power(), card.imageSmallUrl());
        }
    }

    /** Forma HTTP del asiento del solicitante. */
    public record PlayerViewResponse(int seat, int energy, List<CardResponse> hand, int deckRemaining,
            List<CardResponse> graveyard) {
        public PlayerViewResponse {
            hand = List.copyOf(hand);
            graveyard = List.copyOf(graveyard);
        }

        private static PlayerViewResponse from(MatchView.PlayerView player) {
            return new PlayerViewResponse(player.seat(), player.energy(),
                    player.hand().stream().map(CardResponse::from).toList(), player.deckRemaining(),
                    player.graveyard().stream().map(CardResponse::from).toList());
        }
    }

    /** Forma HTTP del rival, limitada a conteos y cementerio visible. */
    public record RivalViewResponse(boolean isBot, int handCount, int deckRemaining, List<CardResponse> graveyard) {
        public RivalViewResponse {
            graveyard = List.copyOf(graveyard);
        }

        private static RivalViewResponse from(MatchView.RivalView rival) {
            return new RivalViewResponse(rival.isBot(), rival.handCount(), rival.deckRemaining(),
                    rival.graveyard().stream().map(CardResponse::from).toList());
        }
    }
}
