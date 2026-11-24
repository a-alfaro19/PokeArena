package cr.ac.tec.pokearena.domain.rule;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import cr.ac.tec.pokearena.domain.model.Card;
import cr.ac.tec.pokearena.domain.model.Match;
import cr.ac.tec.pokearena.domain.model.MatchCard;
import cr.ac.tec.pokearena.domain.model.MatchLocation;
import cr.ac.tec.pokearena.domain.model.MatchParticipant;
import cr.ac.tec.pokearena.domain.model.MatchViewResult;
import cr.ac.tec.pokearena.domain.model.MatchZone;

/**
 * Propósito: construir la vista de una partida para un asiento sin revelar información oculta.
 * Responsabilidades: mostrar las ubicaciones, la mano y el cementerio propios, y solo conteos de la mano y el
 * deck del rival.
 * Referencia: sección 7.3 y Anexo A.15, mensajes 10 y 11; criterios C.10-A2.
 */
public final class MatchView {

    public MatchViewResult forSeat(Match match, int seat, Instant serverTime) {
        Objects.requireNonNull(match, "La partida es obligatoria");
        Objects.requireNonNull(serverTime, "La hora del servidor es obligatoria");
        MatchParticipant own = match.participants().stream()
                .filter(participant -> participant.seat() == seat)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("El asiento no participa en la partida."));
        MatchParticipant rival = match.participants().stream()
                .filter(participant -> participant.seat() != seat)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("La partida no tiene el otro asiento."));

        List<MatchViewResult.Location> locations = match.locations().stream()
                .sorted(Comparator.comparingInt(MatchLocation::slot))
                .map(location -> new MatchViewResult.Location(location.slot(), location.id(),
                        location.location().fields().name(), location.location().fields().description(),
                        location.location().fields().cardLimit(), location.location().fields().extraEnergy().mode(),
                        location.location().fields().extraEnergy().amount(), location.location().imageLargeUrl()))
                .toList();

        List<MatchViewResult.Card> ownHand = cardsInZone(match, own, MatchZone.HAND);
        List<MatchViewResult.Card> ownGraveyard = cardsInZone(match, own, MatchZone.GRAVEYARD);
        List<MatchViewResult.Card> rivalGraveyard = cardsInZone(match, rival, MatchZone.GRAVEYARD);
        int rivalHandCount = countInZone(rival, MatchZone.HAND);

        return new MatchViewResult(match.id(), match.status(), match.context(), match.vsBot(), match.turn(),
                match.turnDurationSeconds(), match.turnStartedAt().plusSeconds(match.turnDurationSeconds()),
                serverTime, match.bet(), locations,
                new MatchViewResult.OwnSeat(own.seat(), own.energy(), ownHand,
                        countInZone(own, MatchZone.DECK), ownGraveyard),
                new MatchViewResult.RivalSeat(rival.isBot(), rivalHandCount,
                        countInZone(rival, MatchZone.DECK), rivalGraveyard));
    }

    private List<MatchViewResult.Card> cardsInZone(Match match, MatchParticipant participant, MatchZone zone) {
        return participant.cards().stream()
                .filter(card -> card.zone() == zone)
                .map(card -> cardView(match, card))
                .toList();
    }

    private MatchViewResult.Card cardView(Match match, MatchCard state) {
        Card card = match.cardsByCode().get(state.code());
        if (card == null) {
            throw new IllegalStateException("Falta la ficha de catálogo para la carta " + state.code() + ".");
        }
        if (card.imageSmallUrl() == null) {
            throw new IllegalStateException("Falta la miniatura de la carta " + state.code() + ".");
        }
        return new MatchViewResult.Card(state.code(), card.fields().name(), card.fields().cost(),
                card.fields().power(), card.imageSmallUrl());
    }

    private static int countInZone(MatchParticipant participant, MatchZone zone) {
        return Math.toIntExact(participant.cards().stream().filter(card -> card.zone() == zone).count());
    }
}
