package cr.ac.tec.pokearena.infrastructure.persistence;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import cr.ac.tec.pokearena.domain.model.Location;
import cr.ac.tec.pokearena.domain.model.LocationEffect;
import cr.ac.tec.pokearena.domain.repository.LocationRepository;
import tools.jackson.databind.json.JsonMapper;

/**
 * Propósito: implementación JDBC de {@link LocationRepository} (tabla location).
 * Responsabilidades: insertar una ubicación y devolver su identificador. Los parámetros del efecto se
 * guardan como JSON en la columna effect_parameters; sin efecto, como objeto vacío ({@code {}}), que
 * es lo que exige la restricción ck_location_no_effect_no_parameters de V1.
 * Referencia: sección 6 (tabla location) y sección 7.2, paso 10.
 */
@Repository
public class JdbcLocationRepository implements LocationRepository {

    private final JdbcTemplate jdbc;
    private final JsonMapper json;

    public JdbcLocationRepository(JdbcTemplate jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public long save(Location location) {
        Map<String, Object> parameters = location.optionalEffect().map(LocationEffect::parameters).orElse(Map.of());
        Long id = jdbc.queryForObject("""
                INSERT INTO location (name, description, effect_type, effect_parameters, card_limit,
                                      extra_energy_mode, extra_energy_amount, image_large_url, image_small_url,
                                      image_status, available, created_at)
                VALUES (?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING id
                """, Long.class,
                location.fields().name(), location.fields().description(),
                location.optionalEffect().map(LocationEffect::code).orElse(null),
                json.writeValueAsString(parameters), location.fields().cardLimit(),
                location.fields().extraEnergy().mode().name(), location.fields().extraEnergy().amount(),
                location.imageLargeUrl(), location.imageSmallUrl(), location.imageStatus().name(),
                location.available(), OffsetDateTime.ofInstant(location.createdAt(), ZoneOffset.UTC));
        return id;
    }
}
