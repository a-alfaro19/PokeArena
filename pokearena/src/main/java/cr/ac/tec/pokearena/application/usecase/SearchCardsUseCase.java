package cr.ac.tec.pokearena.application.usecase;

import java.util.HashMap;
import java.util.UUID;
import cr.ac.tec.pokearena.application.dto.CardSearchPage;
import cr.ac.tec.pokearena.application.dto.CardSearchRequest;
import cr.ac.tec.pokearena.application.port.CardSearchQuery;
import cr.ac.tec.pokearena.application.port.SearchIdentity;
import cr.ac.tec.pokearena.domain.model.CardSearchCriteria;
import cr.ac.tec.pokearena.domain.model.CardSearchException;
import cr.ac.tec.pokearena.domain.model.CardSearchException.Reason;

public class SearchCardsUseCase {
    private final CardSearchQuery query;
    private final SearchIdentity identity;

    public SearchCardsUseCase(CardSearchQuery query, SearchIdentity identity) {
        this.query = query;
        this.identity = identity;
    }

    public CardSearchPage execute(CardSearchRequest request, String authorization) {
        // La autorización antecede a cualquier validación o lectura del catálogo.
        var user = identity.authenticate(authorization).orElseThrow(() ->
                new CardSearchException(Reason.UNAUTHENTICATED, "authorization", "Credenciales inválidas"));
        if (!user.administrator()) {
            throw new CardSearchException(Reason.FORBIDDEN, "authorization", "Se requiere rol ADMIN");
        }

        // Los controles del recorrido se validan aparte de los filtros del catálogo.
        var filters = new HashMap<>(request.parameters());
        String sizeValue = filters.remove("pageSize");
        String cursorValue = filters.remove("cursor");
        int pageSize = 50;
        if (sizeValue != null) {
            try { pageSize = Integer.parseInt(sizeValue); }
            catch (NumberFormatException exception) {
                throw CardSearchException.invalid("pageSize", "Debe ser un entero entre 1 y 100");
            }
        }
        if (pageSize < 1 || pageSize > 100) {
            throw CardSearchException.invalid("pageSize", "Debe estar entre 1 y 100");
        }
        UUID cursor = null;
        if (cursorValue != null) {
            try {
                cursor = UUID.fromString(cursorValue);
                if (!cursor.toString().equals(cursorValue)) { throw new IllegalArgumentException(); }
            } catch (IllegalArgumentException exception) {
                throw CardSearchException.invalid("cursor", "Debe ser un UUID canónico");
            }
        }
        return query.search(new CardSearchCriteria(filters), pageSize, cursor, user.subject());
    }
}
