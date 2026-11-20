package cr.ac.tec.pokearena.infrastructure.persistence;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import cr.ac.tec.pokearena.domain.model.OwnedCard;
import cr.ac.tec.pokearena.domain.repository.PlayerCardRepository;

/**
 * Propósito: implementación JDBC de {@link PlayerCardRepository} (tabla player_card).
 * Responsabilidades: agregar una copia de cada carta a la colección de un jugador, y leer cuáles de unos
 * códigos posee, con la ficha de cada carta.
 * Referencia: sección 6 (tabla player_card) y sección 8.2, paso 5; sección 7.1, paso 4, del Sprint 3.
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
    private final CardRows cardRows;

    public JdbcPlayerCardRepository(JdbcTemplate jdbc, CardRows cardRows) {
        this.jdbc = jdbc;
        this.cardRows = cardRows;
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

    /**
     * Una sola consulta, guiada por la clave {@code pk_player_card (player_id, card_code)}: con 10.000 cartas
     * en el catálogo lee solo las filas de esos códigos del jugador, nunca el catálogo completo. La ficha de
     * cada carta usa las mismas columnas y el mismo mapeo ({@link CardRows}) que el catálogo.
     */
    @Override
    public List<OwnedCard> findOwnedCards(UUID playerId, Collection<String> cardCodes) {
        if (cardCodes.isEmpty()) {
            return List.of();
        }
        return jdbc.query(ownedCardsSql(cardCodes.size()),
                (rs, row) -> new OwnedCard(cardRows.map(rs), rs.getInt("copies")),
                ownedCardsArguments(playerId, cardCodes));
    }

    /**
     * Sentencia de {@link #findOwnedCards}. Solo se genera la cantidad de placeholders: jugador y códigos van
     * siempre como parámetros. Pública solo para que las pruebas pidan el plan ({@code EXPLAIN}) de la misma
     * consulta que usa la aplicación, como {@link CardSearchSql}.
     */
    public static String ownedCardsSql(int codeCount) {
        String placeholders = String.join(",", Collections.nCopies(codeCount, "?"));
        return "SELECT pc.copies, " + CardSearchSql.COLUMNS + CardSearchSql.FROM
                + "JOIN player_card pc ON pc.card_code = c.code\n"
                + "WHERE pc.player_id = ? AND pc.card_code IN (" + placeholders + ")\n"
                + "ORDER BY c.code";
    }

    /** Parámetros de {@link #ownedCardsSql}, en orden: el jugador y los códigos. */
    public static Object[] ownedCardsArguments(UUID playerId, Collection<String> cardCodes) {
        List<Object> arguments = new ArrayList<>();
        arguments.add(playerId);
        arguments.addAll(cardCodes);
        return arguments.toArray();
    }
}
