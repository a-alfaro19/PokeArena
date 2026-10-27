package cr.ac.tec.pokearena;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.env.MockEnvironment;
import cr.ac.tec.pokearena.application.dto.CardSearchRequest;
import cr.ac.tec.pokearena.application.port.SearchIdentity;
import cr.ac.tec.pokearena.application.usecase.SearchCardsUseCase;
import cr.ac.tec.pokearena.domain.model.CardSearchCriteria;
import cr.ac.tec.pokearena.domain.model.CardSearchException;
import cr.ac.tec.pokearena.infrastructure.config.CardSearchConfiguration;
import static org.junit.jupiter.api.Assertions.*;

class CardSearchValidationTests {
    @ParameterizedTest
    @CsvSource({"type,FIRE", "rarity,MYTHIC", "cost,-1", "cost,11", "power,-21", "power,21",
            "cost,1.5", "power,abc", "costMin,99", "powerMax,-99", "unknown,1", "name,''"})
    void rejectsInvalidCriteria(String criterion, String value) {
        // Cada fallo debe identificar el criterio que el consumidor puede corregir.
        var error = assertThrows(CardSearchException.class,
                () -> new CardSearchCriteria(Map.of(criterion, value)));
        assertEquals(criterion, error.criterion());
    }

    @Test
    void rejectsAmbiguousOrReversedRanges() {
        // Los rangos abiertos son válidos; los ambiguos o invertidos nunca se reinterpretan.
        for (String field : new String[] { "cost", "power" }) {
            assertThrows(CardSearchException.class, () -> new CardSearchCriteria(Map.of(field, "1", field + "Min", "0")));
            assertThrows(CardSearchException.class, () -> new CardSearchCriteria(Map.of(field + "Min", "2", field + "Max", "1")));
            assertDoesNotThrow(() -> new CardSearchCriteria(Map.of(field + "Min", "0")));
        }
    }

    @Test
    void cursorAndPageSizeAreValidatedBeforeQuery() {
        // Un adaptador que falla permite demostrar que entradas inválidas no alcanzan almacenamiento.
        var useCase = new SearchCardsUseCase((criteria, size, cursor, user) -> { throw new AssertionError(); },
                authorization -> Optional.of(new SearchIdentity.Identity("admin", true)));
        for (var values : java.util.List.of(Map.of("pageSize", "0"), Map.of("pageSize", "101"),
                Map.of("pageSize", "x"), Map.of("cursor", "bad"), Map.of("cursor", "1-1-1-1-1"))) {
            assertThrows(CardSearchException.class, () -> useCase.execute(new CardSearchRequest(values.entrySet().stream().collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, entry -> java.util.List.of(entry.getValue())))), "ignored"));
        }
        assertDoesNotThrow(() -> UUID.fromString(UUID.randomUUID().toString()));
    }

    @Test
    void authenticationIsClosedByDefaultAndProductionOverridesTestProfiles() {
        // Incluso perfiles mal combinados deben deshabilitar las credenciales de simulación.
        var configuration = new CardSearchConfiguration();
        String token = UUID.randomUUID().toString();
        for (String[] profiles : new String[][] { {}, { "prod" }, { "dev", "prod" }, { "test", "production" } }) {
            var environment = new MockEnvironment().withProperty("pokearena.search.test-admin-token", token);
            environment.setActiveProfiles(profiles);
            assertTrue(configuration.searchIdentity(environment).authenticate("Bearer " + token).isEmpty());
        }
        var dev = new MockEnvironment().withProperty("pokearena.search.test-admin-token", token);
        dev.setActiveProfiles("dev");
        assertTrue(configuration.searchIdentity(dev).authenticate("Bearer " + token).orElseThrow().administrator());
        assertTrue(configuration.searchIdentity(dev).authenticate("Bearer arbitrary").isEmpty());
    }

    @Test
    void repeatedParametersAuthenticateExactlyOnceWithoutQuerying() {
        // Un contador y una consulta que falla comprueban el orden sin duplicar autenticación.
        for (var identity : java.util.List.of(Optional.<SearchIdentity.Identity>empty(),
                Optional.of(new SearchIdentity.Identity("member", false)),
                Optional.of(new SearchIdentity.Identity("admin", true)))) {
            var calls = new java.util.concurrent.atomic.AtomicInteger();
            var useCase = new SearchCardsUseCase((a, b, c, d) -> { throw new AssertionError("No consultar catálogo"); },
                    token -> { calls.incrementAndGet(); return identity; });
            var error = assertThrows(CardSearchException.class, () -> useCase.execute(
                    new CardSearchRequest(Map.of("type", java.util.List.of("FUE", "AGU"))), "token"));
            var expected = identity.isEmpty() ? CardSearchException.Reason.UNAUTHENTICATED
                    : identity.get().administrator() ? CardSearchException.Reason.INVALID : CardSearchException.Reason.FORBIDDEN;
            assertEquals(expected, error.reason());
            assertEquals(1, calls.get());
        }
    }

    @Test
    void authorizationPrecedesSearchValidation() {
        // No autenticar y no autorizar son fallos distinguibles sin tocar la consulta.
        var request = new CardSearchRequest(Map.of("cost", java.util.List.of("bad")));
        var anonymous = new SearchCardsUseCase((a, b, c, d) -> { throw new AssertionError(); }, value -> Optional.empty());
        assertEquals(CardSearchException.Reason.UNAUTHENTICATED,
                assertThrows(CardSearchException.class, () -> anonymous.execute(request, null)).reason());
        var member = new SearchCardsUseCase((a, b, c, d) -> { throw new AssertionError(); },
                value -> Optional.of(new SearchIdentity.Identity("member", false)));
        assertEquals(CardSearchException.Reason.FORBIDDEN,
                assertThrows(CardSearchException.class, () -> member.execute(request, "token")).reason());
    }
}
