package cr.ac.tec.pokearena.domain.rule;

/**
 * Propósito: reglas de la colección inicial de un jugador nuevo (historia 3.1.1).
 * Responsabilidades: decir cuántas cartas distintas y de qué rareza se entregan y cuántos cubos, y
 * garantizar al construirse que la colección alcanza para armar un mazo válido.
 * Referencia: sección 8.2 y criterios C.5 ("15 cartas distintas de rareza Común ... y 1,000 cubos";
 * "con la colección inicial es posible crear al menos un deck válido de 12 cartas").
 *
 * <p>Los valores vienen de configuración ({@code app.collection.initial-cards} y
 * {@code app.collection.initial-cubes}); esta clase no sabe de dónde salen, solo los valida. Una
 * configuración que no permita armar un mazo detiene el arranque: sería un error silencioso que dejaría
 * a todos los jugadores nuevos sin poder jugar.
 *
 * @param cardCount cantidad de cartas distintas que se entregan (una copia de cada una)
 * @param cubes     cubos que se entregan
 */
public record InitialCollectionRules(int cardCount, long cubes) {

    /** Cartas de un mazo válido (criterio C.5). Las cartas de la colección inicial deben alcanzar. */
    public static final int DECK_SIZE = 12;

    /** Rareza de las cartas de la colección inicial: Común (criterio C.5). */
    public static final String CARD_RARITY = "COMMON";

    public InitialCollectionRules {
        if (cardCount < DECK_SIZE) {
            throw new IllegalArgumentException("La colección inicial debe tener al menos " + DECK_SIZE
                    + " cartas distintas para poder armar un mazo; se configuraron " + cardCount);
        }
        if (cubes <= 0) {
            throw new IllegalArgumentException("Los cubos iniciales deben ser positivos; se configuraron " + cubes);
        }
    }
}
