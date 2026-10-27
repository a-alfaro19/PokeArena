package cr.ac.tec.pokearena.application.dto;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// Conserva repeticiones para validarlas en aplicación después de comprobar permisos.
public record CardSearchRequest(Map<String, List<String>> parameters) {
    public CardSearchRequest {
        parameters = parameters.entrySet().stream().collect(Collectors.toUnmodifiableMap(
                Map.Entry::getKey, entry -> List.copyOf(entry.getValue())));
    }
}
