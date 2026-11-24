package cr.ac.tec.pokearena.infrastructure.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import cr.ac.tec.pokearena.application.port.Clock;
import cr.ac.tec.pokearena.application.port.UnitOfWork;
import cr.ac.tec.pokearena.application.usecase.CancelMatchSearchUseCase;
import cr.ac.tec.pokearena.application.usecase.GetMatchSearchUseCase;
import cr.ac.tec.pokearena.domain.repository.MatchSearchRepository;
import cr.ac.tec.pokearena.domain.rule.BotOfferRules;

/** Ensambla las reglas y los casos de uso de consulta y salida (secciones 7.2.3 y 7.2.4). */
@Configuration
public class MatchmakingConfiguration {

    @Bean
    public BotOfferRules botOfferRules(@Value("${app.matchmaking.bot-offer-after}") Duration after) {
        return new BotOfferRules(after);
    }

    @Bean
    public GetMatchSearchUseCase getMatchSearchUseCase(MatchSearchRepository searches, Clock clock,
            BotOfferRules rules, @Value("${app.matchmaking.client-poll-interval}") Duration interval) {
        return new GetMatchSearchUseCase(searches, clock, rules, interval);
    }

    @Bean
    public CancelMatchSearchUseCase cancelMatchSearchUseCase(MatchSearchRepository searches, Clock clock,
            UnitOfWork unitOfWork, @Value("${app.matchmaking.client-poll-interval}") Duration interval) {
        return new CancelMatchSearchUseCase(searches, clock, unitOfWork, interval);
    }
}
