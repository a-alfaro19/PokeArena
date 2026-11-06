package cr.ac.tec.pokearena.domain.rule;

import java.util.Arrays;
import java.util.List;

import cr.ac.tec.pokearena.domain.error.ValidationException;
import cr.ac.tec.pokearena.domain.model.ExtraEnergy;
import cr.ac.tec.pokearena.domain.model.LocationFields;

/**
 * Propósito: validación de los campos de una ubicación nueva (corrección 2 del profesor; paso 5 de 7.2).
 * Responsabilidades: comprobar nombre, descripción, límite de cartas y energía extra, en ese orden, y
 * lanzar el error tipado del primer campo inválido, con su {@code field} y sus {@code allowedValues}.
 * Referencia: sección 7.2, paso 5, y criterio C.2.
 *
 * <p>Energía extra: la modalidad es obligatoria y su monto depende de ella: {@code NONE} solo admite 0
 * (y un monto ausente se toma como 0), {@code FIXED} de 0 a 3 y {@code PER_CARD} de 0 a 2.
 */
public final class LocationFieldRules {

    // Nombres de los campos, iguales a los del formulario y del contrato de la API.
    public static final String NAME_FIELD = "name";
    public static final String DESCRIPTION_FIELD = "description";
    public static final String CARD_LIMIT_FIELD = "cardLimit";
    public static final String ENERGY_MODE_FIELD = "extraEnergyMode";
    public static final String ENERGY_AMOUNT_FIELD = "extraEnergyAmount";
    public static final String EFFECT_FIELD = "effect";
    public static final String IMAGE_FIELD = "image";

    /** Límite de cartas por jugador: de 0 a 5; 0 significa que no se pueden jugar cartas en ella (C.2). */
    public static final int MIN_CARD_LIMIT = 0;
    public static final int MAX_CARD_LIMIT = 5;

    private LocationFieldRules() {
    }

    /**
     * Valida los campos y los devuelve convertidos.
     *
     * @throws ValidationException MISSING_FIELD si falta un campo, INVALID_FIELD si su valor no es válido
     */
    public static LocationFields validate(String name, String description, String cardLimit, String energyMode,
            String energyAmount) {
        String cleanName = FieldChecks.requireText(NAME_FIELD, name, "Debe indicar el nombre de la ubicación.").strip();
        String cleanDescription = FieldChecks.requireText(DESCRIPTION_FIELD, description,
                "Debe indicar la descripción de la ubicación.").strip();
        int limit = FieldChecks.integerInRange(CARD_LIMIT_FIELD, "El límite de cartas", cardLimit, MIN_CARD_LIMIT,
                MAX_CARD_LIMIT);
        ExtraEnergy energy = extraEnergy(energyMode, energyAmount);
        return new LocationFields(cleanName, cleanDescription, limit, energy);
    }

    private static ExtraEnergy extraEnergy(String modeText, String amountText) {
        FieldChecks.requireText(ENERGY_MODE_FIELD, modeText,
                "Debe indicar la modalidad de energía extra: " + String.join(", ", modes()) + ".");
        ExtraEnergy.Mode mode = Arrays.stream(ExtraEnergy.Mode.values())
                .filter(candidate -> candidate.name().equals(modeText.strip()))
                .findFirst()
                .orElseThrow(() -> ValidationException.invalidField(ENERGY_MODE_FIELD,
                        "La modalidad de energía extra '" + modeText + "' no existe.", modes()));

        // Sin energía extra el monto es 0: puede omitirse; cualquier otro valor es inválido.
        if (mode == ExtraEnergy.Mode.NONE) {
            String noEnergy = String.valueOf(ExtraEnergy.MIN_AMOUNT);
            if (amountText == null || amountText.isBlank() || amountText.strip().equals(noEnergy)) {
                return ExtraEnergy.none();
            }
            throw ValidationException.invalidField(ENERGY_AMOUNT_FIELD,
                    "Sin energía extra (NONE) el monto debe ser " + noEnergy + ".", List.of(noEnergy));
        }
        int amount = FieldChecks.integerInRange(ENERGY_AMOUNT_FIELD,
                "El monto de energía extra (" + mode + ")", amountText, ExtraEnergy.MIN_AMOUNT, mode.maxAmount());
        return new ExtraEnergy(mode, amount);
    }

    private static List<String> modes() {
        return Arrays.stream(ExtraEnergy.Mode.values()).map(Enum::name).toList();
    }
}
