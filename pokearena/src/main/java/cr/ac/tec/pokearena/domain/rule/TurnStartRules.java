package cr.ac.tec.pokearena.domain.rule;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import cr.ac.tec.pokearena.domain.model.AvailableLocation;
import cr.ac.tec.pokearena.domain.model.ExtraEnergy;
import cr.ac.tec.pokearena.domain.model.MatchCard;
import cr.ac.tec.pokearena.domain.model.MatchZone;
import cr.ac.tec.pokearena.domain.model.TurnStartResult;

/**
 * Propósito: aplicar la mano, el robo y la energía al inicio de un turno.
 * Responsabilidades: transformar zonas de cartas y calcular energía sin leer el reloj ni acumular energía.
 * Referencia: sección 7.3, paso 5; Anexo A.14; criterios C.10-E3 a E6.
 */
public final class TurnStartRules {

    public static final int FIRST_TURN = 1;

    private static final String INVALID_SETTINGS = "Los límites de inicio de turno no son válidos";
    private final int initialHandSize;
    private final int maxHandSize;
    private final int initialEnergy;
    private final int energyPerTurn;
    private final int maxTurns;

    public TurnStartRules(int initialHandSize, int maxHandSize, int initialEnergy, int energyPerTurn, int maxTurns) {
        if (initialHandSize <= 0 || maxHandSize <= initialHandSize || maxHandSize > DeckRules.DECK_SIZE
                || initialEnergy < 0 || energyPerTurn < 0 || maxTurns < FIRST_TURN) {
            throw new IllegalArgumentException(INVALID_SETTINGS);
        }
        this.initialHandSize = initialHandSize;
        this.maxHandSize = maxHandSize;
        this.initialEnergy = initialEnergy;
        this.energyPerTurn = energyPerTurn;
        this.maxTurns = maxTurns;
    }

    public List<MatchCard> initialDeck(List<String> shuffledCodes) {
        List<String> codes = List.copyOf(shuffledCodes);
        if (codes.size() != DeckRules.DECK_SIZE || new HashSet<>(codes).size() != DeckRules.DECK_SIZE
                || codes.stream().anyMatch(String::isBlank)) {
            throw new IllegalArgumentException("El deck inicial debe tener exactamente las cartas distintas requeridas");
        }
        List<MatchCard> cards = new ArrayList<>(codes.size());
        for (int position = 0; position < codes.size(); position++) {
            cards.add(new MatchCard(position, codes.get(position), MatchZone.DECK));
        }
        return List.copyOf(cards);
    }

    public TurnStartResult startTurn(int turn, List<MatchCard> currentCards, List<AvailableLocation> locations,
            Map<Long, Integer> ownCardsByLocation) {
        if (turn < FIRST_TURN || turn > maxTurns) {
            throw new IllegalArgumentException("El turno está fuera del rango de la partida");
        }
        List<MatchCard> cards = orderedAndValidated(currentCards);
        List<AvailableLocation> safeLocations = List.copyOf(locations);
        Map<Long, Integer> safeCounts = Map.copyOf(
                Objects.requireNonNull(ownCardsByLocation, "El conteo de cartas por ubicación es obligatorio"));
        Set<Long> locationIds = new HashSet<>();
        if (safeLocations.stream().anyMatch(location -> !locationIds.add(location.id()))) {
            throw new IllegalArgumentException("Las ubicaciones deben ser distintas");
        }
        if (safeCounts.values().stream().anyMatch(count -> count < 0)) {
            throw new IllegalArgumentException("El conteo de cartas propias no puede ser negativo");
        }
        if (safeCounts.keySet().stream().anyMatch(locationId -> !locationIds.contains(locationId))) {
            throw new IllegalArgumentException("El conteo incluye una ubicación fuera de la partida");
        }

        if (turn == FIRST_TURN) {
            if (cards.stream().anyMatch(card -> card.zone() != MatchZone.DECK)) {
                throw new IllegalArgumentException("La mano inicial requiere el deck completo en la zona DECK");
            }
            for (int index = 0; index < initialHandSize; index++) {
                cards.set(index, withZone(cards.get(index), MatchZone.HAND));
            }
        }

        long handSize = cards.stream().filter(card -> card.zone() == MatchZone.HAND).count();
        if (handSize > maxHandSize) {
            throw new IllegalArgumentException("La mano supera el máximo permitido");
        }
        if (handSize < maxHandSize) {
            for (int index = 0; index < cards.size(); index++) {
                if (cards.get(index).zone() == MatchZone.DECK) {
                    cards.set(index, withZone(cards.get(index), MatchZone.HAND));
                    break;
                }
            }
        }

        int energy = Math.addExact(initialEnergy,
                Math.multiplyExact(turn - FIRST_TURN, energyPerTurn));
        for (AvailableLocation location : safeLocations) {
            int ownCount = safeCounts.getOrDefault(location.id(), 0);
            if (ownCount > location.location().fields().cardLimit()) {
                throw new IllegalArgumentException("El conteo supera el límite de cartas de la ubicación");
            }
            ExtraEnergy extra = location.location().fields().extraEnergy();
            int amount = switch (extra.mode()) {
                case NONE -> 0;
                case FIXED -> extra.amount();
                case PER_CARD -> Math.multiplyExact(extra.amount(), ownCount);
            };
            energy = Math.addExact(energy, amount);
        }
        return new TurnStartResult(energy, cards);
    }

    private static List<MatchCard> orderedAndValidated(List<MatchCard> currentCards) {
        List<MatchCard> cards = new ArrayList<>(currentCards);
        if (cards.size() != DeckRules.DECK_SIZE) {
            throw new IllegalArgumentException("El estado del participante debe conservar todas las cartas del deck");
        }
        cards.sort(Comparator.comparingInt(MatchCard::position));
        Set<Integer> positions = new HashSet<>();
        Set<String> codes = new HashSet<>();
        for (MatchCard card : cards) {
            Objects.requireNonNull(card, "El estado de cada carta es obligatorio");
            if (card.position() >= DeckRules.DECK_SIZE
                    || !positions.add(card.position()) || !codes.add(card.cardCode())) {
                throw new IllegalArgumentException("Las posiciones y cartas del deck deben ser únicas");
            }
        }
        return cards;
    }

    private static MatchCard withZone(MatchCard card, MatchZone zone) {
        return new MatchCard(card.position(), card.cardCode(), zone);
    }
}
