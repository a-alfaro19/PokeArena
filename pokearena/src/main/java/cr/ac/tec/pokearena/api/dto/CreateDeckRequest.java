package cr.ac.tec.pokearena.api.dto;

import java.util.List;

/**
 * Propósito: cuerpo HTTP de {@code POST /api/v1/decks} (esquema {@code CreateDeckRequest}).
 * Responsabilidades: recibir el nombre y los códigos tal como los envía el cliente. Ambos pueden faltar:
 * los valida el dominio ({@code DeckRules}) con sus errores tipados, no el enlace de Spring.
 * Referencia: sección 7.1 ("{ name, cards }").
 *
 * @param name  nombre del deck
 * @param cards códigos de las cartas de mi colección
 */
public record CreateDeckRequest(String name, List<String> cards) {
}
