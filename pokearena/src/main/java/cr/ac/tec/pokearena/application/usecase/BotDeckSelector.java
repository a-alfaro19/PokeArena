package cr.ac.tec.pokearena.application.usecase;

import java.util.List;

import cr.ac.tec.pokearena.application.port.RandomSource;
import cr.ac.tec.pokearena.domain.error.MatchSearchException;
import cr.ac.tec.pokearena.domain.repository.CollectibleCardRepository;
import cr.ac.tec.pokearena.domain.rule.DeckRules;

/** Arma el deck del bot al aceptar la oferta (A.13, mensajes 17 y 18). */
public final class BotDeckSelector {

    private static final String BOT_RARITY = "COMMON";

    private final CollectibleCardRepository cards;
    private final RandomSource random;

    public BotDeckSelector(CollectibleCardRepository cards, RandomSource random) {
        this.cards = cards;
        this.random = random;
    }

    public List<String> select() {
        // El repositorio selecciona en SQL cartas distintas, disponibles y activas.
        List<String> selected = cards.pickRandomCollectible(BOT_RARITY, random.seed(), DeckRules.DECK_SIZE);
        if (selected.size() != DeckRules.DECK_SIZE) {
            throw MatchSearchException.insufficientBotCards();
        }
        return List.copyOf(selected);
    }
}
