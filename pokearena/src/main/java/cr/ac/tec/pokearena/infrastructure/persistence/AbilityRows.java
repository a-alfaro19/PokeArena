package cr.ac.tec.pokearena.infrastructure.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

import org.springframework.stereotype.Component;

import cr.ac.tec.pokearena.domain.model.Ability;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Propósito: convertir una fila de la tabla ability en {@link Ability}.
 * Responsabilidades: definir las columnas (con alias fijos) que leen todas las consultas que devuelven
 * una habilidad, y traducir la columna jsonb {@code parameters} a un mapa. Así Crear carta (7.1) y
 * Buscar carta (7.3) muestran la habilidad igual.
 * Referencia: sección 6 (tabla ability) y criterio C.3 ("habilidad con sus parámetros").
 *
 * <p>Usa el mismo {@code JsonMapper} de Spring Boot que {@link JacksonEffectReader}. El jsonb se lee
 * como texto ({@code parameters::text}) para no depender del tipo de objeto del controlador JDBC.
 */
@Component
public class AbilityRows {

    /** Columnas de la habilidad, con alias, para el SELECT de una consulta que une {@code ability a}. */
    public static final String COLUMNS = """
            a.code AS ability_code, a.name AS ability_name, a.trigger AS ability_trigger,
            a.effect_type AS ability_effect_type, a.parameters::text AS ability_parameters,
            a.limited_to_type AS ability_limited_to_type, a.evolves_to_card_code AS ability_evolves_to""";

    private static final TypeReference<Map<String, Object>> OBJECT = new TypeReference<>() {
    };

    private final JsonMapper json;

    public AbilityRows(JsonMapper json) {
        this.json = json;
    }

    /** La habilidad de la fila actual; la fila debe traer las {@link #COLUMNS}. */
    public Ability map(ResultSet rs) throws SQLException {
        String parameters = rs.getString("ability_parameters");
        return new Ability(rs.getString("ability_code"), rs.getString("ability_name"),
                rs.getString("ability_trigger"), rs.getString("ability_effect_type"),
                parameters == null ? Map.of() : json.readValue(parameters, OBJECT),
                rs.getString("ability_limited_to_type"), rs.getString("ability_evolves_to"));
    }
}
