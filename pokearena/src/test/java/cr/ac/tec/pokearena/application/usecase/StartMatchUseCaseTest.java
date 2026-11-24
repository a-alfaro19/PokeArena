package cr.ac.tec.pokearena.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import cr.ac.tec.pokearena.application.dto.MatchRequest;
import cr.ac.tec.pokearena.application.dto.MatchSettings;
import cr.ac.tec.pokearena.application.port.Clock;
import cr.ac.tec.pokearena.application.port.RandomSource;
import cr.ac.tec.pokearena.domain.effect.LocationEffectRegistry;
import cr.ac.tec.pokearena.domain.effect.TurnRestrictionEffect;
import cr.ac.tec.pokearena.domain.error.LocationsUnavailableException;
import cr.ac.tec.pokearena.domain.model.AvailableLocation;
import cr.ac.tec.pokearena.domain.model.ExtraEnergy;
import cr.ac.tec.pokearena.domain.model.Location;
import cr.ac.tec.pokearena.domain.model.LocationFields;
import cr.ac.tec.pokearena.domain.model.Match;
import cr.ac.tec.pokearena.domain.model.MatchContext;
import cr.ac.tec.pokearena.domain.model.MatchParticipant;
import cr.ac.tec.pokearena.domain.model.MatchSeat;
import cr.ac.tec.pokearena.domain.model.MatchZone;
import cr.ac.tec.pokearena.domain.model.NewMatch;
import cr.ac.tec.pokearena.domain.repository.LocationRepository;
import cr.ac.tec.pokearena.domain.repository.MatchRepository;
import cr.ac.tec.pokearena.domain.rule.LocationSelectionRules;
import cr.ac.tec.pokearena.domain.rule.SeedMixer;
import cr.ac.tec.pokearena.domain.rule.SeededShuffle;
import cr.ac.tec.pokearena.domain.rule.TurnStartRules;

/**
 * Propósito: probar la preparación completa de una partida sin infraestructura.
 * Responsabilidades: verificar la semilla del servidor, el estado inicial de ambos asientos, la apuesta y que una
 * selección imposible no llegue al repositorio.
 * Referencia: sección 7.3; Anexo A.14; criterios C.10-E1 a E8 y C.10-R1.
 */
