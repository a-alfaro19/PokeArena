package cr.ac.tec.pokearena.infrastructure.persistence;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import cr.ac.tec.pokearena.domain.model.CardType;
import cr.ac.tec.pokearena.domain.model.Rarity;
import cr.ac.tec.pokearena.domain.repository.CardCatalogRepository;

/**
 * Propósito: implementación JDBC de los catálogos de tipos y rarezas (tablas card_type y rarity).
 * Responsabilidades: leer los valores sembrados en V1, ordenados por código para que los valores
 * permitidos de los errores salgan siempre en el mismo orden.
 * Referencia: sección 6.
 */
@Repository
public class JdbcCardCatalogRepository implements CardCatalogRepository {

    private final JdbcTemplate jdbc;

    public JdbcCardCatalogRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<CardType> findAllTypes() {
        return jdbc.query("SELECT code, name FROM card_type ORDER BY code",
                (rs, row) -> new CardType(rs.getString("code"), rs.getString("name")));
    }

    @Override
    public List<Rarity> findAllRarities() {
        return jdbc.query("SELECT code, name FROM rarity ORDER BY code",
                (rs, row) -> new Rarity(rs.getString("code"), rs.getString("name")));
    }
}
