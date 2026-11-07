package cr.ac.tec.pokearena.support;

import org.springframework.jdbc.core.JdbcTemplate;

import cr.ac.tec.pokearena.domain.rule.NameNormalizer;

/**
 * Propósito: catálogo conocido para las pruebas de integración de la colección inicial.
 * Responsabilidades: vaciar el catálogo y sembrar cartas con la rareza, disponibilidad y estado que pide
 * cada prueba, con códigos válidos y distintos.
 * Referencia: sección 8.2 (la colección inicial elige Comunes disponibles y activas).
 *
 * <p>{@link #reset()} borra TODAS las cartas, como las demás pruebas del catálogo: por eso las pruebas de
 * integración corren en secuencia (sección 13). Antes hay que borrar los jugadores con cartas
 * ({@link TestPlayers#delete}), porque {@code player_card} apunta a {@code card}.
 */
public final class TestCards {

    private static final String[] TYPES = { "FUE", "AGU", "PLA", "ELE", "PSI", "LUC" };

    private final JdbcTemplate jdbc;
    private int nextNumber;

    public TestCards(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Vacía el catálogo y reinicia los consecutivos. */
    public void reset() {
        jdbc.update("DELETE FROM card");
        jdbc.update("UPDATE card_type_counter SET last_number = 0");
    }

    /** Siembra {@code count} cartas Comunes disponibles y activas. */
    public void commons(int count) {
        for (int i = 0; i < count; i++) {
            card("COMMON", true, true);
        }
    }

    /** Siembra una carta con la rareza, disponibilidad y estado dados. */
    public void card(String rarity, boolean available, boolean active) {
        nextNumber++;
        String type = TYPES[nextNumber % TYPES.length];
        String code = "PKA-%s-%03d".formatted(type, nextNumber);
        String name = "Carta de prueba " + nextNumber;
        jdbc.update("""
                INSERT INTO card (code, name, name_normalized, cost, power, type_code, rarity_code, ability_code,
                                  image_large_url, image_small_url, image_status, available, active, created_at)
                VALUES (?, ?, ?, 1, 1, ?, ?, 'HAB-000', ?, ?, ?, ?, ?, now())
                """, code, name, NameNormalizer.normalize(name), type, rarity, "/images/cards/" + code + "/large.png",
                available ? "/images/cards/" + code + "/small.png" : null, available ? "READY" : "PENDING_RETRY",
                available, active);
    }
}
