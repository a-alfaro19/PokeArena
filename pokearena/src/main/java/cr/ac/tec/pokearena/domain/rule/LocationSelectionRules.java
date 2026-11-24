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
 * Propósito: escoger las ubicaciones iniciales de una partida con una semilla reproducible.
 * Responsabilidades: seleccionar ubicaciones distintas y garantizar el mínimo jugable sin depender de datos
 * del cliente ni de condicionales por tipo de efecto.
 * Referencia: sección 7.3, pasos 2 y 3; Anexo A.14; criterio C.10-E1 y C.10-R1.
 */
public final class LocationSelectionRules {

    private final int locationsPerMatch;
    private final int minPlayableLocations;
    private final LocationEffectRegistry effects;

    public LocationSelectionRules(int locationsPerMatch, int minPlayableLocations, LocationEffectRegistry effects) {
        if (locationsPerMatch <= 0 || minPlayableLocations <= 0 || minPlayableLocations > locationsPerMatch) {
            throw new IllegalArgumentException("La cantidad de ubicaciones jugables debe caber en la partida.");
        }
        this.locationsPerMatch = locationsPerMatch;
        this.minPlayableLocations = minPlayableLocations;
        this.effects = Objects.requireNonNull(effects, "El registro de efectos es obligatorio");
    }

    /**
     * Baraja las ubicaciones en orden estable por id y reemplaza las no jugables hasta cumplir el mínimo.
     *
     * @param available ubicaciones disponibles y activas
     * @param seed      semilla derivada para la selección de ubicaciones
     * @return ubicaciones seleccionadas en su orden reproducible de partida
     * @throws LocationsUnavailableException si el catálogo no puede satisfacer la cantidad y el mínimo jugable
     */
    public List<AvailableLocation> select(List<AvailableLocation> available, long seed) {
        Objects.requireNonNull(available, "El catálogo de ubicaciones es obligatorio");
        List<AvailableLocation> ordered = available.stream()
                .sorted(Comparator.comparingLong(AvailableLocation::id))
                .toList();
        Set<Long> ids = new HashSet<>();
        if (ordered.stream().anyMatch(location -> !ids.add(location.id()))) {
            throw new IllegalArgumentException("El catálogo contiene ids de ubicación repetidos.");
        }
        if (ordered.size() < locationsPerMatch) {
            throw new LocationsUnavailableException(
                    "No hay suficientes ubicaciones disponibles para preparar la partida.");
        }

        List<AvailableLocation> shuffled = SeededShuffle.shuffle(ordered, seed);
        List<AvailableLocation> selected = new ArrayList<>(shuffled.subList(0, locationsPerMatch));
        long playableTotal = ordered.stream().filter(this::isPlayable).count();
        if (playableTotal < minPlayableLocations) {
            throw unavailablePlayable();
        }

        long selectedPlayable = selected.stream().filter(this::isPlayable).count();
        int nextCandidate = locationsPerMatch;
        while (selectedPlayable < minPlayableLocations) {
            while (nextCandidate < shuffled.size() && !isPlayable(shuffled.get(nextCandidate))) {
                nextCandidate++;
            }
            if (nextCandidate == shuffled.size()) {
                throw unavailablePlayable();
            }
            int replaceIndex = firstUnplayable(selected);
            selected.set(replaceIndex, shuffled.get(nextCandidate));
            nextCandidate++;
            selectedPlayable++;
        }
        return List.copyOf(selected);
    }

    private int firstUnplayable(List<AvailableLocation> selected) {
        for (int index = 0; index < selected.size(); index++) {
            if (!isPlayable(selected.get(index))) {
                return index;
            }
        }
        throw new IllegalStateException("La selección no tiene una ubicación reemplazable.");
    }

    private boolean isPlayable(AvailableLocation available) {
        if (available.location().fields().cardLimit() <= 0) {
            return false;
        }
        LocationEffect effect = available.location().effect();
        return effect == null || !effects.blocksAllTurns(effect);
    }

    private LocationsUnavailableException unavailablePlayable() {
        return new LocationsUnavailableException(
                "No hay al menos " + minPlayableLocations + " ubicaciones que permitan jugar cartas.");
    }
}
