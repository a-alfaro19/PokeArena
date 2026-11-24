package cr.ac.tec.pokearena.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import cr.ac.tec.pokearena.application.dto.MatchStartView;
import cr.ac.tec.pokearena.domain.model.ExtraEnergy;
import cr.ac.tec.pokearena.domain.model.MatchContext;
import cr.ac.tec.pokearena.domain.model.MatchStatus;

/**
 * Propósito: cuerpo HTTP de la consulta del inicio de una partida (esquema MatchResponse).
 * Responsabilidades: exponer las ubicaciones y solo la información permitida del jugador y su rival.
 * Referencia: sección 7.3; Anexo A.15; criterio C.10-A2.
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
public record MatchResponse(UUID id, MatchStatus status, MatchContext context, boolean vsBot, int turn,
        int turnDurationSeconds, Instant turnEndsAt, Instant serverTime, int bet, List<MatchLocationResponse> locations,
        MatchOwnSeatResponse you, MatchRivalSeatResponse rival) {

    public static MatchResponse from(MatchStartView match) {
        return new MatchResponse(match.id(), match.status(), match.context(), match.vsBot(), match.turn(),
                match.turnDurationSeconds(), match.turnEndsAt(), match.serverTime(), match.bet(),
                match.locations().stream().map(location -> new MatchLocationResponse(location.slot(), location.id(),
                        location.name(), location.description(), location.cardLimit(), location.extraEnergyMode(),
                        location.extraEnergyAmount(), location.imageLargeUrl())).toList(),
                new MatchOwnSeatResponse(match.you().seat(), match.you().energy(), cards(match.you().hand()),
                        match.you().deckRemaining(), cards(match.you().graveyard())),
                new MatchRivalSeatResponse(match.rival().isBot(), match.rival().handCount(),
                        match.rival().deckRemaining(), cards(match.rival().graveyard())));
    }

    private static List<MatchCardResponse> cards(List<MatchStartView.Card> cards) {
        return cards.stream()
                .map(card -> new MatchCardResponse(card.code(), card.name(), card.cost(), card.power(),
                        card.imageSmallUrl()))
                .toList();
    }

    public record MatchLocationResponse(int slot, long id, String name, String description, int cardLimit,
            ExtraEnergy.Mode extraEnergyMode, int extraEnergyAmount, String imageLargeUrl) {
    }

    public record MatchOwnSeatResponse(int seat, int energy, List<MatchCardResponse> hand, int deckRemaining,
            List<MatchCardResponse> graveyard) {
    }

    public record MatchRivalSeatResponse(boolean isBot, int handCount, int deckRemaining,
            List<MatchCardResponse> graveyard) {
    }

    public record MatchCardResponse(String code, String name, int cost, int power, String imageSmallUrl) {
    }
}
