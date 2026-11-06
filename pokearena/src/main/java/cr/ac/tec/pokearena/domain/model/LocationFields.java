package cr.ac.tec.pokearena.domain.model;

import java.util.Objects;

/**
 * Propósito: campos de una ubicación nueva que ya pasaron la validación (paso 5 de la sección 7.2).
 * Responsabilidades: llevar los valores ya convertidos y comprobados hasta la creación de la
 * ubicación, dentro de la transacción. Solo los crea {@code LocationFieldRules}.
 * Referencia: sección 7.2, paso 5.
 *
 * @param name        nombre, sin espacios al inicio ni al final
 * @param description descripción, sin espacios al inicio ni al final
 * @param cardLimit   cartas por jugador, de 0 a 5 (0: no se pueden jugar cartas en ella)
 * @param extraEnergy energía extra por turno
 */
public record LocationFields(String name, String description, int cardLimit, ExtraEnergy extraEnergy) {

    public LocationFields {
        Objects.requireNonNull(name, "El nombre es obligatorio");
        Objects.requireNonNull(description, "La descripción es obligatoria");
        Objects.requireNonNull(extraEnergy, "La energía extra es obligatoria");
    }
}
