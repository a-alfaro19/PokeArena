package cr.ac.tec.pokearena.infrastructure.config;

import java.security.SecureRandom;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import cr.ac.tec.pokearena.application.port.Clock;
import cr.ac.tec.pokearena.application.port.RandomSource;
import cr.ac.tec.pokearena.application.port.UnitOfWork;
import cr.ac.tec.pokearena.application.usecase.DeliverInitialCollectionUseCase;
import cr.ac.tec.pokearena.domain.repository.CollectibleCardRepository;
import cr.ac.tec.pokearena.domain.repository.CubeRepository;
import cr.ac.tec.pokearena.domain.repository.PlayerCardRepository;
import cr.ac.tec.pokearena.domain.repository.PlayerRepository;
import cr.ac.tec.pokearena.domain.rule.InitialCollectionRules;

/**
 * Propósito: ensamblar la entrega de la colección inicial (historia 3.1.1).
 * Responsabilidades: leer {@code app.collection.*}, crear la fuente de aleatoriedad real y construir
 * {@link DeliverInitialCollectionUseCase}, que no lleva anotaciones de Spring (sección 4.3).
 * Referencia: sección 8.2 y sección 4.2 (regla 5: la aleatoriedad entra por un puerto).
 *
 * <p>Este caso de uso todavía NO está conectado al inicio de sesión: el login sigue usando
 * {@code ProvisionalInitialCollectionDelivery} (ver {@code IdentityConfiguration}). La conexión es una
 * tarea posterior.
 */
@Configuration
public class CollectionConfiguration {

    /**
     * Semillas con {@link SecureRandom}: no predecibles, así nadie puede adivinar qué cartas recibirá.
     * Es el único lugar de producción que genera azar; la aplicación lo recibe por el puerto.
     */
    @Bean
    public RandomSource secureRandomSource() {
        SecureRandom random = new SecureRandom();
        return random::nextLong;
    }

    /** Una configuración que no permita armar un mazo detiene el arranque (InitialCollectionRules). */
    @Bean
    public InitialCollectionRules initialCollectionRules(@Value("${app.collection.initial-cards}") int cards,
            @Value("${app.collection.initial-cubes}") long cubes) {
        return new InitialCollectionRules(cards, cubes);
    }

    @Bean
    public DeliverInitialCollectionUseCase deliverInitialCollectionUseCase(UnitOfWork unitOfWork,
            PlayerRepository players, CollectibleCardRepository cards, PlayerCardRepository playerCards,
            CubeRepository cubes, RandomSource random, Clock clock, InitialCollectionRules rules) {
        return new DeliverInitialCollectionUseCase(unitOfWork, players, cards, playerCards, cubes, random, clock,
                rules);
    }
}
