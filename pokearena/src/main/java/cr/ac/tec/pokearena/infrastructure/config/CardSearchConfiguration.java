package cr.ac.tec.pokearena.infrastructure.config;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableScheduling;
import cr.ac.tec.pokearena.application.port.CardSearchQuery;
import cr.ac.tec.pokearena.application.port.SearchIdentity;
import cr.ac.tec.pokearena.application.usecase.SearchCardsUseCase;

@Configuration
@EnableScheduling
public class CardSearchConfiguration {
    @Bean
    public SearchCardsUseCase searchCardsUseCase(CardSearchQuery query, SearchIdentity identity) {
        return new SearchCardsUseCase(query, identity);
    }

    @Bean
    @ConditionalOnMissingBean(SearchIdentity.class)
    public SearchIdentity searchIdentity(Environment environment) {
        // Un despliegue sin proveedor real nunca confía en cabeceras de rol enviadas por el cliente.
        var profiles = Arrays.asList(environment.getActiveProfiles());
        boolean testing = (profiles.contains("dev") || profiles.contains("test"))
                && !profiles.contains("prod") && !profiles.contains("production");
        if (!testing) { return authorization -> Optional.empty(); }
        String admin = environment.getProperty("pokearena.search.test-admin-token", "");
        String user = environment.getProperty("pokearena.search.test-user-token", "");
        if ((!admin.isEmpty() && admin.length() < 32) || (!user.isEmpty() && user.length() < 32)
                || (!admin.isEmpty() && admin.equals(user))) {
            throw new IllegalStateException("Configure tokens de prueba distintos de al menos 32 caracteres");
        }
        return authorization -> {
            // Las identidades se asignan en el servidor; los tokens solo se habilitan explícitamente en dev/test.
            if (matches(authorization, admin)) {
                return Optional.of(new SearchIdentity.Identity("test-admin", true));
            }
            if (matches(authorization, user)) {
                return Optional.of(new SearchIdentity.Identity("test-user", false));
            }
            return Optional.empty();
        };
    }

    private static boolean matches(String authorization, String configured) {
        if (configured.isEmpty() || authorization == null || !authorization.startsWith("Bearer ")) { return false; }
        return MessageDigest.isEqual(authorization.substring(7).getBytes(StandardCharsets.UTF_8),
                configured.getBytes(StandardCharsets.UTF_8));
    }
}
