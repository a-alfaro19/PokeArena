package cr.ac.tec.pokearena.support;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import cr.ac.tec.pokearena.domain.model.Ability;
import cr.ac.tec.pokearena.domain.model.Card;
import cr.ac.tec.pokearena.domain.model.CardCode;
import cr.ac.tec.pokearena.domain.model.CardFields;
import cr.ac.tec.pokearena.domain.model.CardType;
import cr.ac.tec.pokearena.domain.model.ExtraEnergy;
import cr.ac.tec.pokearena.domain.model.ImageStatus;
import cr.ac.tec.pokearena.domain.model.Location;
import cr.ac.tec.pokearena.domain.model.LocationFields;
import cr.ac.tec.pokearena.domain.model.Match;
import cr.ac.tec.pokearena.domain.model.MatchContext;
import cr.ac.tec.pokearena.domain.model.MatchLocation;
import cr.ac.tec.pokearena.domain.model.MatchParticipant;
import cr.ac.tec.pokearena.domain.model.MatchStatus;
import cr.ac.tec.pokearena.domain.model.MatchZone;
import cr.ac.tec.pokearena.domain.model.Rarity;
import cr.ac.tec.pokearena.domain.model.MatchCard;

/**
 * Propósito: crear partidas completas y seguras para pruebas de aplicación y API.
 * Responsabilidades: mantener fixtures con cartas, ubicaciones y zonas válidas según el modelo.
 * Referencia: sección 7.3; Anexo A.15.
 */
public final class MatchFixtures {

    public static final Instant STARTED_AT = Instant.parse("2026-11-24T12:00:00Z");

    private MatchFixtures() {
    }

    public static Match initial(UUID matchId, UUID playerId) {
        List<MatchLocation> locations = new ArrayList<>();
        for (int slot = MatchLocation.FIRST_SLOT; slot <= MatchLocation.LAST_SLOT; slot++) {
            String name = "Ubicación " + slot;
            Location location = Location.create(
                    new LocationFields(name, "Descripción " + slot, 3, ExtraEnergy.none()), null,
                    "/images/locations/" + slot + "/large.png", "/images/locations/" + slot + "/small.png",
                    STARTED_AT);
            locations.add(new MatchLocation(slot, slot, location));
        }

        Map<String, Card> catalog = new LinkedHashMap<>();
        List<MatchCard> humanCards = cardsFor("FUE", "Fuego", catalog);
        List<MatchCard> rivalCards = cardsFor("AGU", "Agua", catalog);
        MatchParticipant human = new MatchParticipant(1, playerId, false, 71L, 3, true, humanCards);
        MatchParticipant rival = new MatchParticipant(2, UUID.randomUUID(), false, 72L, 3, true, rivalCards);
        return new Match(matchId, MatchContext.RANKED, false, MatchStatus.ACTIVE, 928374L, 4, 1, STARTED_AT, 30,
                STARTED_AT, locations, List.of(human, rival), catalog);
    }

    private static List<MatchCard> cardsFor(String typeCode, String typeName, Map<String, Card> catalog) {
        return IntStream.range(0, 12).mapToObj(index -> {
            CardCode code = CardCode.of(typeCode, index + 1);
            Card card = Card.create(code,
                    new CardFields("Carta " + code.value(), "carta-" + code.value().toLowerCase(), 2, 3,
                            new CardType(typeCode, typeName), new Rarity("COMMON", "Común")),
                    new Ability(Ability.NO_ABILITY_CODE, "Sin habilidad", null, null),
                    "/images/cards/" + code.value() + "/large.png",
                    "/images/cards/" + code.value() + "/small.png", STARTED_AT);
            catalog.put(code.value(), card);
            MatchZone zone = index < 4 ? MatchZone.HAND : MatchZone.DECK;
            return new MatchCard(index, code.value(), zone);
        }).toList();
    }
}
