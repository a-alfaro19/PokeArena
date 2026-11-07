package cr.ac.tec.pokearena.infrastructure.persistence;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import cr.ac.tec.pokearena.domain.repository.PlayerCardRepository;

/**
 * Propósito: implementación JDBC de {@link PlayerCardRepository} (tabla player_card).
 * Responsabilidades: agregar una copia de cada carta a la colección de un jugador.
 * Referencia: sección 6 (tabla player_card) y sección 8.2, paso 5.
 *
 * <p>Las filas se insertan en un solo lote ({@code batchUpdate}): una ida a la base para las 15 cartas.
 * {@code ON CONFLICT} suma una copia si el jugador ya tenía la carta; en la colección inicial no ocurre
 * (el jugador no tiene cartas), pero así el método sirve tal cual para futuras entregas de cartas.
 */
@Repository
public class JdbcPlayerCardRepository implements PlayerCardRepository {

    // Copias que agrega cada llamada por carta.
    private static final int ONE_COPY = 1;

    private final JdbcTemplate jdbc;

    public JdbcPlayerCardRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void addOneCopyOfEach(UUID playerId, Collection<String> cardCodes) {
        List<Object[]> rows = new ArrayList<>();
        for (String code : cardCodes) {
            rows.add(new Object[] { playerId, code, ONE_COPY });
        }
        jdbc.batchUpdate("""
                INSERT INTO player_card (player_id, card_code, copies) VALUES (?, ?, ?)
                ON CONFLICT (player_id, card_code) DO UPDATE SET copies = player_card.copies + EXCLUDED.copies
                """, rows);
    }
}
