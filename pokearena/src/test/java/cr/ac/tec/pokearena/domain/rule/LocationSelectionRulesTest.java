package cr.ac.tec.pokearena.domain.rule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
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
 * Propósito: verificar la selección determinista de ubicaciones para una partida.
 * Responsabilidades: cubrir cantidad, unicidad, mínimo de ubicaciones jugables y error por catálogo insuficiente.
 * Referencia: sección 7.3, paso 3; criterios C.10-E1 y C.10-R1.
 */
@DisplayName("Selección de ubicaciones para partida")
class LocationSelectionRulesTest {

    private final LocationSelectionRules rules = new LocationSelectionRules(3, 2,
            new LocationEffectRegistry(List.of(new TurnRestrictionEffect())));

    @Test
    @DisplayName("Selecciona tres ubicaciones distintas y garantiza al menos dos jugables")
    void selectionContainsRequiredPlayableLocations() {
        List<AvailableLocation> selected = rules.select(List.of(
                location(1, 0, null),
                location(2, 3, restriction(1, 2, 3, 4, 5, 6)),
                location(3, 2, null),
                location(4, 1, restriction(1, 6)),
                location(5, 3, null)), 222L);

        assertThat(selected).hasSize(3);
        assertThat(selected.stream().map(AvailableLocation::id).distinct()).hasSize(3);
        assertThat(selected.stream().filter(LocationSelectionRulesTest::isPlayable)).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("La selección es reproducible y no depende del orden de entrada")
    void selectionIsDeterministic() {
        List<AvailableLocation> catalog = List.of(
                location(1, 0, null),
                location(2, 3, restriction(1, 2, 3, 4, 5, 6)),
                location(3, 2, null),
                location(4, 1, restriction(1, 6)),
                location(5, 3, null));

        assertThat(rules.select(catalog, 222L))
                .containsExactlyElementsOf(rules.select(List.of(catalog.get(4), catalog.get(2), catalog.get(0),
                        catalog.get(3), catalog.get(1)), 222L));
    }

    @Test
    @DisplayName("Falla si no existen tres ubicaciones o si hay menos de dos jugables")
    void insufficientCatalogThrows() {
        assertThatThrownBy(() -> rules.select(List.of(location(1, 1, null), location(2, 1, null)), 10L))
                .isInstanceOf(LocationsUnavailableException.class);
        assertThatThrownBy(() -> rules.select(List.of(
                location(1, 0, null),
                location(2, 2, restriction(1, 2, 3, 4, 5, 6)),
                location(3, 3, null)), 10L))
                .isInstanceOf(LocationsUnavailableException.class);
        assertThatThrownBy(() -> rules.select(List.of(
                location(1, 1, null),
                location(1, 1, null),
                location(2, 1, null)), 10L))
                .isInstanceOf(LocationsUnavailableException.class);
    }

    private static boolean isPlayable(AvailableLocation location) {
        return location.location().fields().cardLimit() > 0
                && (location.location().effect() == null
                        || !location.location().effect().code().equals(TurnRestrictionEffect.CODE)
                        || ((List<?>) location.location().effect().parameters().get("turns")).size()
                                < TurnRestrictionEffect.LAST_TURN);
    }

    private static LocationEffect restriction(Integer... turns) {
        return new LocationEffect(TurnRestrictionEffect.CODE, Map.of("turns", List.of(turns)));
    }

    private static AvailableLocation location(long id, int cardLimit, LocationEffect effect) {
        LocationFields fields = new LocationFields("Location " + id, "Description " + id, cardLimit,
                ExtraEnergy.none());
        return new AvailableLocation(id, Location.create(fields, effect, "large", "small", Instant.EPOCH));
    }
}
