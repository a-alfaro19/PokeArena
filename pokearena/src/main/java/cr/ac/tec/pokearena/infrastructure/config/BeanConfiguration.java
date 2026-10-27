package cr.ac.tec.pokearena.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import cr.ac.tec.pokearena.application.usecase.CreateCardUseCase;
import cr.ac.tec.pokearena.domain.repository.CardRepository;

@Configuration
public class BeanConfiguration {
    @Bean
    public CreateCardUseCase createCardUseCase(CardRepository cardRepository) {
        return new CreateCardUseCase(cardRepository);
    }
}
