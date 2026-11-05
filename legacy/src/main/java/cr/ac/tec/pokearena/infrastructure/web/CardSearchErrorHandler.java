package cr.ac.tec.pokearena.infrastructure.web;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import cr.ac.tec.pokearena.domain.model.CardSearchException;

// Limita la traducción de errores a Buscar Carta para conservar los flujos existentes.
@RestControllerAdvice(assignableTypes = CardSearchController.class)
public class CardSearchErrorHandler {
    @ExceptionHandler(CardSearchException.class)
    public ResponseEntity<SearchError> handle(CardSearchException exception) {
        int status = switch (exception.reason()) {
            case INVALID -> 400;
            case UNAUTHENTICATED -> 401;
            case FORBIDDEN -> 403;
            case EXPIRED -> 410;
            case UNAVAILABLE -> 503;
        };
        var response = ResponseEntity.status(status).header(HttpHeaders.CACHE_CONTROL, "no-store");
        if (status == 401) { response.header(HttpHeaders.WWW_AUTHENTICATE, "Bearer"); }
        if (status == 503) { response.header(HttpHeaders.RETRY_AFTER, "1"); }
        return response.body(new SearchError(exception.reason().name(), exception.criterion(), exception.getMessage()));
    }

    public record SearchError(String code, String criterion, String message) { }
}
