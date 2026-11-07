package cr.ac.tec.pokearena.infrastructure.persistence;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import cr.ac.tec.pokearena.domain.model.NewPlayer;
import cr.ac.tec.pokearena.domain.model.Player;
import cr.ac.tec.pokearena.domain.model.PlayerAccount;
import cr.ac.tec.pokearena.domain.model.ProviderIdentity;
import cr.ac.tec.pokearena.domain.model.Role;
import cr.ac.tec.pokearena.domain.repository.PlayerRepository;

/**
 * Propósito: implementación JDBC de {@link PlayerRepository} (sección 6, tablas player y player_identity).
 * Responsabilidades: buscar el jugador de una identidad y crearlo si no existe, con una sola cuenta
 * por identidad aunque dos solicitudes lleguen a la vez.
 * Referencia: sección 8.1, paso 5 ("INSERT ... ON CONFLICT DO NOTHING y luego leer").
 *
 * <p>Cómo se garantiza una sola cuenta (debe ejecutarse dentro de un {@code UnitOfWork}):
 * <ol>
 *   <li>Si la identidad ya existe, se devuelve su jugador.</li>
 *   <li>Si no, se inserta un jugador nuevo y se intenta asociarle la identidad con
 *       {@code ON CONFLICT DO NOTHING}. La clave primaria de player_identity es
 *       (provider, provider_subject): si otra transacción la está insertando, PostgreSQL espera a
 *       que esa termine y entonces esta inserción no hace nada.</li>
 *   <li>Si la asociación no insertó nada, perdimos la carrera: se borra el jugador recién creado,
 *       que quedó sin identidad, y se lee el jugador de la transacción que ganó.</li>
 * </ol>
 * Solo la transacción cuya asociación insertó la fila informa {@code created = true}: así, de dos
 * primeros ingresos simultáneos, uno solo es el "primer ingreso".
 */
@Repository
public class JdbcPlayerRepository implements PlayerRepository {

    private static final String SELECT_BY_IDENTITY = """
            SELECT p.id, p.display_name, p.email, p.role
            FROM player_identity i
            JOIN player p ON p.id = i.player_id
            WHERE i.provider = ? AND i.provider_subject = ?
            """;

    private static final RowMapper<Player> PLAYER = (rs, row) -> new Player(
            rs.getObject("id", UUID.class),
            rs.getString("display_name"),
            rs.getString("email"),
            Role.valueOf(rs.getString("role")));

    private final JdbcTemplate jdbc;

    public JdbcPlayerRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Player> findByIdentity(ProviderIdentity identity) {
        return jdbc.query(SELECT_BY_IDENTITY, PLAYER, identity.provider(), identity.subject()).stream().findFirst();
    }

    @Override
    public PlayerAccount findOrCreate(ProviderIdentity identity, NewPlayer candidate) {
        // 1. La identidad ya tiene cuenta: se reutiliza, sin tocar sus datos.
        Optional<Player> existing = findByIdentity(identity);
        if (existing.isPresent()) {
            return new PlayerAccount(existing.get(), false);
        }

        // 2. Cuenta nueva y asociación de la identidad; la unicidad la decide la base.
        UUID newPlayerId = jdbc.queryForObject(
                "INSERT INTO player (display_name, email, role, created_at) VALUES (?, ?, ?, ?) RETURNING id",
                UUID.class, candidate.displayName(), candidate.email(), candidate.role().name(),
                OffsetDateTime.ofInstant(candidate.createdAt(), ZoneOffset.UTC));
        int linked = jdbc.update("""
                INSERT INTO player_identity (provider, provider_subject, player_id) VALUES (?, ?, ?)
                ON CONFLICT (provider, provider_subject) DO NOTHING
                """, identity.provider(), identity.subject(), newPlayerId);

        // 3. Otra transacción creó la cuenta primero: se descarta la nuestra.
        if (linked == 0) {
            jdbc.update("DELETE FROM player WHERE id = ?", newPlayerId);
        }

        // Lectura posterior: devuelve la cuenta que quedó, sea la nuestra o la de la otra transacción.
        Player player = findByIdentity(identity).orElseThrow(() -> new IllegalStateException(
                "La identidad " + identity + " no quedó asociada a ningún jugador"));
        return new PlayerAccount(player, linked == 1);
    }

    @Override
    public void updateRole(UUID playerId, Role role) {
        int updated = jdbc.update("UPDATE player SET role = ? WHERE id = ?", role.name(), playerId);
        if (updated == 0) {
            throw new IllegalStateException("No existe el jugador " + playerId + " para cambiar su rol");
        }
    }
}
