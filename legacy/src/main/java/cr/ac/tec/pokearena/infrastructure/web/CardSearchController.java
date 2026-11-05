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

@RestController
public class CardSearchController {
    private final SearchCardsUseCase search;

    public CardSearchController(SearchCardsUseCase search) { this.search = search; }

    @GetMapping("/api/v1/cards")
    public ResponseEntity<CardSearchPage> search(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestParam MultiValueMap<String, String> parameters) {
        // Entrega los valores completos para autorizar antes de validar repeticiones.
        var page = search.execute(new CardSearchRequest(parameters), authorization);
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(page);
    }
}
