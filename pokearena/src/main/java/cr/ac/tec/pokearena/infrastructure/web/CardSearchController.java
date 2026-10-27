package cr.ac.tec.pokearena.infrastructure.web;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import cr.ac.tec.pokearena.application.dto.CardSearchPage;
import cr.ac.tec.pokearena.application.dto.CardSearchRequest;
import cr.ac.tec.pokearena.application.usecase.SearchCardsUseCase;
import cr.ac.tec.pokearena.domain.model.CardSearchException;

@RestController
public class CardSearchController {
    private final SearchCardsUseCase search;

    public CardSearchController(SearchCardsUseCase search) { this.search = search; }

    @GetMapping("/api/v1/cards")
    public ResponseEntity<CardSearchPage> search(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestParam MultiValueMap<String, String> parameters) {
        // Impide que criterios repetidos se pierdan silenciosamente al convertir los parámetros.
        for (var entry : parameters.entrySet()) {
            if (entry.getValue().size() != 1) {
                throw CardSearchException.invalid(entry.getKey(), "El criterio no puede repetirse");
            }
        }
        var page = search.execute(new CardSearchRequest(parameters.toSingleValueMap()), authorization);
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(page);
    }
}
