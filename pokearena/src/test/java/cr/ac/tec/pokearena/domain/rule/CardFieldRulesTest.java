package cr.ac.tec.pokearena.domain.rule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import cr.ac.tec.pokearena.domain.error.ErrorType;
import cr.ac.tec.pokearena.domain.error.ValidationException;
import cr.ac.tec.pokearena.domain.model.Ability;
import cr.ac.tec.pokearena.domain.model.Card;
import cr.ac.tec.pokearena.domain.model.CardCode;
import cr.ac.tec.pokearena.domain.model.CardFields;
import cr.ac.tec.pokearena.domain.model.CardType;
import cr.ac.tec.pokearena.domain.model.ImageStatus;
import cr.ac.tec.pokearena.domain.model.Rarity;

/**
 * Propósito: pruebas unitarias de las reglas de una carta nueva (corrección 5; criterio C.1).
 * Responsabilidades: comprobar la validación de cada campo con su error tipado, el código
 * PKA-TIPO-NNN (mínimo 3 dígitos, sin máximo), el nombre normalizado y los invariantes de la carta.
 * Referencia: sección 7.1 (paso 5 y "Código único").
 */
@DisplayName("Reglas de una carta nueva")
class CardFieldRulesTest {

    private static final List<CardType> TYPES = List.of(new CardType("AGU", "Agua"), new CardType("FUE", "Fuego"));
    private static final List<Rarity> RARITIES = List.of(new Rarity("COMMON", "Común"), new Rarity("RARE", "Rara"));

    private static CardFields valid(String cost, String power) {
        return CardFieldRules.validate("Pikachu", cost, power, "FUE", "COMMON", TYPES, RARITIES);
    }

    @Test
    @DisplayName("Campos válidos se devuelven convertidos, con el nombre sin espacios y normalizado")
    void validFields() {
        CardFields fields = CardFieldRules.validate("  Pikachú Rojo ", "3", "-5", "FUE", "RARE", TYPES, RARITIES);

        assertThat(fields.name()).isEqualTo("Pikachú Rojo");
        assertThat(fields.nameNormalized()).isEqualTo("pikachu rojo");
        assertThat(fields.cost()).isEqualTo(3);
        assertThat(fields.power()).isEqualTo(-5);
        assertThat(fields.type().code()).isEqualTo("FUE");
        assertThat(fields.rarity().code()).isEqualTo("RARE");
    }

    @Test
    @DisplayName("Los extremos permitidos se aceptan: costo 0 y 10, poder -20 y 20")
    void boundariesAreAccepted() {
        assertThat(valid("0", "-20").cost()).isZero();
        assertThat(valid("10", "20").power()).isEqualTo(20);
    }

    @ParameterizedTest(name = "costo {0}, poder {1}: campo {2}")
    @CsvSource({ "11, 0, cost, 0..10", "-1, 0, cost, 0..10", "abc, 0, cost, 0..10", "3.5, 0, cost, 0..10",
            "0, 21, power, -20..20", "0, -21, power, -20..20", "0, x, power, -20..20" })
    @DisplayName("Un costo o poder fuera de rango o no entero es INVALID_FIELD con el campo y su rango")
    void outOfRangeNumbers(String cost, String power, String field, String allowed) {
        assertThatThrownBy(() -> valid(cost, power)).isInstanceOfSatisfying(ValidationException.class, error -> {
            assertThat(error.type()).isEqualTo(ErrorType.INVALID_FIELD);
            assertThat(error.field()).contains(field);
            assertThat(error.allowedValues()).containsExactly(allowed);
        });
    }

    @Test
    @DisplayName("Un tipo o una rareza inexistentes son INVALID_FIELD con los valores del catálogo")
    void unknownCatalogValues() {
        assertThatThrownBy(() -> CardFieldRules.validate("Pikachu", "3", "0", "XXX", "COMMON", TYPES, RARITIES))
                .isInstanceOfSatisfying(ValidationException.class, error -> {
                    assertThat(error.field()).contains("type");
                    assertThat(error.allowedValues()).containsExactly("AGU", "FUE");
                });
        assertThatThrownBy(() -> CardFieldRules.validate("Pikachu", "3", "0", "FUE", "EPIC", TYPES, RARITIES))
                .isInstanceOfSatisfying(ValidationException.class, error -> {
                    assertThat(error.field()).contains("rarity");
                    assertThat(error.allowedValues()).containsExactly("COMMON", "RARE");
                });
    }

