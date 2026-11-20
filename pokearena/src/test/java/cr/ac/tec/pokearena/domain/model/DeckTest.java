package cr.ac.tec.pokearena.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Propósito: pruebas unitarias del modelo de deck guardado ({@link Deck} y {@link StoredDeck}).
 * Responsabilidades: comprobar que el detalle y el resumen describen el mismo deck y que los códigos salen
 * de sus cartas. Java puro, sin Spring.
 * Referencia: sección 7.1 ("Listar y consultar"); Anexo A.8.
 */
@DisplayName("Modelo de deck guardado")
class DeckTest {

    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-11-19T12:00:00Z");

    private static Card card(int number) {
        CardFields fields = new CardFields("Carta " + number, "carta " + number, 1, 1, new CardType("FUE", "Fuego"),
                new Rarity("COMMON", "Común"));
        return Card.create(CardCode.of("FUE", number), fields, new Ability("HAB-000", "Sin Habilidad", null, null),
                "/images/l.png", "/images/s.png", NOW);
    }

    @Test
    @DisplayName("Los códigos del deck salen de sus cartas, en el mismo orden")
    void codesFromCards() {
        Deck deck = new Deck(new StoredDeck(7, PLAYER, "Fuego", 2, NOW), List.of(card(1), card(2)));

        assertThat(deck.cardCodes()).containsExactly("PKA-FUE-001", "PKA-FUE-002");
    }

    @Test
    @DisplayName("Un deck cuyo detalle no tiene la cantidad de cartas del resumen no se puede construir")
    void sizeMustMatch() {
        StoredDeck stored = new StoredDeck(7, PLAYER, "Fuego", 3, NOW);

        assertThatThrownBy(() -> new Deck(stored, List.of(card(1), card(2))))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
