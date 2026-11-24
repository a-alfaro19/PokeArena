package cr.ac.tec.pokearena.infrastructure.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Map;

import org.springframework.stereotype.Component;

import cr.ac.tec.pokearena.domain.effect.LocationEffectRegistry;
import cr.ac.tec.pokearena.domain.model.ExtraEnergy;
import cr.ac.tec.pokearena.domain.model.ImageStatus;
import cr.ac.tec.pokearena.domain.model.Location;
import cr.ac.tec.pokearena.domain.model.LocationFields;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Propósito: convertir una fila del catálogo de ubicaciones en su modelo de dominio.
 * Responsabilidades: compartir el mapeo de campos y la lectura del efecto entre la selección para partidas y
 * la consulta de partidas existentes.
 * Referencia: secciones 6 y 7.3.
 */
@Component
public class LocationRows {

    static final String COLUMNS = """
            l.id AS location_id, l.name AS location_name, l.description AS location_description,
            l.effect_type AS location_effect_type, l.effect_parameters::text AS location_effect_parameters,
            l.card_limit AS location_card_limit, l.extra_energy_mode AS location_extra_energy_mode,
            l.extra_energy_amount AS location_extra_energy_amount, l.image_large_url AS location_image_large_url,
            l.image_small_url AS location_image_small_url, l.image_status AS location_image_status,
            l.available AS location_available, l.created_at AS location_created_at
            """;

    private static final TypeReference<Map<String, Object>> PARAMETERS = new TypeReference<>() {
    };

    private final JsonMapper json;
    private final LocationEffectRegistry effects;

    public LocationRows(JsonMapper json, LocationEffectRegistry effects) {
        this.json = json;
        this.effects = effects;
    }

    public Location map(ResultSet rs) throws SQLException {
        String parameters = rs.getString("location_effect_parameters");
        Map<String, Object> effectParameters = parameters == null ? Map.of() : json.readValue(parameters, PARAMETERS);
        return new Location(
                new LocationFields(rs.getString("location_name"), rs.getString("location_description"),
                        rs.getInt("location_card_limit"),
                        new ExtraEnergy(ExtraEnergy.Mode.valueOf(rs.getString("location_extra_energy_mode")),
                                rs.getInt("location_extra_energy_amount"))),
                effects.validate(rs.getString("location_effect_type"), effectParameters).orElse(null),
                rs.getString("location_image_large_url"), rs.getString("location_image_small_url"),
                ImageStatus.valueOf(rs.getString("location_image_status")),
                rs.getBoolean("location_available"),
                rs.getObject("location_created_at", OffsetDateTime.class).toInstant());
    }
}
