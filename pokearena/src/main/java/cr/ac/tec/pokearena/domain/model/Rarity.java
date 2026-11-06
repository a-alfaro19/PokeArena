package cr.ac.tec.pokearena.domain.model;

import java.util.Objects;

/**
 * Propósito: rareza de carta del catálogo fijo (Común, Poco común, Rara, Legendaria).
 * Responsabilidades: llevar el código y el nombre. Los valores vienen de la tabla rarity (semilla de V1).
 * Referencia: sección 6 y criterio C.1 ("rareza Común, Poco común, Rara o Legendaria").
 *
 * @param code código, por ejemplo {@code COMMON}
 * @param name nombre, por ejemplo {@code Común}
 */
public record Rarity(String code, String name) {

    public Rarity {
        Objects.requireNonNull(code, "El código de la rareza es obligatorio");
        Objects.requireNonNull(name, "El nombre de la rareza es obligatorio");
    }
}
