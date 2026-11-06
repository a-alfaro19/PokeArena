package cr.ac.tec.pokearena.domain.rule;

import java.util.List;

import cr.ac.tec.pokearena.domain.error.ValidationException;
import cr.ac.tec.pokearena.domain.model.CardFields;
import cr.ac.tec.pokearena.domain.model.CardType;
import cr.ac.tec.pokearena.domain.model.Rarity;

/**
 * Propósito: validación de los campos de una carta nueva (corrección 5 del profesor; paso 5 de 7.1).
 * Responsabilidades: comprobar nombre, costo, poder, tipo y rareza, en ese orden, y lanzar el error
 * tipado del primer campo inválido, con su {@code field} y, cuando aplica, sus {@code allowedValues}.
 * Referencia: sección 7.1, paso 5, y criterio C.1 ("valores permitidos").
 *
 * <p>Recibe los valores como texto, tal como llegan del formulario, para que un número mal escrito
 * ("abc") tenga el mismo error y los mismos valores permitidos que un número fuera de rango.
 * Los catálogos de tipos y rarezas se reciben de afuera: vienen de la base, no están fijos aquí.
 */
public final class CardFieldRules {

    // Nombres de los campos, iguales a los del formulario y del contrato de la API.
    public static final String NAME_FIELD = "name";
    public static final String COST_FIELD = "cost";
    public static final String POWER_FIELD = "power";
    public static final String TYPE_FIELD = "type";
    public static final String RARITY_FIELD = "rarity";
    public static final String ABILITY_FIELD = "ability";
    public static final String IMAGE_FIELD = "image";

    /** Costo permitido: de 0 a 10 (criterio C.1). */
    public static final int MIN_COST = 0;
    public static final int MAX_COST = 10;

    /** Poder permitido: de -20 a 20; una carta puede restar poder a su ubicación (criterio C.1). */
    public static final int MIN_POWER = -20;
    public static final int MAX_POWER = 20;

    private CardFieldRules() {
    }

    /**
     * Valida los campos y los devuelve convertidos.
     *
     * @throws ValidationException MISSING_FIELD si falta un campo, INVALID_FIELD si su valor no es válido
     */
    public static CardFields validate(String name, String cost, String power, String typeCode, String rarityCode,
            List<CardType> types, List<Rarity> rarities) {
        String cleanName = FieldChecks.requireText(NAME_FIELD, name, "Debe indicar el nombre de la carta.").strip();
        int validCost = FieldChecks.integerInRange(COST_FIELD, "El costo", cost, MIN_COST, MAX_COST);
        int validPower = FieldChecks.integerInRange(POWER_FIELD, "El poder", power, MIN_POWER, MAX_POWER);
        CardType type = typeOf(typeCode, types);
        Rarity rarity = rarityOf(rarityCode, rarities);
        return new CardFields(cleanName, NameNormalizer.normalize(cleanName), validCost, validPower, type, rarity);
    }

    private static CardType typeOf(String code, List<CardType> types) {
        FieldChecks.requireText(TYPE_FIELD, code, "Debe elegir el tipo de la carta.");
        return types.stream().filter(type -> type.code().equals(code)).findFirst()
                .orElseThrow(() -> ValidationException.invalidField(TYPE_FIELD,
                        "El tipo '" + code + "' no existe.", types.stream().map(CardType::code).toList()));
    }

    private static Rarity rarityOf(String code, List<Rarity> rarities) {
        FieldChecks.requireText(RARITY_FIELD, code, "Debe elegir la rareza de la carta.");
        return rarities.stream().filter(rarity -> rarity.code().equals(code)).findFirst()
                .orElseThrow(() -> ValidationException.invalidField(RARITY_FIELD,
                        "La rareza '" + code + "' no existe.", rarities.stream().map(Rarity::code).toList()));
    }
}
