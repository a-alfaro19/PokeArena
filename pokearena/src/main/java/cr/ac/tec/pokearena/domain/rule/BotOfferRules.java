package cr.ac.tec.pokearena.domain.rule;

import java.time.Duration;
import java.time.Instant;

import cr.ac.tec.pokearena.domain.model.MatchSearch;
import cr.ac.tec.pokearena.domain.model.MatchSearchStatus;

/**
 * Determina si el jugador puede ver la oferta del bot al consultar su búsqueda.
 * El plazo viene de la configuración y la hora de consulta del caso de uso.
 * Referencia: sección 7.2.3, mensaje 10 de A.11, criterio C.9-E3.
 */
public final class BotOfferRules {

    private final Duration botOfferAfter;

    public BotOfferRules(Duration botOfferAfter) {
        this.botOfferAfter = botOfferAfter;
    }

    public boolean isAvailable(MatchSearch search, Instant now) {
        return search.status() == MatchSearchStatus.WAITING
                && !now.isBefore(search.createdAt().plus(botOfferAfter));
    }
}
