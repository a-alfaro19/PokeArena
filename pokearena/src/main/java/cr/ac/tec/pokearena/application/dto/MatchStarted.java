package cr.ac.tec.pokearena.application.dto;

import java.util.Objects;
import java.util.UUID;

/**
 * Propósito: confirmar que una partida quedó guardada.
 * Responsabilidades: devolver a quien empareja únicamente el id de la partida.
 * Referencia: sección 7.3, paso 7; Anexo A.14, mensaje 23.
 *
 * @param id identificador de la partida creada
 */
public record MatchStarted(UUID id) {

    public MatchStarted {
        Objects.requireNonNull(id, "El id de la partida es obligatorio");
    }
}
