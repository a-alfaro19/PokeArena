package cr.ac.tec.pokearena.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Propósito: deck validado que todavía no se ha guardado (historia 3.1.3).
 * Responsabilidades: llevar lo que {@code DeckRepository.save} necesita: dueño, nombre limpio y normalizado,
 * los códigos de sus cartas y la hora de creación.
 * Referencia: sección 7.1 (pasos 3 y 6); Anexo A.7, mensajes 14 y 19.
 *
 * <p>Solo lo crea {@code DeckRules.validateShape}, después de comprobar la forma: así un {@code NewDeck}
 * siempre tiene un nombre válido y la cantidad exacta de cartas distintas. La posesión de las cartas se
 * comprueba aparte ({@code DeckRules.checkOwnership}, mensaje 17), porque necesita consultar la colección.
 *
 * @param playerId       jugador dueño del deck
 * @param name           nombre sin espacios al inicio ni al final, tal como se mostrará
 * @param nameNormalized nombre en minúsculas y sin tildes ({@code NameNormalizer}); es único por jugador
 * @param cardCodes      códigos de las cartas, distintos y ordenados (el orden de envío no importa)
 * @param createdAt      hora de creación, que entrega el caso de uso desde el puerto {@code Clock}
 */
public record NewDeck(UUID playerId, String name, String nameNormalized, List<String> cardCodes,
        Instant createdAt) {

    public NewDeck {
        Objects.requireNonNull(playerId, "El jugador es obligatorio");
        Objects.requireNonNull(name, "El nombre es obligatorio");
        Objects.requireNonNull(nameNormalized, "El nombre normalizado es obligatorio");
        Objects.requireNonNull(createdAt, "La hora de creación es obligatoria");
        cardCodes = List.copyOf(cardCodes);
    }
}
