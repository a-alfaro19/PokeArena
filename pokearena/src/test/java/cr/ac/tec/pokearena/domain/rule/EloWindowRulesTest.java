package cr.ac.tec.pokearena.domain.rule;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import cr.ac.tec.pokearena.domain.model.MatchContext;
import cr.ac.tec.pokearena.domain.model.MatchSearch;
import cr.ac.tec.pokearena.domain.model.MatchSearchStatus;

class EloWindowRulesTest {
    private static final Instant NOW = Instant.parse("2026-11-24T12:00:00Z");
    private final EloWindowRules rules = new EloWindowRules(100, 50, Duration.ofSeconds(10), 400);

    @Test
    @DisplayName("C.8-E3: el rango crece a intervalos completos y se detiene en el tope")
    void growsInSteps() {
        // Probar antes y exactamente en los límites para distinguir truncamiento de redondeo.
        assertThat(rules.rangeAt(NOW, NOW.minusSeconds(1))).isEqualTo(100);
        assertThat(rules.rangeAt(NOW, NOW.plusSeconds(10).minusNanos(1))).isEqualTo(100);
        assertThat(rules.rangeAt(NOW, NOW.plusSeconds(10))).isEqualTo(150);
        assertThat(rules.rangeAt(NOW, NOW.plusSeconds(60))).isEqualTo(400);
        assertThat(rules.rangeAt(NOW, NOW.plusSeconds(600))).isEqualTo(400);
    }

    @Test
    @DisplayName("C.8-E3: gana el mayor rango y la diferencia límite es inclusiva")
    void choosesFirstCompatibleCandidate() {
        // Una candidata con más espera puede ser compatible aunque la primera del lote no lo sea.
        MatchSearch anchor = search(1000, NOW);
        MatchSearch recent = search(1150, NOW);
        MatchSearch older = search(1150, NOW.minusSeconds(10));
        assertThat(rules.pickCompatible(anchor, List.of(recent, older), NOW)).contains(older);
        assertThat(rules.pickCompatible(older, List.of(anchor), NOW)).contains(anchor);
    }

    private static MatchSearch search(int elo, Instant createdAt) {
        // La regla solo necesita el Elo y la antigüedad de la búsqueda.
        return new MatchSearch(UUID.randomUUID(), UUID.randomUUID(), 1, MatchContext.RANKED,
                MatchSearchStatus.WAITING, elo, null, null, null, createdAt, NOW, null);
    }
}
