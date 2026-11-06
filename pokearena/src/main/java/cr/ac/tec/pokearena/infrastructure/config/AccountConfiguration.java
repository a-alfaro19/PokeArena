package cr.ac.tec.pokearena.infrastructure.config;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import cr.ac.tec.pokearena.application.port.Clock;
import cr.ac.tec.pokearena.application.port.TokenIssuer;
import cr.ac.tec.pokearena.application.port.UnitOfWork;
import cr.ac.tec.pokearena.application.usecase.IssueDevSessionUseCase;
import cr.ac.tec.pokearena.application.usecase.PlayerAccountService;
import cr.ac.tec.pokearena.domain.model.ProviderIdentity;
import cr.ac.tec.pokearena.domain.repository.PlayerRepository;
import cr.ac.tec.pokearena.domain.rule.AdminIdentities;

/**
 * Propósito: ensamblar las cuentas de jugadores y las sesiones de prueba (sección 5.2).
 * Responsabilidades: leer {@code app.admin.identities}, crear la regla {@link AdminIdentities} y
 * construir los servicios de aplicación, que no llevan anotaciones de Spring (sección 4.3).
 * Referencia: sección 5.2.
 */
@Configuration
public class AccountConfiguration {

    // Separa el proveedor del identificador en cada entrada de app.admin.identities.
    private static final String IDENTITY_SEPARATOR = ":";

    /**
     * Identidades administradoras, a partir de una lista {@code proveedor:sub} separada por comas.
     * Una entrada mal escrita detiene el arranque: un error aquí daría o quitaría permisos sin aviso.
     */
    @Bean
    public AdminIdentities adminIdentities(@Value("${app.admin.identities:}") List<String> entries) {
        Set<ProviderIdentity> identities = new LinkedHashSet<>();
        for (String entry : entries) {
            String value = entry.trim();
            if (value.isEmpty()) {
                continue;
            }
            int separator = value.indexOf(IDENTITY_SEPARATOR);
            if (separator <= 0 || separator == value.length() - 1) {
                throw new IllegalStateException("app.admin.identities tiene una entrada inválida: '" + value
                        + "'. El formato es proveedor:sub, por ejemplo google:1234567890.");
            }
            identities.add(new ProviderIdentity(value.substring(0, separator), value.substring(separator + 1)));
        }
        return new AdminIdentities(identities);
    }

    @Bean
    public PlayerAccountService playerAccountService(UnitOfWork unitOfWork, PlayerRepository players,
            AdminIdentities adminIdentities, Clock clock) {
        return new PlayerAccountService(unitOfWork, players, adminIdentities, clock);
    }

    /** Solo existe con el perfil dev, igual que su controlador (RNF-SEG-10). */
    @Bean
    @Profile("dev")
    public IssueDevSessionUseCase issueDevSessionUseCase(PlayerAccountService accounts, TokenIssuer tokenIssuer) {
        return new IssueDevSessionUseCase(accounts, tokenIssuer);
    }
}
