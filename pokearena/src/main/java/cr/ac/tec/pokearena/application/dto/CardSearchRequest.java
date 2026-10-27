package cr.ac.tec.pokearena.application.dto;

import java.util.Map;

// Separa los parámetros de entrada del modelo de carta existente.
public record CardSearchRequest(Map<String, String> parameters) {
    public CardSearchRequest {
        parameters = Map.copyOf(parameters);
    }
}
