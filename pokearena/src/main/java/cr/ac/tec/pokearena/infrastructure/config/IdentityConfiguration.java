package cr.ac.tec.pokearena.infrastructure.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import cr.ac.tec.pokearena.application.port.IdentityProvider;
import cr.ac.tec.pokearena.application.port.IdentityProviderRegistry;
import cr.ac.tec.pokearena.application.port.InitialCollectionDelivery;
import cr.ac.tec.pokearena.application.port.TokenIssuer;
import cr.ac.tec.pokearena.application.usecase.LoginWithProviderUseCase;
import cr.ac.tec.pokearena.application.usecase.PlayerAccountService;
import cr.ac.tec.pokearena.application.usecase.ProvisionalInitialCollectionDelivery;

/**
 * Propósito: ensamblar el inicio de sesión con proveedor (historia 1.1.1).
 * Responsabilidades: construir el registro de proveedores con TODOS los beans {@link IdentityProvider}
 * (así un proveedor nuevo se registra solo con declararlo como bean), la entrega de la colección inicial
 * y {@link LoginWithProviderUseCase}, que no llevan anotaciones de Spring (sección 4.3).
 * Referencia: sección 8.1 y sección 4.4 (Strategy y Registry).
 */
@Configuration
public class IdentityConfiguration {

    @Bean
    public IdentityProviderRegistry identityProviderRegistry(List<IdentityProvider> providers) {
        return new IdentityProviderRegistry(providers);
    }

    /**
     * TEMPORAL: la tarea de la historia 3.1.1 reemplaza este bean por {@code DeliverInitialCollectionUseCase},
     * que implementa el mismo puerto (sección 8.2).
     */
    @Bean
    public InitialCollectionDelivery initialCollectionDelivery() {
        return new ProvisionalInitialCollectionDelivery();
    }

    @Bean
    public LoginWithProviderUseCase loginWithProviderUseCase(IdentityProviderRegistry providers,
            PlayerAccountService accounts, InitialCollectionDelivery initialCollection, TokenIssuer tokenIssuer) {
        return new LoginWithProviderUseCase(providers, accounts, initialCollection, tokenIssuer);
    }
}
