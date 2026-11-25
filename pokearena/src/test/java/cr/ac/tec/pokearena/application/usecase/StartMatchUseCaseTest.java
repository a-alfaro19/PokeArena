package cr.ac.tec.pokearena.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import cr.ac.tec.pokearena.application.dto.MatchRequest;
import cr.ac.tec.pokearena.application.dto.MatchSeat;
import cr.ac.tec.pokearena.application.dto.MatchSettings;
import cr.ac.tec.pokearena.application.port.Clock;
import cr.ac.tec.pokearena.application.port.RandomSource;
import cr.ac.tec.pokearena.domain.effect.LocationEffectRegistry;
import cr.ac.tec.pokearena.domain.error.LocationsUnavailableException;
import cr.ac.tec.pokearena.domain.model.AvailableLocation;
import cr.ac.tec.pokearena.domain.model.ExtraEnergy;
import cr.ac.tec.pokearena.domain.model.Location;
import cr.ac.tec.pokearena.domain.model.LocationFields;
import cr.ac.tec.pokearena.domain.model.MatchContext;
import cr.ac.tec.pokearena.domain.model.MatchParticipant;
import cr.ac.tec.pokearena.domain.model.MatchZone;
import cr.ac.tec.pokearena.domain.model.NewMatch;
import cr.ac.tec.pokearena.domain.repository.LocationRepository;
import cr.ac.tec.pokearena.domain.repository.MatchRepository;
import cr.ac.tec.pokearena.domain.rule.DeckRules;
import cr.ac.tec.pokearena.domain.rule.SeedMixer;
import cr.ac.tec.pokearena.domain.rule.SeededShuffle;

/**
 * Propósito: verificar la preparación coordinada de una partida.
 * Responsabilidades: cubrir seed, selección, decks, mano inicial, energía, apuesta y fallo antes de guardar.
 * Referencia: sección 7.3, pasos 1 a 7; Anexo A.14.
 */
@DisplayName("Preparación de partida")
class StartMatchUseCaseTest {

    private static final long SEED = 82931L;
    private static final Instant NOW = Instant.parse("2026-11-24T12:00:00Z");
    private static final UUID MATCH_ID = UUID.fromString("00000000-0000-0000-0000-000000000051");

    private final RandomSource random = mock(RandomSource.class);
    private final Clock clock = mock(Clock.class);
    private final LocationRepository locations = mock(LocationRepository.class);
    private final MatchRepository matches = mock(MatchRepository.class);
    private final MatchSettings settings = new MatchSettings(3, 2, 3, 7, 2, 1, 6, Duration.ofSeconds(30), 4);
    private final StartMatchUseCase start = new StartMatchUseCase(random, clock, locations, matches, settings,
            new LocationEffectRegistry(List.of()));
    private final List<AvailableLocation> available = availableLocations();
    private final UUID firstPlayer = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private final List<String> firstDeck = codes("first");
    private final List<String> secondDeck = codes("second");

    @ParameterizedTest
    @EnumSource(MatchContext.class)
    @DisplayName("C.10-E1 a E7 y A3: inicia ambos asientos de forma reproducible para cada contexto")
    void preparesBothSeats(MatchContext context) {
        when(random.seed()).thenReturn(SEED);
        when(clock.now()).thenReturn(NOW);
        when(locations.findAllAvailable()).thenReturn(available);
        when(matches.save(any())).thenReturn(MATCH_ID);

        var result = start.execute(new MatchRequest(context,
                new MatchSeat(firstPlayer, 17L, firstDeck), new MatchSeat(null, null, secondDeck)));

        ArgumentCaptor<NewMatch> saved = ArgumentCaptor.forClass(NewMatch.class);
        verify(matches).save(saved.capture());
        NewMatch match = saved.getValue();
        assertThat(result.matchId()).isEqualTo(MATCH_ID);
        assertThat(match.context()).isEqualTo(context);
        assertThat(match.seed()).isEqualTo(SEED);
        assertThat(match.bet()).isEqualTo(settings.initialBet());
        assertThat(match.turn()).isEqualTo(1);
        assertThat(match.turnStartedAt()).isEqualTo(NOW);
        assertThat(match.createdAt()).isEqualTo(NOW);
        assertThat(match.turnDurationSeconds()).isEqualTo(30);
        assertThat(match.vsBot()).isTrue();
        assertThat(match.locations()).hasSize(3).extracting(location -> location.locationId()).doesNotHaveDuplicates();
        assertThat(match.participants()).extracting(MatchParticipant::seat).containsExactly(1, 2);

        MatchParticipant human = match.participants().getFirst();
        MatchParticipant bot = match.participants().getLast();
        assertThat(human.energy()).isEqualTo(3);
        assertThat(bot.energy()).isEqualTo(3);
        assertThat(human.cards()).extracting(card -> card.zone())
                .containsExactly(MatchZone.HAND, MatchZone.HAND, MatchZone.HAND, MatchZone.HAND,
                        MatchZone.DECK, MatchZone.DECK, MatchZone.DECK, MatchZone.DECK,
                        MatchZone.DECK, MatchZone.DECK, MatchZone.DECK, MatchZone.DECK);
        assertThat(human.cards()).extracting(card -> card.cardCode())
                .containsExactlyElementsOf(SeededShuffle.shuffle(firstDeck, SeedMixer.derive(SEED, "seat-1")));
        assertThat(bot.cards()).extracting(card -> card.cardCode())
                .containsExactlyElementsOf(SeededShuffle.shuffle(secondDeck, SeedMixer.derive(SEED, "seat-2")));
        assertThat(human.cards()).filteredOn(card -> card.zone() == MatchZone.GRAVEYARD).isEmpty();
        verify(random).seed();
        verify(clock).now();
    }

    @Test
    @DisplayName("C.10-R1: no guarda nada si no puede seleccionar las ubicaciones requeridas")
    void rejectsUnavailableLocationsBeforeSaving() {
        when(random.seed()).thenReturn(SEED);
        when(clock.now()).thenReturn(NOW);
        when(locations.findAllAvailable()).thenReturn(available.subList(0, 2));

        assertThatThrownBy(() -> start.execute(new MatchRequest(MatchContext.RANKED,
                new MatchSeat(firstPlayer, 17L, firstDeck), new MatchSeat(null, null, secondDeck))))
                .isInstanceOf(LocationsUnavailableException.class);
        verify(matches, never()).save(any());
    }

    private static List<AvailableLocation> availableLocations() {
        return List.of(location(101, "fija", new ExtraEnergy(ExtraEnergy.Mode.FIXED, 1)),
                location(102, "por-carta", new ExtraEnergy(ExtraEnergy.Mode.PER_CARD, 1)),
                location(103, "sin-energia", ExtraEnergy.none()));
    }

    private static AvailableLocation location(long id, String name, ExtraEnergy energy) {
        Instant createdAt = NOW;
        Location location = Location.create(new LocationFields(name, "Descripción " + name, 3, energy), null,
                "/locations/" + name + "/large.png", "/locations/" + name + "/small.png", createdAt);
        return new AvailableLocation(id, location);
    }

    private static List<String> codes(String prefix) {
        return IntStream.range(0, DeckRules.DECK_SIZE).mapToObj(index -> prefix + "-" + index).toList();
    }
}
