package cr.ac.tec.pokearena.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import cr.ac.tec.pokearena.application.port.Clock;
import cr.ac.tec.pokearena.application.port.UnitOfWork;
import cr.ac.tec.pokearena.application.usecase.CreateDeckUseCase;
import cr.ac.tec.pokearena.application.usecase.GetDeckUseCase;
import cr.ac.tec.pokearena.application.usecase.IdempotencyGuard;
import cr.ac.tec.pokearena.application.usecase.ListMyDecksUseCase;
import cr.ac.tec.pokearena.application.usecase.Pagination;
import cr.ac.tec.pokearena.domain.repository.DeckRepository;
import cr.ac.tec.pokearena.domain.repository.PlayerCardRepository;

/**
 * Propósito: ensamblar los casos de uso de la historia 3.1.3 "Crear Deck".
 * Responsabilidades: construir con {@code new} {@link CreateDeckUseCase}, {@link ListMyDecksUseCase} y
 * {@link GetDeckUseCase}, que no llevan anotaciones de Spring (sección 4.3), con los mismos beans que usan
 * las demás historias: idempotencia, paginación, unidad de trabajo y reloj.
 * Referencia: Anexo D.4 del Sprint 3 ({@code DeckConfiguration}).
 */
@Configuration
public class DeckConfiguration {

    @Bean
    public CreateDeckUseCase createDeckUseCase(IdempotencyGuard idempotency, PlayerCardRepository playerCards,
            DeckRepository decks, UnitOfWork unitOfWork, Clock clock) {
        return new CreateDeckUseCase(idempotency, playerCards, decks, unitOfWork, clock);
    }

    @Bean
    public ListMyDecksUseCase listMyDecksUseCase(Pagination pagination, DeckRepository decks) {
        return new ListMyDecksUseCase(pagination, decks);
    }

    @Bean
    public GetDeckUseCase getDeckUseCase(DeckRepository decks) {
        return new GetDeckUseCase(decks);
    }
}
