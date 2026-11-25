package cr.ac.tec.pokearena.domain.rule;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import cr.ac.tec.pokearena.domain.effect.LocationEffectRegistry;
import cr.ac.tec.pokearena.domain.error.LocationsUnavailableException;
import cr.ac.tec.pokearena.domain.model.AvailableLocation;
import cr.ac.tec.pokearena.domain.model.LocationEffect;

/**
 * Propósito: seleccionar ubicaciones distintas para una partida.
 * Responsabilidades: barajar de forma determinista y asegurar el mínimo de ubicaciones jugables.
 * Referencia: sección 7.3, paso 3; Anexo A.14; criterios C.10-E1 y C.10-R1.
 */
public final class LocationSelectionRules {

    private final int locationsPerMatch;
    private final int minPlayableLocations;
    private final LocationEffectRegistry effects;

    public LocationSelectionRules(int locationsPerMatch, int minPlayableLocations, LocationEffectRegistry effects) {
        this.effects = Objects.requireNonNull(effects, "El registro de efectos es obligatorio");
        if (locationsPerMatch <= 0 || minPlayableLocations <= 0 || minPlayableLocations > locationsPerMatch) {
            throw new IllegalArgumentException("Los límites de selección de ubicaciones no son válidos");
        }
        this.locationsPerMatch = locationsPerMatch;
        this.minPlayableLocations = minPlayableLocations;
    }

    public List<AvailableLocation> select(List<AvailableLocation> available, long seed) {
        List<AvailableLocation> ordered = List.copyOf(available).stream()
                .sorted(Comparator.comparingLong(AvailableLocation::id))
                .toList();
        Set<Long> locationIds = new HashSet<>();
        if (ordered.stream().anyMatch(location -> !locationIds.add(location.id()))
                || ordered.size() < locationsPerMatch
                || ordered.stream().filter(this::isPlayable).count() < minPlayableLocations) {
            throw new LocationsUnavailableException();
        }

        List<AvailableLocation> shuffled = SeededShuffle.shuffle(ordered, seed);
        List<AvailableLocation> selected = new ArrayList<>(shuffled.subList(0, locationsPerMatch));
        List<AvailableLocation> remaining = new ArrayList<>(shuffled.subList(locationsPerMatch, shuffled.size()));
        int playable = (int) selected.stream().filter(this::isPlayable).count();

        for (int index = 0; playable < minPlayableLocations; index++) {
            if (isPlayable(selected.get(index))) {
                continue;
            }
            int replacement = nextPlayableIndex(remaining);
            if (replacement < 0) {
                throw new LocationsUnavailableException();
            }
            selected.set(index, remaining.remove(replacement));
            playable++;
        }
        return List.copyOf(selected);
    }

    private int nextPlayableIndex(List<AvailableLocation> candidates) {
        for (int index = 0; index < candidates.size(); index++) {
            if (isPlayable(candidates.get(index))) {
                return index;
            }
        }
        return -1;
    }

    private boolean isPlayable(AvailableLocation candidate) {
        if (candidate.location().fields().cardLimit() <= 0) {
            return false;
        }
        LocationEffect effect = candidate.location().effect();
        return effect == null || !effects.blocksAllTurns(effect);
    }
}
