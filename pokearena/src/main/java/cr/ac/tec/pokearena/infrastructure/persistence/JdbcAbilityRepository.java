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
    private final AbilityRows abilityRows;

    public JdbcAbilityRepository(JdbcTemplate jdbc, AbilityRows abilityRows) {
        this.jdbc = jdbc;
        this.abilityRows = abilityRows;
    }

    @Override
    public Optional<Ability> findActiveByCode(String code) {
        return jdbc.query("SELECT " + AbilityRows.COLUMNS + " FROM ability a WHERE a.code = ? AND a.active",
                (rs, row) -> abilityRows.map(rs), code).stream().findFirst();
    }
}
