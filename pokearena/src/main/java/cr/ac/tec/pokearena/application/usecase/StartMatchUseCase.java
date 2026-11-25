package cr.ac.tec.pokearena.application.usecase;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import cr.ac.tec.pokearena.application.dto.MatchRequest;
import cr.ac.tec.pokearena.application.dto.MatchSeat;
import cr.ac.tec.pokearena.application.dto.MatchSettings;
import cr.ac.tec.pokearena.application.dto.MatchStarted;
import cr.ac.tec.pokearena.application.port.Clock;
import cr.ac.tec.pokearena.application.port.RandomSource;
import cr.ac.tec.pokearena.application.port.StartMatch;
import cr.ac.tec.pokearena.domain.effect.LocationEffectRegistry;
import cr.ac.tec.pokearena.domain.model.AvailableLocation;
import cr.ac.tec.pokearena.domain.model.MatchCard;
import cr.ac.tec.pokearena.domain.model.MatchLocation;
import cr.ac.tec.pokearena.domain.model.MatchParticipant;
import cr.ac.tec.pokearena.domain.model.MatchStatus;
import cr.ac.tec.pokearena.domain.model.MatchZone;
import cr.ac.tec.pokearena.domain.model.NewMatch;
import cr.ac.tec.pokearena.domain.repository.LocationRepository;
import cr.ac.tec.pokearena.domain.repository.MatchRepository;
import cr.ac.tec.pokearena.domain.rule.LocationSelectionRules;
import cr.ac.tec.pokearena.domain.rule.SeedMixer;
import cr.ac.tec.pokearena.domain.rule.SeededShuffle;
import cr.ac.tec.pokearena.domain.rule.TurnStartRules;

/**
 * Propósito: preparar un agregado inicial de partida dentro de la transacción de su llamador.
 * Responsabilidades: elegir ubicaciones, barajar los decks, iniciar ambos turnos y guardar el agregado completo.
 * Referencia: sección 7.3, pasos 1 a 7; Anexo A.14.
 */
public final class StartMatchUseCase implements StartMatch {

    private static final String LOCATIONS_SEED_PURPOSE = "locations";

    private final RandomSource random;
    private final Clock clock;
    private final LocationRepository locations;
    private final MatchRepository matches;
    private final MatchSettings settings;
    private final LocationSelectionRules locationSelection;
    private final TurnStartRules turnStart;
    private final int turnDurationSeconds;

    public StartMatchUseCase(RandomSource random, Clock clock, LocationRepository locations, MatchRepository matches,
            MatchSettings settings, LocationEffectRegistry effects) {
        this.random = Objects.requireNonNull(random, "La fuente de semillas es obligatoria");
        this.clock = Objects.requireNonNull(clock, "El reloj es obligatorio");
        this.locations = Objects.requireNonNull(locations, "El repositorio de ubicaciones es obligatorio");
        this.matches = Objects.requireNonNull(matches, "El repositorio de partidas es obligatorio");
        this.settings = Objects.requireNonNull(settings, "La configuración de partidas es obligatoria");
        Objects.requireNonNull(effects, "El registro de efectos es obligatorio");
        this.locationSelection = new LocationSelectionRules(settings.locationsPerMatch(),
                settings.minPlayableLocations(), effects);
        this.turnStart = new TurnStartRules(settings.initialHandSize(), settings.maxHandSize(),
                settings.initialEnergy(), settings.energyPerTurn(), settings.maxTurns());
        this.turnDurationSeconds = Math.toIntExact(settings.turnDuration().toSeconds());
        if (turnDurationSeconds <= 0) {
            throw new IllegalArgumentException("La duración del turno debe ser de al menos un segundo");
        }
    }

    /**
     * Ejecuta A.14 dentro de la transacción ya abierta por el llamador. El repositorio exige esa transacción al
     * guardar; este caso de uso no inicia otra.
     */
    @Override
    public MatchStarted execute(MatchRequest request) {
        Objects.requireNonNull(request, "La solicitud de partida es obligatoria");
        // A.14, mensajes 3 a 6 (paso 1): la semilla es del servidor y el reloj fija el inicio del turno.
        long matchSeed = random.seed();
        Instant now = clock.now();

        // A.14, mensajes 7 a 15 (pasos 2 y 3): se seleccionan ubicaciones disponibles con semilla derivada.
        List<AvailableLocation> selected = locationSelection.select(locations.findAllAvailable(),
                SeedMixer.derive(matchSeed, LOCATIONS_SEED_PURPOSE));

        // A.14, mensajes 16 a 23 (pasos 4 y 5): cada asiento recibe su deck y estado inicial del turno 1.
        List<MatchParticipant> participants = List.of(
                participant(MatchParticipant.FIRST_SEAT, request.first(), matchSeed, selected),
                participant(MatchParticipant.LAST_SEAT, request.second(), matchSeed, selected));
        List<MatchLocation> assignedLocations = selected.stream()
                .map(location -> new MatchLocation(MatchLocation.FIRST_SLOT + selected.indexOf(location), location))
                .toList();
        boolean vsBot = participants.stream().filter(MatchParticipant::isBot).count() == 1;
        NewMatch initial = new NewMatch(request.context(), vsBot, MatchStatus.ACTIVE, matchSeed,
                settings.initialBet(), TurnStartRules.FIRST_TURN, now, turnDurationSeconds, now, assignedLocations,
                participants);
        // A.14, mensajes 24 a 28 (pasos 6 y 7): guardar una vez y devolver el id al llamador.
        return new MatchStarted(matches.save(initial));
    }

    private MatchParticipant participant(int seat, MatchSeat request, long matchSeed,
            List<AvailableLocation> selectedLocations) {
        Objects.requireNonNull(request, "Cada asiento de la solicitud es obligatorio");
        // A.14, mensajes 16 a 23: la semilla se deriva por asiento y el turno 1 parte sin cartas en ubicaciones.
        long seatSeed = SeedMixer.derive(matchSeed, "seat-" + seat);
        List<String> shuffledCodes = SeededShuffle.shuffle(request.deckCodes(), seatSeed);
        List<MatchCard> deck = turnStart.initialDeck(shuffledCodes);
        var result = turnStart.startTurn(TurnStartRules.FIRST_TURN, deck, selectedLocations, Map.of());
        return new MatchParticipant(seat, request.playerId(), request.playerId() == null, request.deckId(),
                result.energy(), true, result.cards());
    }
}
