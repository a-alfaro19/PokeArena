package cr.ac.tec.pokearena.application.dto;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import cr.ac.tec.pokearena.domain.error.MatchException;
import cr.ac.tec.pokearena.domain.model.Card;
import cr.ac.tec.pokearena.domain.model.Match;
import cr.ac.tec.pokearena.domain.model.MatchCard;
import cr.ac.tec.pokearena.domain.model.MatchLocation;
import cr.ac.tec.pokearena.domain.model.MatchParticipant;
import cr.ac.tec.pokearena.domain.model.MatchZone;

/**
 * Propósito: vista inicial de una partida para un único participante.
 * Responsabilidades: proyectar el estado visible sin revelar la mano rival, órdenes de deck ni semilla.
 * Referencia: sección 7.3; Anexo A.15, mensajes 9 a 12.
 */
public record MatchView(UUID id, String status, String context, boolean vsBot, int turn, int turnDurationSeconds,
        Instant turnEndsAt, Instant serverTime, int bet, List<LocationView> locations, PlayerView you,
        RivalView rival) {

    public MatchView {
        Objects.requireNonNull(id, "El id de partida es obligatorio");
        Objects.requireNonNull(status, "El estado de partida es obligatorio");
        Objects.requireNonNull(context, "El contexto de partida es obligatorio");
        Objects.requireNonNull(turnEndsAt, "La hora de fin del turno es obligatoria");
        Objects.requireNonNull(serverTime, "La hora del servidor es obligatoria");
        locations = List.copyOf(locations);
        Objects.requireNonNull(you, "La vista propia es obligatoria");
        Objects.requireNonNull(rival, "La vista rival es obligatoria");
    }

    /** Construye la proyección segura para el jugador que participa en la partida. */
    public static MatchView forSeat(Match match, UUID playerId, Instant serverTime) {
        Objects.requireNonNull(match, "La partida es obligatoria");
        Objects.requireNonNull(playerId, "El jugador es obligatorio");
        Objects.requireNonNull(serverTime, "La hora del servidor es obligatoria");

        MatchParticipant you = match.participants().stream()
                .filter(participant -> playerId.equals(participant.playerId()))
                .findFirst()
                .orElseThrow(MatchException::notFound);
        MatchParticipant rival = match.participants().stream()
                .filter(participant -> participant.seat() != you.seat())
                .findFirst()
                .orElseThrow(MatchException::notFound);

        return new MatchView(match.id(), match.status().name(), match.context().name(), match.vsBot(), match.turn(),
                match.turnDurationSeconds(), match.turnStartedAt().plusSeconds(match.turnDurationSeconds()), serverTime,
                match.bet(), match.locations().stream().sorted(Comparator.comparingInt(MatchLocation::slot))
                        .map(MatchView::location).toList(),
                player(match, you), rival(match, rival));
    }

    private static LocationView location(MatchLocation assigned) {
        var location = assigned.location();
        var fields = location.fields();
        var energy = fields.extraEnergy();
        return new LocationView(assigned.slot(), assigned.locationId(), fields.name(), fields.description(),
                fields.cardLimit(), energy.mode().name(), energy.amount(), location.imageLargeUrl());
    }

    private static PlayerView player(Match match, MatchParticipant participant) {
        List<MatchCard> cards = participant.cards();
        return new PlayerView(participant.seat(), participant.energy(),
                cardsInZone(match, cards, MatchZone.HAND), countInZone(cards, MatchZone.DECK),
                cardsInZone(match, cards, MatchZone.GRAVEYARD));
    }

    private static RivalView rival(Match match, MatchParticipant participant) {
        List<MatchCard> cards = participant.cards();
        return new RivalView(participant.isBot(), countInZone(cards, MatchZone.HAND),
                countInZone(cards, MatchZone.DECK), cardsInZone(match, cards, MatchZone.GRAVEYARD));
    }

    private static List<CardView> cardsInZone(Match match, List<MatchCard> cards, MatchZone zone) {
        return cards.stream()
                .filter(card -> card.zone() == zone)
                .sorted(Comparator.comparingInt(MatchCard::position))
                .map(card -> card(match, card))
                .toList();
    }

    private static int countInZone(List<MatchCard> cards, MatchZone zone) {
        return Math.toIntExact(cards.stream().filter(card -> card.zone() == zone).count());
    }

    private static CardView card(Match match, MatchCard matchCard) {
        Card card = match.cardCatalog().get(matchCard.cardCode());
        if (card == null) {
            throw new IllegalStateException("Falta la ficha de una carta guardada en la partida");
        }
        if (card.imageSmallUrl() == null) {
            throw new IllegalStateException("La ficha de una carta visible no tiene miniatura");
        }
        return new CardView(card.code().value(), card.fields().name(), card.fields().cost(), card.fields().power(),
                card.imageSmallUrl());
    }

    /** Datos públicos de una ubicación asignada. */
    public record LocationView(int slot, long id, String name, String description, int cardLimit,
            String extraEnergyMode, int extraEnergyAmount, String imageLargeUrl) {
    }

    /** Ficha de una carta visible en una mano o cementerio. */
    public record CardView(String code, String name, int cost, int power, String imageSmallUrl) {
    }

    /** Estado visible del solicitante. */
    public record PlayerView(int seat, int energy, List<CardView> hand, int deckRemaining, List<CardView> graveyard) {
        public PlayerView {
            hand = List.copyOf(hand);
            graveyard = List.copyOf(graveyard);
        }
    }

    /** Estado limitado del rival; nunca incluye su mano ni el orden de su deck. */
    public record RivalView(boolean isBot, int handCount, int deckRemaining, List<CardView> graveyard) {
        public RivalView {
            graveyard = List.copyOf(graveyard);
        }
    }
}