@DisplayName("Caso de uso Iniciar partida")
class StartMatchUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-11-20T12:00:00Z");
    private static final long SERVER_SEED = 345_678L;
    private static final int CATALOG_SIZE = 12;
    private static final int INITIAL_HAND_AND_DRAW_SIZE = 4;
    private static final UUID MATCH_ID = UUID.fromString("2e58e671-3449-4c7f-b395-95ebf3ab1f43");
    private static final List<String> DECK_CODES = java.util.stream.IntStream.range(1, CATALOG_SIZE + 1)
            .mapToObj(index -> "PKA-FUE-%03d".formatted(index))
            .toList();

    @Test
    @DisplayName("C.10-E1 a E8: persiste una partida con ubicaciones, decks barajados y turno inicial")
    void preparesAndSavesTheCompleteInitialMatch() {
        CapturingMatchRepository matches = new CapturingMatchRepository();
        StartMatchUseCase useCase = useCase(matches, catalog(3));

        cr.ac.tec.pokearena.application.dto.MatchStarted result = useCase.execute(request());

        assertThat(result.id()).isEqualTo(MATCH_ID);
        assertThat(matches.saved).isNotNull();
        assertThat(matches.saveCalls).isEqualTo(1);
        assertThat(matches.saved.seed()).isEqualTo(SERVER_SEED);
        assertThat(matches.saved.context()).isEqualTo(MatchContext.RANKED);
        assertThat(matches.saved.bet()).isEqualTo(4);
        assertThat(matches.saved.turn()).isEqualTo(Match.FIRST_TURN);
        assertThat(matches.saved.turnStartedAt()).isEqualTo(NOW);
        assertThat(matches.saved.turnDurationSeconds()).isEqualTo(30);
        assertThat(matches.saved.locations()).hasSize(3)
                .extracting(location -> location.location().fields().name()).doesNotHaveDuplicates();

        List<MatchParticipant> participants = matches.saved.participants();
        assertThat(participants).hasSize(2);
        for (MatchParticipant participant : participants) {
            assertThat(participant.energy()).isEqualTo(2);
            assertThat(participant.cards()).hasSize(CATALOG_SIZE);
            assertThat(participant.cards()).filteredOn(card -> card.zone() == MatchZone.HAND)
                    .hasSize(INITIAL_HAND_AND_DRAW_SIZE);
            assertThat(participant.cards()).extracting(card -> card.position())
                    .containsExactlyElementsOf(java.util.stream.IntStream.range(0, CATALOG_SIZE).boxed().toList());
            assertThat(participant.cards()).extracting(card -> card.code())
                    .containsExactlyElementsOf(SeededShuffle.shuffle(DECK_CODES,
                            SeedMixer.derive(SERVER_SEED, "seat-%d".formatted(participant.seat()))))
                    .doesNotHaveDuplicates();
        }
    }

    @Test
    @DisplayName("C.10-R1: si no se pueden elegir tres ubicaciones, no guarda una partida parcial")
    void doesNotSaveWhenLocationsCannotBeSelected() {
        CapturingMatchRepository matches = new CapturingMatchRepository();

        assertThatThrownBy(() -> useCase(matches, catalog(2)).execute(request()))
                .isInstanceOf(LocationsUnavailableException.class);

        assertThat(matches.saveCalls).isZero();
        assertThat(matches.saved).isNull();
    }

    @Test
    @DisplayName("C.10-A1 y R2: la misma semilla de servidor reproduce ubicaciones, decks y mano inicial")
    void sameServerSeedReplaysTheSameInitialState() {
        CapturingMatchRepository firstRepository = new CapturingMatchRepository();
        CapturingMatchRepository replayRepository = new CapturingMatchRepository();

        useCase(firstRepository, catalog(3)).execute(request());
        useCase(replayRepository, catalog(3).reversed()).execute(request());

        assertThat(replayRepository.saved.locations()).extracting(location -> location.id())
                .containsExactlyElementsOf(firstRepository.saved.locations().stream()
                        .map(location -> location.id()).toList());
        assertThat(replayRepository.saved.participants()).extracting(participant -> participant.cards())
                .containsExactlyElementsOf(firstRepository.saved.participants().stream()
                        .map(participant -> participant.cards()).toList());
    }

    @Test
    @DisplayName("C.10-A3: una partida de torneo conserva el reparto, robo y energía; solo cambia el contexto y la apuesta")
    void tournamentUsesTheSamePreparationRules() {
        CapturingMatchRepository rankedRepository = new CapturingMatchRepository();
        CapturingMatchRepository tournamentRepository = new CapturingMatchRepository();

        useCase(rankedRepository, catalog(3)).execute(request(MatchContext.RANKED));
        useCase(tournamentRepository, catalog(3)).execute(request(MatchContext.TOURNAMENT));

        assertThat(tournamentRepository.saved.context()).isEqualTo(MatchContext.TOURNAMENT);
        assertThat(tournamentRepository.saved.bet()).isZero();
        assertThat(tournamentRepository.saved.locations()).extracting(location -> location.id())
                .containsExactlyElementsOf(rankedRepository.saved.locations().stream()
                        .map(location -> location.id()).toList());
        assertThat(tournamentRepository.saved.participants()).extracting(participant -> participant.cards())
                .containsExactlyElementsOf(rankedRepository.saved.participants().stream()
                        .map(participant -> participant.cards()).toList());
        assertThat(tournamentRepository.saved.participants()).extracting(MatchParticipant::energy)
                .containsExactlyElementsOf(rankedRepository.saved.participants().stream()
                        .map(MatchParticipant::energy).toList());
    }

    private static StartMatchUseCase useCase(CapturingMatchRepository matches, List<AvailableLocation> catalog) {
        LocationRepository locations = new LocationRepository() {
            @Override
            public long save(Location location) {
                throw new UnsupportedOperationException();
            }

            @Override
            public List<AvailableLocation> findAllAvailable() {
                return catalog;
            }
        };
        MatchSettings settings = new MatchSettings(3, 2, 3, 7, 2, 1, 6, Duration.ofSeconds(30), 4, 10);
        LocationSelectionRules locationSelection = new LocationSelectionRules(3, 2,
                new LocationEffectRegistry(List.of(new TurnRestrictionEffect())));
        TurnStartRules turnStart = new TurnStartRules(3, 7, 2, 1, 6);
        RandomSource random = () -> SERVER_SEED;
        Clock clock = () -> NOW;
        return new StartMatchUseCase(random, clock, locations, matches, settings, locationSelection, turnStart);
    }

    private static MatchRequest request() {
        return request(MatchContext.RANKED);
    }

    private static MatchRequest request(MatchContext context) {
        MatchSeat player = new MatchSeat(MatchSeat.FIRST_SEAT, UUID.fromString("019b1b1b-1111-7111-8111-111111111111"),
                17L, DECK_CODES);
        MatchSeat bot = new MatchSeat(MatchSeat.SECOND_SEAT, null, null, DECK_CODES);
        return new MatchRequest(context, List.of(player, bot));
    }

    private static List<AvailableLocation> catalog(int count) {
        List<AvailableLocation> locations = new ArrayList<>();
        for (int id = 1; id <= count; id++) {
            Location location = Location.create(new LocationFields("Lugar " + id, "Descripción " + id, 3,
                    ExtraEnergy.none()), null, "/large/" + id, "/small/" + id, NOW);
            locations.add(new AvailableLocation(id, location));
        }
        return locations;
    }

    private static final class CapturingMatchRepository implements MatchRepository {

        private NewMatch saved;
        private int saveCalls;

        @Override
        public UUID save(NewMatch match) {
            saved = match;
            saveCalls++;
            return MATCH_ID;
        }

        @Override
        public Optional<UUID> findActiveMatchId(UUID playerId) {
            return Optional.empty();
        }

        @Override
        public Optional<Match> findByIdForPlayer(UUID matchId, UUID playerId) {
            return Optional.empty();
        }
    }
}
