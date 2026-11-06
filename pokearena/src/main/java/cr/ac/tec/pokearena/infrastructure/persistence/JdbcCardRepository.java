package cr.ac.tec.pokearena.infrastructure.persistence;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import cr.ac.tec.pokearena.domain.model.Card;
import cr.ac.tec.pokearena.domain.repository.CardRepository;

/**
 * Propósito: implementación JDBC de {@link CardRepository} (tablas card y card_type_counter).
 * Responsabilidades: entregar el consecutivo de cada tipo de forma atómica, guardar cartas y leer el
 * destino de evolución de la habilidad de una carta.
 * Referencia: sección 7.1 ("Código único" y paso 10).
 *
 * <p>El consecutivo usa una sola sentencia {@code UPDATE ... RETURNING}: PostgreSQL bloquea la fila
 * del tipo hasta que la transacción termina, así dos transacciones del mismo tipo nunca reciben el
 * mismo número; y si la transacción se revierte, el incremento también se revierte (no se consume).
 * Por eso esta operación exige una transacción abierta.
 */
@Repository
public class JdbcCardRepository implements CardRepository {

    private final JdbcTemplate jdbc;

    public JdbcCardRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public int nextNumber(String typeCode) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException(
                    "El consecutivo se obtiene dentro del UnitOfWork que guarda la carta (sección 7.1)");
        }
        return jdbc.query("""
                UPDATE card_type_counter SET last_number = last_number + 1
                WHERE type_code = ?
                RETURNING last_number
                """, (rs, row) -> rs.getInt("last_number"), typeCode).stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("No hay consecutivo para el tipo " + typeCode));
    }

    @Override
    public void save(Card card) {
        jdbc.update("""
                INSERT INTO card (code, name, name_normalized, cost, power, type_code, rarity_code, ability_code,
                                  image_large_url, image_small_url, image_status, available, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                card.code().value(), card.fields().name(), card.fields().nameNormalized(), card.fields().cost(),
                card.fields().power(), card.fields().type().code(), card.fields().rarity().code(),
                card.ability().code(), card.imageLargeUrl(), card.imageSmallUrl(), card.imageStatus().name(),
                card.available(), OffsetDateTime.ofInstant(card.createdAt(), ZoneOffset.UTC));
    }

    @Override
    public Optional<String> evolutionTargetOf(String cardCode) {
        return jdbc.query("""
                SELECT a.evolves_to_card_code
                FROM card c
                JOIN ability a ON a.code = c.ability_code
                WHERE c.code = ? AND a.evolves_to_card_code IS NOT NULL
                """, (rs, row) -> rs.getString(1), cardCode).stream().findFirst();
    }
}
