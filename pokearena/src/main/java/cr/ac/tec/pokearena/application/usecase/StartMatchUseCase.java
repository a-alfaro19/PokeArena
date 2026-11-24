package cr.ac.tec.pokearena.application.usecase;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import cr.ac.tec.pokearena.application.dto.MatchRequest;
import cr.ac.tec.pokearena.application.dto.MatchSettings;
import cr.ac.tec.pokearena.application.dto.MatchStarted;
import cr.ac.tec.pokearena.application.port.Clock;
import cr.ac.tec.pokearena.application.port.RandomSource;
import cr.ac.tec.pokearena.domain.model.AvailableLocation;
import cr.ac.tec.pokearena.domain.model.Match;
import cr.ac.tec.pokearena.domain.model.MatchCard;
import cr.ac.tec.pokearena.domain.model.MatchContext;
import cr.ac.tec.pokearena.domain.model.MatchLocation;
import cr.ac.tec.pokearena.domain.model.MatchParticipant;
import cr.ac.tec.pokearena.domain.model.MatchSeat;
import cr.ac.tec.pokearena.domain.model.MatchStatus;
import cr.ac.tec.pokearena.domain.model.MatchZone;
import cr.ac.tec.pokearena.domain.model.NewMatch;
import cr.ac.tec.pokearena.domain.model.TurnStartResult;
import cr.ac.tec.pokearena.domain.repository.LocationRepository;
import cr.ac.tec.pokearena.domain.repository.MatchRepository;
import cr.ac.tec.pokearena.domain.rule.LocationSelectionRules;
import cr.ac.tec.pokearena.domain.rule.SeedMixer;
import cr.ac.tec.pokearena.domain.rule.SeededShuffle;
import cr.ac.tec.pokearena.domain.rule.TurnStartRules;

/**
 * Propósito: preparar una partida nueva, dentro de la transacción de quien forma la pareja.
 * Responsabilidades: seleccionar ubicaciones, barajar los decks desde una semilla de servidor, aplicar el turno
 * inicial y guardar el agregado solo cuando toda la preparación está completa.
 * Referencia: sección 7.3; Anexo A.14; criterios C.10-E1 a E8 y C.10-R1 a R3.
 */
public class StartMatchUseCase {

    private static final String LOCATIONS_SEED_PURPOSE = "locations";
    private static final String SEAT_SEED_PURPOSE = "seat-%d";
    private static final int NO_BET = 0;

    private final RandomSource random;
    private final Clock clock;
    private final LocationRepository locations;
    private final MatchRepository matches;
    private final MatchSettings settings;
    private final LocationSelectionRules locationSelection;
    private final TurnStartRules turnStart;

    public StartMatchUseCase(RandomSource random, Clock clock, LocationRepository locations, MatchRepository matches,
            MatchSettings settings, LocationSelectionRules locationSelection, TurnStartRules turnStart) {
        this.random = random;
        this.clock = clock;
        this.locations = locations;
        this.matches = matches;
        this.settings = settings;
        this.locationSelection = locationSelection;
        this.turnStart = turnStart;
    }

    /**
     * Prepara y guarda la partida. No abre ni confirma una transacción: el llamador debe incluir este método y
     * el cambio de estado de búsqueda en la misma unidad de trabajo.
     */
    public MatchStarted execute(MatchRequest request) {
        // Mensaje 2 del diagrama A.14: el azar del juego parte de una sola semilla del servidor.
        long seed = random.seed();
        // Mensaje 4 del diagrama A.14: todas las fechas de la partida parten del puerto Clock.
        Instant now = clock.now();

        // Mensajes 6 y 7 del diagrama A.14: ubicaciones available y active, no paginadas.
        List<AvailableLocation> available = locations.findAllAvailable();
        // Mensajes 8 a 10 del diagrama A.14: semilla derivada y selección determinista de ubicaciones.
        List<AvailableLocation> selected = locationSelection.select(available,
                SeedMixer.derive(seed, LOCATIONS_SEED_PURPOSE));

        List<MatchLocation> matchLocations = new ArrayList<>(selected.size());
        for (int index = 0; index < selected.size(); index++) {
            matchLocations.add(new MatchLocation(MatchLocation.FIRST_SLOT + index, selected.get(index)));
        }

        List<MatchParticipant> participants = new ArrayList<>(request.seats().size());
        for (MatchSeat seat : request.seats()) {
            // Mensajes 14 a 17 del diagrama A.14: cada asiento tiene una semilla de barajado distinta.
            long seatSeed = SeedMixer.derive(seed, SEAT_SEED_PURPOSE.formatted(seat.seat()));
            List<String> shuffled = SeededShuffle.shuffle(seat.deckCodes(), seatSeed);
            List<MatchCard> deck = TurnStartRules.initialDeck(shuffled);

            // Mensajes 18 y 19 del diagrama A.14: reparte la mano, roba en el turno 1 y calcula su energía.
            TurnStartResult started = turnStart.startTurn(Match.FIRST_TURN, deck, selected, Map.of());
            participants.add(new MatchParticipant(seat.seat(), seat.playerId(), seat.isBot(), seat.deckId(),
                    started.energy(), true, started.cards()));
        }

        int bet = request.context() == MatchContext.RANKED ? settings.initialBet() : NO_BET;
        // Mensaje 20 del diagrama A.14: completa el estado inicial antes de cualquier escritura.
        NewMatch newMatch = new NewMatch(request.context(), request.vsBot(), MatchStatus.ACTIVE, seed, bet,
                Match.FIRST_TURN, now, Math.toIntExact(settings.turnDuration().toSeconds()), now,
                matchLocations, participants);
        // Mensajes 21 y 22 del diagrama A.14: un único save lógico dentro de la transacción del llamador.
        return new MatchStarted(matches.save(newMatch));
    }
}
