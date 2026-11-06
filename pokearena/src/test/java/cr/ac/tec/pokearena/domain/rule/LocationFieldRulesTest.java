package cr.ac.tec.pokearena.domain.rule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import cr.ac.tec.pokearena.domain.error.ErrorType;
import cr.ac.tec.pokearena.domain.error.ValidationException;
import cr.ac.tec.pokearena.domain.model.ExtraEnergy;
import cr.ac.tec.pokearena.domain.model.ImageStatus;
import cr.ac.tec.pokearena.domain.model.Location;
import cr.ac.tec.pokearena.domain.model.LocationFields;

/**
 * Propósito: pruebas unitarias de las reglas de una ubicación nueva (corrección 2; criterio C.2).
 * Responsabilidades: comprobar la validación de cada campo con su error tipado, la energía extra según
 * su modalidad y los invariantes de la ubicación.
 * Referencia: sección 7.2, paso 5.
 */
@DisplayName("Reglas de una ubicación nueva")
class LocationFieldRulesTest {

    private static LocationFields validate(String cardLimit, String mode, String amount) {
        return LocationFieldRules.validate("Volcán", "Un volcán activo", cardLimit, mode, amount);
    }

    @Test
    @DisplayName("Campos válidos se devuelven convertidos, con nombre y descripción sin espacios")
    void validFields() {
        LocationFields fields = LocationFieldRules.validate("  Volcán ", " Lava ", "3", "FIXED", "2");

        assertThat(fields.name()).isEqualTo("Volcán");
        assertThat(fields.description()).isEqualTo("Lava");
        assertThat(fields.cardLimit()).isEqualTo(3);
        assertThat(fields.extraEnergy()).isEqualTo(new ExtraEnergy(ExtraEnergy.Mode.FIXED, 2));
    }

    @ParameterizedTest(name = "límite {0}, {1} {2}")
    @CsvSource(nullValues = "NULO", value = { "0, NONE, NULO", "5, NONE, 0", "2, FIXED, 0", "2, FIXED, 3",
            "2, PER_CARD, 0", "2, PER_CARD, 2" })
    @DisplayName("Los extremos permitidos se aceptan: límite 0 y 5, energía fija 0 a 3 y por cartas 0 a 2")
    void boundariesAreAccepted(String limit, String mode, String amount) {
        LocationFields fields = validate(limit, mode, amount);

        assertThat(fields.extraEnergy().mode().name()).isEqualTo(mode);
    }

    @Test
    @DisplayName("Sin energía extra (NONE) el monto puede omitirse y vale 0")
    void noneWithoutAmountIsZero() {
        assertThat(validate("1", "NONE", null).extraEnergy()).isEqualTo(ExtraEnergy.none());
    }

    @ParameterizedTest(name = "{0}: límite {1}, {2} {3}")
    @CsvSource(nullValues = "NULO", value = { "cardLimit, 6, NONE, NULO, 0..5", "cardLimit, -1, NONE, NULO, 0..5",
            "cardLimit, abc, NONE, NULO, 0..5", "extraEnergyMode, 1, DOUBLE, 1, NONE",
            "extraEnergyAmount, 1, FIXED, 4, 0..3", "extraEnergyAmount, 1, PER_CARD, 3, 0..2",
            "extraEnergyAmount, 1, NONE, 1, 0", "extraEnergyAmount, 1, FIXED, -1, 0..3" })
    @DisplayName("Un límite fuera de 0 a 5, una modalidad inexistente o un monto fuera de su rango son INVALID_FIELD con el campo")
    void invalidValues(String field, String limit, String mode, String amount, String allowed) {
        assertThatThrownBy(() -> validate(limit, mode, amount)).isInstanceOfSatisfying(ValidationException.class,
                error -> {
                    assertThat(error.type()).isEqualTo(ErrorType.INVALID_FIELD);
                    assertThat(error.field()).contains(field);
                    assertThat(error.allowedValues()).contains(allowed);
                });
    }

    @ParameterizedTest(name = "falta {0}")
    @CsvSource(nullValues = "NULO", value = { "name, NULO, Lava, 1, NONE, NULO",
            "description, Volcán, '  ', 1, NONE, NULO", "cardLimit, Volcán, Lava, NULO, NONE, NULO",
            "extraEnergyMode, Volcán, Lava, 1, NULO, NULO", "extraEnergyAmount, Volcán, Lava, 1, FIXED, NULO" })
    @DisplayName("Un campo ausente o vacío es MISSING_FIELD con su nombre (con FIXED o PER_CARD, el monto es obligatorio)")
    void missingFields(String field, String name, String description, String limit, String mode, String amount) {
        assertThatThrownBy(() -> LocationFieldRules.validate(name, description, limit, mode, amount))
                .isInstanceOfSatisfying(ValidationException.class, error -> {
                    assertThat(error.type()).isEqualTo(ErrorType.MISSING_FIELD);
                    assertThat(error.field()).contains(field);
                });
    }

    @Test
    @DisplayName("Una ubicación nueva con miniatura queda READY y disponible; sin miniatura, PENDING_RETRY y no disponible")
    void availabilityDependsOnThumbnail() {
        LocationFields fields = validate("2", "NONE", null);

        Location ready = Location.create(fields, null, "/images/l.png", "/images/s.png", Instant.EPOCH);
        Location pending = Location.create(fields, null, "/images/l.png", null, Instant.EPOCH);

        assertThat(ready.imageStatus()).isEqualTo(ImageStatus.READY);
        assertThat(ready.available()).isTrue();
        assertThat(pending.imageStatus()).isEqualTo(ImageStatus.PENDING_RETRY);
        assertThat(pending.available()).isFalse();
    }

    @Test
    @DisplayName("Una ubicación no puede estar disponible sin sus dos imágenes, ni la energía salirse de su modalidad (invariantes)")
    void invariants() {
        LocationFields fields = validate("2", "NONE", null);

        assertThatThrownBy(() -> new Location(fields, null, "/images/l.png", null, ImageStatus.PENDING_RETRY, true,
                Instant.EPOCH)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new ExtraEnergy(ExtraEnergy.Mode.PER_CARD, 3))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
