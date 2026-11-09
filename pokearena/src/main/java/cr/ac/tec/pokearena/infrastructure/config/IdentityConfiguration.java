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

/**
 * Propósito: ensamblar el inicio de sesión con proveedor (historia 1.1.1).
 * Responsabilidades: construir el registro de proveedores con TODOS los beans {@link IdentityProvider}
 * (así un proveedor nuevo se registra solo con declararlo como bean) y {@link LoginWithProviderUseCase},
 * que no lleva anotaciones de Spring (sección 4.3). La entrega de la colección inicial que usa el paso 6
 * la declara {@link CollectionConfiguration}.
 * Referencia: sección 8.1 y sección 4.4 (Strategy y Registry).
 */
@Configuration
public class IdentityConfiguration {

    @Bean
    public IdentityProviderRegistry identityProviderRegistry(List<IdentityProvider> providers) {
        return new IdentityProviderRegistry(providers);
    }

    /**
     * {@code initialCollection} es el único bean del puerto: {@code DeliverInitialCollectionUseCase},
     * declarado en {@link CollectionConfiguration} (sección 8.1, paso 6).
     */
    @Bean
    public LoginWithProviderUseCase loginWithProviderUseCase(IdentityProviderRegistry providers,
            PlayerAccountService accounts, InitialCollectionDelivery initialCollection, TokenIssuer tokenIssuer) {
        return new LoginWithProviderUseCase(providers, accounts, initialCollection, tokenIssuer);
    }
}
