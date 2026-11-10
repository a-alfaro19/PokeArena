package cr.ac.tec.pokearena.application.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Propósito: entrada de Explorar catálogo (8.3).
 * Responsabilidades: llevar el jugador de la sesión verificada y preservar filtros repetidos
 * para informar errores tipados; playerId nunca se enlaza a un parámetro HTTP.
 */
public record ExploreCatalogCommand(UUID playerId, Map<String, List<String>> parameters) {

    public ExploreCatalogCommand {
        // La solicitud queda inmutable mientras se validan y ejecutan sus filtros.
        parameters = parameters.entrySet().stream().collect(Collectors.toUnmodifiableMap(
                Map.Entry::getKey, entry -> List.copyOf(entry.getValue())));
    }
}
