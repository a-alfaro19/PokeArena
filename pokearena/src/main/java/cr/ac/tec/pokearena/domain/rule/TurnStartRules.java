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
 * Propósito: aplicar las reglas puras de inicio de turno para un asiento.
 * Responsabilidades: repartir la mano inicial, robar una carta cuando hay espacio y calcular energía nueva
 * desde la base del turno más la energía de las ubicaciones.
 * Referencia: sección 7.3 ("Inicio de turno"), paso 5; Anexo A.14; criterios C.10-E2 a C.10-E5.
 */
public final class TurnStartRules {

    private final int initialHandSize;
    private final int maxHandSize;
    private final int initialEnergy;
    private final int energyPerTurn;
    private final int maxTurns;

    public TurnStartRules(int initialHandSize, int maxHandSize, int initialEnergy, int energyPerTurn, int maxTurns) {
        if (initialHandSize < 0 || maxHandSize <= 0 || initialHandSize > maxHandSize
                || initialEnergy < 0 || energyPerTurn < 0 || maxTurns <= 0) {
            throw new IllegalArgumentException("La configuración de inicio de turno no es válida.");
        }
        this.initialHandSize = initialHandSize;
        this.maxHandSize = maxHandSize;
        this.initialEnergy = initialEnergy;
        this.energyPerTurn = energyPerTurn;
        this.maxTurns = maxTurns;
    }

    /**
     * Construye el estado inicial de cartas del deck ya barajado, con posiciones desde cero.
     *
     * @param shuffledCodes códigos en el orden barajado
     * @return todas las cartas inicialmente en la zona DECK
     */
    public static List<MatchCard> initialDeck(List<String> shuffledCodes) {
        Objects.requireNonNull(shuffledCodes, "El deck barajado es obligatorio");
        Set<String> seen = new HashSet<>();
        List<MatchCard> cards = new ArrayList<>(shuffledCodes.size());
        for (int position = 0; position < shuffledCodes.size(); position++) {
            String code = shuffledCodes.get(position);
            if (code == null || code.isBlank() || !seen.add(code)) {
                throw new IllegalArgumentException("El deck barajado debe contener códigos distintos y no vacíos.");
            }
            cards.add(new MatchCard(code, position, MatchZone.DECK));
        }
        return List.copyOf(cards);
    }

    /**
     * Inicia el turno y devuelve la nueva zona de cada carta y la energía de este turno.
     *
     * @param turn turno dentro del rango de la partida
     * @param cards cartas con las zonas previas; en el turno 1 todas deben estar en DECK
     * @param locations ubicaciones seleccionadas para esta partida
     * @param ownCardCounts cantidad de cartas propias por id de ubicación, tomada al inicio del turno
     * @return estado actualizado de cartas y energía del turno
     */
    public TurnStartResult startTurn(int turn, List<MatchCard> cards, List<AvailableLocation> locations,
            Map<Long, Integer> ownCardCounts) {
        if (turn < 1 || turn > maxTurns) {
            throw new IllegalArgumentException("El turno debe estar entre 1 y " + maxTurns + ".");
        }
        Objects.requireNonNull(cards, "El estado de las cartas es obligatorio");
        Objects.requireNonNull(locations, "Las ubicaciones son obligatorias");
        Objects.requireNonNull(ownCardCounts, "El conteo de cartas por ubicación es obligatorio");

        List<MatchCard> ordered = validateAndOrder(cards);
        List<MatchCard> updated = new ArrayList<>(ordered);
        if (turn == 1) {
            if (updated.stream().anyMatch(card -> card.zone() != MatchZone.DECK)) {
                throw new IllegalArgumentException("Las cartas deben iniciar en DECK durante el turno 1.");
            }
            int cardsToDeal = Math.min(initialHandSize, updated.size());
            for (int index = 0; index < cardsToDeal; index++) {
                updated.set(index, updated.get(index).inZone(MatchZone.HAND));
            }
        }
        int handSize = (int) updated.stream().filter(card -> card.zone() == MatchZone.HAND).count();
        if (handSize < maxHandSize) {
            updated.stream().filter(card -> card.zone() == MatchZone.DECK).findFirst().ifPresent(card -> {
                int index = card.position();
                updated.set(index, card.inZone(MatchZone.HAND));
            });
        }

        int energy = Math.addExact(initialEnergy, Math.multiplyExact(turn - 1, energyPerTurn));
        for (AvailableLocation location : locations) {
            int count = ownCardCounts.getOrDefault(location.id(), 0);
            if (count < 0) {
                throw new IllegalArgumentException("El conteo de cartas propias no puede ser negativo.");
            }
            ExtraEnergy extra = location.location().fields().extraEnergy();
            int additional = switch (extra.mode()) {
                case NONE -> 0;
                case FIXED -> extra.amount();
                case PER_CARD -> Math.multiplyExact(extra.amount(), count);
            };
            energy = Math.addExact(energy, additional);
        }
        return new TurnStartResult(updated, energy);
    }

    private List<MatchCard> validateAndOrder(List<MatchCard> cards) {
        List<MatchCard> ordered = cards.stream().sorted(Comparator.comparingInt(MatchCard::position)).toList();
        Set<String> codes = new HashSet<>();
        for (int index = 0; index < ordered.size(); index++) {
            MatchCard card = ordered.get(index);
            if (card.position() != index || !codes.add(card.code())) {
                throw new IllegalArgumentException("Las cartas deben tener posiciones consecutivas y códigos únicos.");
            }
        }
        return ordered;
    }
}
