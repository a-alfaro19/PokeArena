package cr.ac.tec.pokearena.application.dto;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Propósito: solicitud de la búsqueda de cartas del administrador (historia 2.1.2).
 * Responsabilidades: llevar TODOS los parámetros de la consulta tal como llegaron, con sus
 * repeticiones, para que el caso de uso pueda rechazar un criterio desconocido o repetido con el error
 * tipado del dominio en lugar de un error genérico del enlace de parámetros.
 * Referencia: sección 7.3.
 *
 * @param parameters nombre de cada parámetro y sus valores ({@code limit} y {@code cursor} incluidos)
 */
public record SearchCardsCommand(Map<String, List<String>> parameters) {

    public SearchCardsCommand {
        parameters = parameters.entrySet().stream().collect(Collectors.toUnmodifiableMap(
                Map.Entry::getKey, entry -> List.copyOf(entry.getValue())));
    }
}
