package cr.ac.tec.pokearena.domain.rule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import cr.ac.tec.pokearena.domain.effect.LocationEffectRegistry;
import cr.ac.tec.pokearena.domain.effect.TurnRestrictionEffect;
import cr.ac.tec.pokearena.domain.error.LocationsUnavailableException;
import cr.ac.tec.pokearena.domain.model.AvailableLocation;
import cr.ac.tec.pokearena.domain.model.ExtraEnergy;
import cr.ac.tec.pokearena.domain.model.Location;
import cr.ac.tec.pokearena.domain.model.LocationEffect;
import cr.ac.tec.pokearena.domain.model.LocationFields;

/**
 * Propósito: probar la selección de ubicaciones iniciales con una semilla fija.
 * Responsabilidades: garantizar cantidad, unicidad, mínimo de ubicaciones jugables, reemplazo reproducible y
 * rechazo cuando el catálogo no satisface las reglas.
 * Referencia: sección 7.3, paso 3; criterios C.10-E1 y C.10-R1.
 */
@DisplayName("Selección de ubicaciones de partida")
class LocationSelectionRulesTest {

    private static final int LOCATIONS_PER_MATCH = 3;
    private static final int MIN_PLAYABLE_LOCATIONS = 2;
    private static final Instant CREATED_AT = Instant.parse("2026-11-20T12:00:00Z");
    private static final LocationEffectRegistry EFFECTS =
            new LocationEffectRegistry(List.of(new TurnRestrictionEffect()));

    private final LocationSelectionRules rules = new LocationSelectionRules(
            LOCATIONS_PER_MATCH, MIN_PLAYABLE_LOCATIONS, EFFECTS);

    @Nested
    @DisplayName("Éxito")
    class Success {

        @Test
        @DisplayName("C.10-E1: selecciona tres ids distintos y al menos dos ubicaciones jugables")
        void selectsDistinctLocationsWithMinimumPlayable() {
            List<AvailableLocation> catalog = List.of(
                    available(1, 0, null),
                    available(2, 0, allTurnsRestricted()),
                    available(3, 2, null),
                    available(4, 3, null));

            List<AvailableLocation> selected = rules.select(catalog, 41L);

            assertThat(selected).hasSize(LOCATIONS_PER_MATCH)
                    .extracting(AvailableLocation::id).doesNotHaveDuplicates();
            assertThat(selected.stream().filter(LocationSelectionRulesTest::isPlayable).count())
                    .isGreaterThanOrEqualTo(MIN_PLAYABLE_LOCATIONS);
        }

        @Test
        @DisplayName("C.10-A1: ordena el catálogo por id antes del barajado y repite la selección con la misma semilla")
        void selectionIsDeterministicRegardlessOfRepositoryOrder() {
            List<AvailableLocation> catalog = List.of(
                    available(4, 3, null),
                    available(2, 0, allTurnsRestricted()),
                    available(1, 0, null),
                    available(3, 2, null));

            assertThat(rules.select(catalog, 73L))
                    .extracting(AvailableLocation::id)
                    .containsExactlyElementsOf(rules.select(catalog.reversed(), 73L)
                            .stream().map(AvailableLocation::id).toList());
        }
    }

    @Nested
    @DisplayName("Errores")
    class Errors {

        @Test
        @DisplayName("C.10-R1: dos ubicaciones no alcanzan la cantidad requerida")
        void insufficientLocations() {
            assertThatThrownBy(() -> rules.select(List.of(available(1, 2, null), available(2, 2, null)), 1L))
                    .isInstanceOf(LocationsUnavailableException.class)
                    .hasMessageContaining("No hay suficientes ubicaciones");
        }

        @Test
        @DisplayName("C.10-R1: una sola ubicación jugable no satisface el mínimo")
        void insufficientPlayableLocations() {
            List<AvailableLocation> catalog = List.of(
                    available(1, 0, null),
                    available(2, 0, allTurnsRestricted()),
                    available(3, 4, null));

            assertThatThrownBy(() -> rules.select(catalog, 1L))
                    .isInstanceOf(LocationsUnavailableException.class)
                    .hasMessageContaining("al menos " + MIN_PLAYABLE_LOCATIONS);
        }
    }

    private static AvailableLocation available(long id, int cardLimit, LocationEffect effect) {
        LocationFields fields = new LocationFields("Lugar " + id, "Descripción " + id, cardLimit,
                ExtraEnergy.none());
        Location location = Location.create(fields, effect, "/images/locations/" + id + "/large",
                "/images/locations/" + id + "/small", CREATED_AT);
        return new AvailableLocation(id, location);
    }

    private static LocationEffect allTurnsRestricted() {
        List<Integer> turns = IntStream.rangeClosed(TurnRestrictionEffect.FIRST_TURN, TurnRestrictionEffect.LAST_TURN)
                .boxed().toList();
        return new LocationEffect(TurnRestrictionEffect.CODE, Map.of("turns", turns));
    }

    private static boolean isPlayable(AvailableLocation available) {
        LocationEffect effect = available.location().effect();
        return available.location().fields().cardLimit() > 0
                && (effect == null || !EFFECTS.blocksAllTurns(effect));
    }
}
