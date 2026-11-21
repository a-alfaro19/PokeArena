package cr.ac.tec.pokearena.application.dto;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Propósito: solicitud de la lista de "Mis decks" (historia 3.1.3).
 * Responsabilidades: llevar el jugador de la sesión y los parámetros de paginación tal como llegaron; los
 * valida {@code Pagination}, la misma de la búsqueda y el catálogo.
 * Referencia: sección 7.1 y 5.5; Anexo A.8, mensaje 5 ({@code execute(ListMyDecksCommand)}).
 *
 * @param playerId   jugador autenticado
 * @param pagination {@code limit} y {@code cursor} tal como llegaron (solo los que vinieron)
 */
public record ListMyDecksCommand(UUID playerId, Map<String, List<String>> pagination) {

    public ListMyDecksCommand {
        Objects.requireNonNull(playerId, "El jugador es obligatorio");
        pagination = Map.copyOf(pagination);
    }
}
