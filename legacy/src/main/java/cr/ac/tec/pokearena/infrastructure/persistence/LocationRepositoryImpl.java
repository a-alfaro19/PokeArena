package cr.ac.tec.pokearena.infrastructure.persistence;

import java.sql.PreparedStatement;
import java.util.Objects;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import cr.ac.tec.pokearena.domain.model.Location;
import cr.ac.tec.pokearena.domain.repository.LocationRepository;

@Repository
public class LocationRepositoryImpl implements LocationRepository {
    private final JdbcTemplate jdbcTemplate;

    public LocationRepositoryImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Location save(Location location) {
        String sql = "INSERT INTO locations (name, description) VALUES (?, ?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, new String[] { "id" });
            ps.setString(1, location.getName());
            ps.setString(2, location.getDescription());
            return ps;
        }, keyHolder);

        Long id = Objects.requireNonNull(keyHolder.getKey()).longValue();
        return new Location(id, location.getName(), location.getDescription());
    }
}
