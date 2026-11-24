package cr.ac.tec.pokearena.application.usecase;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import cr.ac.tec.pokearena.application.dto.GetMatchSearchCommand;
import cr.ac.tec.pokearena.application.dto.MatchSearchDetails;
import cr.ac.tec.pokearena.application.port.Clock;
import cr.ac.tec.pokearena.domain.error.MatchSearchException;
import cr.ac.tec.pokearena.domain.model.MatchSearch;
import cr.ac.tec.pokearena.domain.repository.MatchSearchRepository;
import cr.ac.tec.pokearena.domain.rule.BotOfferRules;

/** Consulta el estado de una búsqueda propia y calcula la oferta a la misma hora (US 47, A.11). */
public final class GetMatchSearchUseCase {

    private final MatchSearchRepository searches;
    private final Clock clock;
    private final BotOfferRules botRules;
    private final long pollIntervalSeconds;

    public GetMatchSearchUseCase(MatchSearchRepository searches, Clock clock, BotOfferRules botRules,
            Duration pollInterval) {
        this.searches = searches;
        this.clock = clock;
        this.botRules = botRules;
        this.pollIntervalSeconds = MatchSearchDetails.pollSeconds(pollInterval);
    }

    public MatchSearchDetails execute(GetMatchSearchCommand command) {
        // Mensajes 6 a 9 y 14 a 16 de A.11: la consulta atómica comprueba la titularidad.
        Instant now = clock.now();
        UUID id = MatchSearchIds.parse(command.searchId());
        MatchSearch search = searches.touchAndFind(id, command.playerId(), now)
                .orElseThrow(MatchSearchException::notFound);
        // Mensajes 10 a 12: evaluar el estado devuelto por la base, incluida una posible expiración.
        return MatchSearchDetails.from(search, botRules.isAvailable(search, now), pollIntervalSeconds);
    }
}
