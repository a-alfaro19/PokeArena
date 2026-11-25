package cr.ac.tec.pokearena.infrastructure.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Map;

import org.springframework.stereotype.Component;

import cr.ac.tec.pokearena.domain.model.AvailableLocation;
import cr.ac.tec.pokearena.domain.model.ExtraEnergy;
import cr.ac.tec.pokearena.domain.model.ImageStatus;
import cr.ac.tec.pokearena.domain.model.Location;
import cr.ac.tec.pokearena.domain.model.LocationEffect;
import cr.ac.tec.pokearena.domain.model.LocationFields;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Propósito: convertir una fila de la tabla location en una ubicación disponible del dominio.
 * Responsabilidades: compartir las columnas y el mapeo entre el catálogo y la lectura de partidas.
 * Referencia: secciones 6 y 7.3.
 */
@Component
public class LocationRows {

    public static final String COLUMNS = """
            l.id AS location_id, l.name AS location_name, l.description AS location_description,
            l.effect_type AS location_effect_type, l.effect_parameters::text AS location_effect_parameters,
            l.card_limit AS location_card_limit, l.extra_energy_mode AS location_extra_energy_mode,
            l.extra_energy_amount AS location_extra_energy_amount, l.image_large_url AS location_image_large_url,
            l.image_small_url AS location_image_small_url, l.image_status AS location_image_status,
            l.available AS location_available, l.created_at AS location_created_at
            """;

    private static final TypeReference<Map<String, Object>> JSON_OBJECT = new TypeReference<>() {
    };

    private final JsonMapper json;

    public LocationRows(JsonMapper json) {
        this.json = json;
    }

    /** Mapea una ubicación persistida, sin inferir su disponibilidad actual. */
    public Location mapLocation(ResultSet rs) throws SQLException {
        String effectType = rs.getString("location_effect_type");
        LocationEffect effect = effectType == null
                ? null
                : new LocationEffect(effectType,
                        json.readValue(rs.getString("location_effect_parameters"), JSON_OBJECT));
        LocationFields fields = new LocationFields(rs.getString("location_name"),
                rs.getString("location_description"), rs.getInt("location_card_limit"),
                new ExtraEnergy(ExtraEnergy.Mode.valueOf(rs.getString("location_extra_energy_mode")),
                        rs.getInt("location_extra_energy_amount")));
        Location location = new Location(fields, effect, rs.getString("location_image_large_url"),
                rs.getString("location_image_small_url"),
                ImageStatus.valueOf(rs.getString("location_image_status")),
                rs.getBoolean("location_available"),
                rs.getObject("location_created_at", OffsetDateTime.class).toInstant());
        return location;
    }

    /** Mapea una fila de la consulta de ubicaciones disponibles y activas. */
    public AvailableLocation mapAvailableLocation(ResultSet rs) throws SQLException {
        return new AvailableLocation(rs.getLong("location_id"), mapLocation(rs));
    }
}
