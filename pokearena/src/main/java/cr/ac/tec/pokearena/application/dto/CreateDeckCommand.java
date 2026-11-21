package cr.ac.tec.pokearena.application.dto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Propósito: datos de la solicitud Crear deck tal como llegaron (historia 3.1.3).
 * Responsabilidades: llevar al caso de uso el jugador de la sesión y el nombre y los códigos sin validar:
 * los valida {@code DeckRules}, con sus errores tipados.
 * Referencia: sección 7.1; Anexo A.7, mensaje 11 ({@code execute(IdempotentRequest, CreateDeckCommand)}).
 *
 * @param playerId jugador autenticado (de la sesión, nunca del cuerpo)
 * @param name     nombre tal como llegó; puede ser {@code null}
 * @param cards    códigos tal como llegaron; puede ser {@code null}
 */
public record CreateDeckCommand(UUID playerId, String name, List<String> cards) {

    public CreateDeckCommand {
        Objects.requireNonNull(playerId, "El jugador es obligatorio");
        // Copia que admite null: un elemento nulo lo informa DeckRules con su error tipado.
        cards = cards == null ? null : Collections.unmodifiableList(new ArrayList<>(cards));
    }
}
