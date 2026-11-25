package cr.ac.tec.pokearena.infrastructure.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import cr.ac.tec.pokearena.application.port.Clock;
import cr.ac.tec.pokearena.application.port.RandomSource;
import cr.ac.tec.pokearena.application.port.StartMatch;
import cr.ac.tec.pokearena.application.port.UnitOfWork;
import cr.ac.tec.pokearena.application.usecase.AcceptBotMatchUseCase;
import cr.ac.tec.pokearena.application.usecase.BotDeckSelector;
import cr.ac.tec.pokearena.application.usecase.CancelMatchSearchUseCase;
import cr.ac.tec.pokearena.application.usecase.GetMatchSearchUseCase;
import cr.ac.tec.pokearena.application.usecase.IdempotencyGuard;
import cr.ac.tec.pokearena.application.usecase.StartMatchSearchUseCase;
import cr.ac.tec.pokearena.domain.repository.ActiveMatchLookup;
import cr.ac.tec.pokearena.domain.repository.CollectibleCardRepository;
import cr.ac.tec.pokearena.domain.repository.DeckRepository;
import cr.ac.tec.pokearena.domain.repository.MatchSearchRepository;
import cr.ac.tec.pokearena.domain.repository.PlayerCardRepository;
import cr.ac.tec.pokearena.domain.repository.PlayerRepository;
import cr.ac.tec.pokearena.domain.rule.BotOfferRules;
import cr.ac.tec.pokearena.domain.rule.MatchSearchRules;

/** Ensambla las reglas y los casos de uso de las búsquedas de partida (sección 7.2). */
@Configuration
public class MatchmakingConfiguration {

    @Bean
    public StartMatchSearchUseCase startMatchSearchUseCase(IdempotencyGuard keys, DeckRepository decks,
            PlayerCardRepository cards, MatchSearchRepository searches, ActiveMatchLookup matches,
            PlayerRepository players, BotOfferRules botOffer, UnitOfWork unitOfWork, Clock clock,
            @Value("${app.match.max-bet}") long maxBet,
            @Value("${app.matchmaking.client-poll-interval}") Duration interval) {
        // Compartir el intervalo y la regla del bot mantiene alineados el alta y la consulta posterior.
        return new StartMatchSearchUseCase(keys, decks, cards, searches, matches, players, new MatchSearchRules(maxBet),
                botOffer, unitOfWork, clock, interval);
    }

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

    @Bean
    public BotDeckSelector botDeckSelector(CollectibleCardRepository cards, RandomSource random) {
        return new BotDeckSelector(cards, random);
    }

    /** StartMatch se resuelve al preparar la partida, cuando la implementación de A.14 debe estar instalada. */
    @Bean
    public AcceptBotMatchUseCase acceptBotMatchUseCase(MatchSearchRepository searches, DeckRepository decks,
            BotOfferRules rules, BotDeckSelector bots, @Lazy StartMatch start, IdempotencyGuard keys, UnitOfWork uow,
            Clock clock, @Value("${app.matchmaking.inactivity-timeout}") Duration inactivity,
            @Value("${app.matchmaking.client-poll-interval}") Duration interval) {
        return new AcceptBotMatchUseCase(searches, decks, rules, bots, start, keys, uow, clock, inactivity, interval);
    }
}
