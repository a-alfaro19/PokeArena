package cr.ac.tec.pokearena.domain.model;

import java.util.Objects;
import java.util.Optional;

/**
 * Propósito: habilidad que se asigna a una carta (sección 6, tabla ability).
 * Responsabilidades: identificar la habilidad y decir si está limitada a un tipo de carta y si es
 * de evolución (hacia qué carta). Lo que hace la habilidad en el motor del juego queda para la
 * historia del motor; aquí solo importa para crear y mostrar cartas.
 * Referencia: sección 6 y criterios de C.1 sobre habilidades y evoluciones.
 *
 * @param code              código, por ejemplo {@code HAB-000}
 * @param name              nombre, por ejemplo {@code Sin Habilidad}
 * @param limitedToType     código del tipo al que está limitada, o {@code null}
 * @param evolvesToCardCode código de la carta a la que evoluciona, o {@code null}
 */
public record Ability(String code, String name, String limitedToType, String evolvesToCardCode) {

    /** Habilidad sembrada desde el inicio, sin efecto (criterio C.1). */
    public static final String NO_ABILITY_CODE = "HAB-000";

    public Ability {
        Objects.requireNonNull(code, "El código de la habilidad es obligatorio");
        Objects.requireNonNull(name, "El nombre de la habilidad es obligatorio");
    }

    /** Tipo al que está limitada, si lo está. */
    public Optional<String> limitedType() {
        return Optional.ofNullable(limitedToType);
    }

    /** Carta a la que evoluciona, si es una habilidad de evolución. */
    public Optional<String> evolutionTarget() {
        return Optional.ofNullable(evolvesToCardCode);
    }
}