    @ParameterizedTest(name = "falta {0}")
    @CsvSource(nullValues = "NULO", value = { "name, NULO, 3, 0, FUE, COMMON", "name, '  ', 3, 0, FUE, COMMON",
            "cost, Pikachu, NULO, 0, FUE, COMMON", "power, Pikachu, 3, NULO, FUE, COMMON",
            "type, Pikachu, 3, 0, NULO, COMMON", "rarity, Pikachu, 3, 0, FUE, NULO" })
    @DisplayName("Un campo ausente o vacío es MISSING_FIELD con su nombre")
    void missingFields(String field, String name, String cost, String power, String type, String rarity) {
        assertThatThrownBy(() -> CardFieldRules.validate(name, cost, power, type, rarity, TYPES, RARITIES))
                .isInstanceOfSatisfying(ValidationException.class, error -> {
                    assertThat(error.type()).isEqualTo(ErrorType.MISSING_FIELD);
                    assertThat(error.field()).contains(field);
                });
    }

    @Test
    @DisplayName("Se valida en el orden nombre, costo, poder, tipo y rareza: se informa el primero que falla")
    void validationOrder() {
        assertThatThrownBy(() -> CardFieldRules.validate("Pikachu", "99", "99", "XXX", "EPIC", TYPES, RARITIES))
                .isInstanceOfSatisfying(ValidationException.class, error -> assertThat(error.field()).contains("cost"));
    }

    @Test
    @DisplayName("El código es PKA-<TIPO>-<NNN> con 3 dígitos y ceros a la izquierda")
    void cardCodeFormat() {
        assertThat(CardCode.of("FUE", 1).value()).isEqualTo("PKA-FUE-001");
        assertThat(CardCode.of("AGU", 42).value()).isEqualTo("PKA-AGU-042");
        assertThat(CardCode.of("LUC", 999).value()).isEqualTo("PKA-LUC-999");
    }

    @Test
    @DisplayName("El consecutivo tiene mínimo 3 dígitos y no tiene máximo: después de 999 sigue 1000")
    void cardCodeHasNoMaximum() {
        assertThat(CardCode.of("FUE", 1000).value()).isEqualTo("PKA-FUE-1000");
        assertThat(CardCode.of("FUE", 10_000).value()).isEqualTo("PKA-FUE-10000");
    }

    @Test
    @DisplayName("Un consecutivo menor que 1 es un error de programación")
    void cardCodeStartsAtOne() {
        assertThatThrownBy(() -> CardCode.of("FUE", 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("El nombre normalizado queda en minúsculas y sin tildes")
    void nameNormalization() {
        assertThat(NameNormalizer.normalize("Ñandú ÉLÉCTRICO Ü")).isEqualTo("nandu electrico u");
    }

    @Test
    @DisplayName("Una carta nueva con miniatura queda READY y disponible; sin miniatura, PENDING_RETRY y no disponible")
    void availabilityDependsOnThumbnail() {
        CardFields fields = valid("3", "0");
        Ability ability = new Ability("HAB-000", "Sin Habilidad", null, null);

        Card ready = Card.create(CardCode.of("FUE", 1), fields, ability, "/images/l.png", "/images/s.png", Instant.EPOCH);
        Card pending = Card.create(CardCode.of("FUE", 2), fields, ability, "/images/l.png", null, Instant.EPOCH);

        assertThat(ready.imageStatus()).isEqualTo(ImageStatus.READY);
        assertThat(ready.available()).isTrue();
        assertThat(pending.imageStatus()).isEqualTo(ImageStatus.PENDING_RETRY);
        assertThat(pending.available()).isFalse();
    }

    @Test
    @DisplayName("Una carta no puede estar disponible sin sus dos imágenes (invariante)")
    void availableRequiresReady() {
        CardFields fields = valid("3", "0");
        Ability ability = new Ability("HAB-000", "Sin Habilidad", null, null);

        assertThatThrownBy(() -> new Card(CardCode.of("FUE", 1), fields, ability, "/images/l.png", null,
                ImageStatus.PENDING_RETRY, true, Instant.EPOCH)).isInstanceOf(IllegalStateException.class);
    }
}
