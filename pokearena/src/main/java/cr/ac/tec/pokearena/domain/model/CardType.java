package cr.ac.tec.pokearena.domain.model;

import java.util.Objects;

/**
 * Propósito: tipo de carta del catálogo fijo (Fuego, Agua, Planta, Eléctrico, Psíquico, Lucha).
 * Responsabilidades: llevar el código de 3 letras, que forma parte del código de la carta
 * ({@code PKA-<TIPO>-<NNN>}), y su nombre. Los valores vienen de la tabla card_type (semilla de V1).
 * Referencia: sección 6 y criterio C.1 ("tipo Fuego, Agua, Planta, Eléctrico, Psíquico o Lucha").
 *
 * @param code código, por ejemplo {@code FUE}
 * @param name nombre, por ejemplo {@code Fuego}
 */
public record CardType(String code, String name) {

    public CardType {
        Objects.requireNonNull(code, "El código del tipo es obligatorio");
        Objects.requireNonNull(name, "El nombre del tipo es obligatorio");
    }
}
