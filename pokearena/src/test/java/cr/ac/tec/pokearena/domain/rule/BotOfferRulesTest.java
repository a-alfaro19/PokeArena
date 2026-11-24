package cr.ac.tec.pokearena.domain.rule;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import cr.ac.tec.pokearena.domain.model.MatchContext;
import cr.ac.tec.pokearena.domain.model.MatchSearch;
import cr.ac.tec.pokearena.domain.model.MatchSearchStatus;

class BotOfferRulesTest {

    private static final Instant START = Instant.parse("2026-11-23T12:00:00Z");
    private final BotOfferRules rules = new BotOfferRules(Duration.ofSeconds(60));

    @Nested
    @DisplayName("Éxito")
    class Success {
        @Test
        @DisplayName("C.9-E3: la oferta aparece al cumplir 60 segundos desde el inicio y sigue disponible")
        void offerUsesCreationTime() {
            MatchSearch search = searchWithStatus(MatchSearchStatus.WAITING);
            assertThat(rules.isAvailable(search, START.plusSeconds(60).minusNanos(1))).isFalse();
            assertThat(rules.isAvailable(search, START.plusSeconds(60))).isTrue();
            assertThat(rules.isAvailable(search, START.plusSeconds(65))).isTrue();
        }
    }

    @Nested
    @DisplayName("Alternos")
    class Alternatives {
        @ParameterizedTest
        @EnumSource(value = MatchSearchStatus.class, names = "WAITING", mode = EnumSource.Mode.EXCLUDE)
        @DisplayName("C.9-E3: una búsqueda cerrada no ofrece el bot")
        void closedSearchHasNoOffer(MatchSearchStatus status) {
            assertThat(rules.isAvailable(searchWithStatus(status), START.plusSeconds(65))).isFalse();
        }

        @Test
        @DisplayName("C.9-E3: el plazo de la oferta se recibe por configuración")
        void configurableThreshold() {
            BotOfferRules longerWait = new BotOfferRules(Duration.ofSeconds(70));
            MatchSearch search = searchWithStatus(MatchSearchStatus.WAITING);
            assertThat(longerWait.isAvailable(search, START.plusSeconds(60))).isFalse();
            assertThat(longerWait.isAvailable(search, START.plusSeconds(70))).isTrue();
        }
    }

    private MatchSearch searchWithStatus(MatchSearchStatus status) {
        UUID matchId = status == MatchSearchStatus.MATCHED ? new UUID(0, 3) : null;
        Instant lastPoll = START.plusSeconds(55);
        Instant closedAt = status == MatchSearchStatus.WAITING ? null : lastPoll;
        return new MatchSearch(new UUID(0, 1), new UUID(0, 2), 7L, MatchContext.RANKED,
                status, 0, matchId, null, null, START, lastPoll, closedAt);
    }
}
