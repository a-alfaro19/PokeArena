package cr.ac.tec.pokearena.infrastructure.persistence;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import cr.ac.tec.pokearena.domain.repository.CollectibleCardRepository;

/**
 * Propósito: implementación JDBC de {@link CollectibleCardRepository}: las cartas que un jugador puede
 * recibir en su colección inicial (tabla card).
 * Responsabilidades: contar las cartas disponibles y activas de una rareza y elegir al azar, en la base,
 * cartas distintas de esa rareza con una semilla, sin cargar el catálogo en memoria.
 * Referencia: sección 8.2, pasos 3 y 4, y Anexo A.5.
 */
@Repository
public class JdbcCollectibleCardRepository implements CollectibleCardRepository {

    /**
     * Selección al azar de la colección inicial (parámetros: rareza, semilla como texto y cantidad).
     * Es pública para que la prueba de rendimiento pida el plan de esta misma consulta.
     */
    public static final String PICK_COLLECTIBLE_SQL = """
            SELECT code FROM card
            WHERE rarity_code = ? AND available AND active
            ORDER BY md5(code || ?), code
            LIMIT ?
            """;

    private final JdbcTemplate jdbc;

    public JdbcCollectibleCardRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public int countCollectible(String rarityCode) {
        return jdbc.queryForObject("""
                SELECT count(*) FROM card WHERE rarity_code = ? AND available AND active
                """, Integer.class, rarityCode);
    }

    /**
     * Ordena las cartas candidatas por {@code md5(code || semilla)} y toma las primeras: es un orden
     * pseudoaleatorio que depende solo de la semilla y del código, así la misma semilla da la misma
     * selección y semillas distintas dan otra. Las cartas son distintas porque {@code code} es único.
     * La base calcula el md5 de cada candidata y conserva solo las {@code count} menores (Top-N heapsort):
     * nunca devuelve ni guarda en memoria el catálogo completo. El código desempata, por si acaso.
     */
    @Override
    public List<String> pickRandomCollectible(String rarityCode, long seed, int count) {
        return jdbc.queryForList(PICK_COLLECTIBLE_SQL, String.class, rarityCode, Long.toString(seed), count);
    }
}
