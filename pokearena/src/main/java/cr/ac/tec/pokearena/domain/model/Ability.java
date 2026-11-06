package cr.ac.tec.pokearena.domain.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Propósito: habilidad que se asigna a una carta (sección 6, tabla ability).
 * Responsabilidades: identificar la habilidad, decir si está limitada a un tipo de carta y si es de
 * evolución (hacia qué carta), y llevar su disparador, su efecto y sus parámetros para mostrarlos.
 * Lo que hace la habilidad en el motor del juego queda para la historia del motor; aquí solo importa
 * para crear, buscar y mostrar cartas.
 * Referencia: sección 6, criterios de C.1 sobre habilidades y criterio C.3 ("habilidad con sus
 * parámetros"; búsqueda "por disparador y efecto").
 *
 * @param code              código, por ejemplo {@code HAB-000}
 * @param name              nombre, por ejemplo {@code Sin Habilidad}
 * @param trigger           disparador, o {@code null} si no tiene (como {@code HAB-000})
 * @param effectType        tipo de efecto, o {@code null} si no tiene
 * @param parameters        parámetros del efecto (columna jsonb); vacío si no tiene
 * @param limitedToType     código del tipo al que está limitada, o {@code null}
 * @param evolvesToCardCode código de la carta a la que evoluciona, o {@code null}
 */
public record Ability(String code, String name, String trigger, String effectType, Map<String, Object> parameters,
        String limitedToType, String evolvesToCardCode) {

    /** Habilidad sembrada desde el inicio, sin efecto (criterio C.1). */
    public static final String NO_ABILITY_CODE = "HAB-000";

    public Ability {
        Objects.requireNonNull(code, "El código de la habilidad es obligatorio");
        Objects.requireNonNull(name, "El nombre de la habilidad es obligatorio");
        // Copia inmutable que conserva el orden; admite valores null, que un JSON puede traer.
        parameters = parameters == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(parameters));
    }

    /**
     * Habilidad sin disparador, sin efecto y sin parámetros, como {@code HAB-000}. Es la forma que
     * necesitan Crear carta y sus pruebas, donde solo importan el tipo limitado y la evolución.
     */
    public Ability(String code, String name, String limitedToType, String evolvesToCardCode) {
        this(code, name, null, null, Map.of(), limitedToType, evolvesToCardCode);
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
