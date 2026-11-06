package cr.ac.tec.pokearena.infrastructure.persistence;

import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import cr.ac.tec.pokearena.domain.model.Ability;
import cr.ac.tec.pokearena.domain.repository.AbilityRepository;

/**
 * Propósito: implementación JDBC de la lectura de habilidades (tabla ability).
 * Responsabilidades: encontrar una habilidad activa por su código. Una habilidad desactivada no se
 * puede asignar a cartas nuevas: para el caso de uso es como si no existiera.
 * Referencia: sección 6 y sección 7.1, paso 6.
 */
@Repository
public class JdbcAbilityRepository implements AbilityRepository {

    private final JdbcTemplate jdbc;

    public JdbcAbilityRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Ability> findActiveByCode(String code) {
        return jdbc.query("""
                SELECT code, name, limited_to_type, evolves_to_card_code
                FROM ability
                WHERE code = ? AND active
                """,
                (rs, row) -> new Ability(rs.getString("code"), rs.getString("name"),
                        rs.getString("limited_to_type"), rs.getString("evolves_to_card_code")),
                code).stream().findFirst();
    }
}
