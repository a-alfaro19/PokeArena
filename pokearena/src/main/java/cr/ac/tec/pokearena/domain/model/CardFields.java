package cr.ac.tec.pokearena.domain.model;

import java.util.Objects;

/**
 * Propósito: campos de una carta nueva que ya pasaron la validación (paso 5 de la sección 7.1).
 * Responsabilidades: llevar los valores ya convertidos y comprobados hasta la creación de la carta,
 * que ocurre dentro de la transacción. Solo los crea {@code CardFieldRules}.
 * Referencia: sección 7.1, paso 5.
 *
 * @param name           nombre, sin espacios al inicio ni al final
 * @param nameNormalized nombre en minúsculas y sin tildes
 * @param cost           costo, de 0 a 10
 * @param power          poder, de -20 a 20
 * @param type           tipo del catálogo
 * @param rarity         rareza del catálogo
 */
public record CardFields(String name, String nameNormalized, int cost, int power, CardType type, Rarity rarity) {

    public CardFields {
        Objects.requireNonNull(name, "El nombre es obligatorio");
        Objects.requireNonNull(nameNormalized, "El nombre normalizado es obligatorio");
        Objects.requireNonNull(type, "El tipo es obligatorio");
        Objects.requireNonNull(rarity, "La rareza es obligatoria");
    }
}
