package cr.ac.tec.pokearena.domain.rule;

import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Function;

import cr.ac.tec.pokearena.domain.error.DeckException;
import cr.ac.tec.pokearena.domain.error.ValidationException;
import cr.ac.tec.pokearena.domain.model.NewDeck;

/**
 * Propósito: reglas de forma y de posesión de un deck (historia 3.1.3 "Crear Deck").
 * Responsabilidades: ser la única fuente del tamaño del deck y del largo máximo de su nombre, y validar un
 * deck nuevo ({@link #validateShape}), la posesión de sus cartas ({@link #checkOwnership}) y si un deck
 * guardado sigue siendo jugable ({@link #validateForPlay}). Java puro: sin Spring, sin base de datos y sin
 * reloj (la hora llega por parámetro).
 * Referencia: secciones 5.7, 7.1 (paso 3 y paso 5) y 7.2.1 (paso 5) de la especificación del Sprint 3;
 * Anexo A.7, mensajes 18 y 21; criterios C.7-R1, R2 y R3; decisión Q5 del Anexo B.2.
 *
 * <h2>Decisiones</h2>
 * <ul>
 *   <li>{@code DECK_SIZE} y {@code NAME_MAX_LENGTH} son constantes de dominio y no propiedades porque son
 *       reglas del juego, no parámetros de operación (sección 5.7). La interfaz los obtiene de
 *       {@code GET /api/v1/opciones/juego}.</li>
 *   <li>Se detiene en el primer error, igual que {@code CardFieldRules}: el jugador corrige una cosa a la
 *       vez y el orden de los errores es predecible (y probado).</li>
 *   <li>Cada regla de las cartas (cantidad, repetidas, posesión) se escribe <b>una sola vez</b>, como una
 *       comprobación que devuelve el motivo si falla. {@code validateShape} y {@code checkOwnership} la
 *       convierten en su {@link DeckException}; {@code validateForPlay} devuelve el motivo sin lanzar. Así
 *       crear un deck y usarlo para buscar partida aplican exactamente las mismas reglas.</li>
 *   <li>Los códigos se listan ordenados y sin repetir, para que el mensaje sea estable entre solicitudes.</li>
 * </ul>
 */
public final class DeckRules {

    /** Cantidad exacta de cartas distintas de un deck (criterio C.7-E1). */
    public static final int DECK_SIZE = 12;

    /** Largo mínimo del nombre del deck, en caracteres: no puede quedar vacío. */
    public static final int NAME_MIN_LENGTH = 1;

    /** Largo máximo del nombre del deck, en caracteres (decisión Q5, valor por defecto). */
    public static final int NAME_MAX_LENGTH = 40;

    /** Separador de los códigos en los mensajes. */
    private static final String CODE_SEPARATOR = ", ";

    /**
     * Una regla de las cartas: recibe los datos y devuelve el motivo si no se cumple. El motivo es el mismo
     * texto que lleva la excepción de {@code validateShape} o {@code checkOwnership}.
     */
    private record Problem(String message, Function<String, DeckException> asError) {

        DeckException toError() {
            return asError.apply(message);
        }
    }

    private DeckRules() {
    }

    /**
     * Valida la forma de un deck nuevo y lo devuelve listo para guardar. Mensaje 18 del diagrama A.7.
     *
     * <p>Orden de las comprobaciones (sección 7.1, paso 3); cada error aparece solo si los anteriores pasan:
     * <ol>
     *   <li>Nombre presente: {@code MISSING_FIELD} en {@code name}.</li>
     *   <li>Nombre (sin espacios al inicio ni al final) de {@value #NAME_MIN_LENGTH} a {@value #NAME_MAX_LENGTH}
     *       caracteres: {@code INVALID_FIELD} en {@code name}, con {@code allowedValues ["1..40"]}.</li>
     *   <li>Lista de cartas presente: {@code MISSING_FIELD} en {@code cards}.</li>
     *   <li>Exactamente {@value #DECK_SIZE} cartas: {@code INVALID_DECK_SIZE}, con la cantidad actual.</li>
     *   <li>Cada elemento es un código (no nulo ni vacío): {@code INVALID_FIELD} en {@code cards}.</li>
     *   <li>Sin repetidas: {@code DUPLICATE_DECK_CARDS}, con los códigos repetidos.</li>
     * </ol>
     *
     * @param playerId  jugador dueño del deck (el de la sesión, nunca uno que envíe el cliente)
     * @param name      nombre tal como llegó
     * @param cardCodes códigos tal como llegaron; puede ser {@code null}
     * @param now       hora de creación, que el caso de uso toma del puerto {@code Clock}
     * @return el deck validado, con el nombre limpio y normalizado y los códigos ordenados
     */
    public static NewDeck validateShape(UUID playerId, String name, List<String> cardCodes, Instant now) {
        String cleanName = validateName(name);
        if (cardCodes == null) {
            throw ValidationException.missingField(DeckException.CARDS_FIELD, "Debe elegir las cartas del deck.");
        }
        throwIfPresent(sizeProblem(cardCodes));
        requireCodes(cardCodes);
        throwIfPresent(duplicatesProblem(cardCodes));
        return new NewDeck(playerId, cleanName, NameNormalizer.normalize(cleanName),
                List.copyOf(new TreeSet<>(cardCodes)), now);
    }

    /**
     * Comprueba que el jugador posee todas las cartas del deck. Mensaje 21 del diagrama A.7.
     *
     * @param cardCodes  códigos del deck (ya validados por {@link #validateShape})
     * @param ownedCodes códigos que el jugador posee entre ellos (una sola consulta,
     *                   {@code PlayerCardRepository.findOwnedCards})
     * @throws DeckException {@code CARD_NOT_OWNED}, con los códigos que no posee
     */
    public static void checkOwnership(Collection<String> cardCodes, Collection<String> ownedCodes) {
        throwIfPresent(notOwnedProblem(cardCodes, ownedCodes));
    }

    /**
     * Indica si un deck guardado sigue siendo jugable, sin lanzar excepciones (sección 7.2.1, paso 5).
     *
     * <p>Comprueba, en este orden y con las mismas reglas que la creación: exactamente {@value #DECK_SIZE}
     * cartas, sin repetidas y todas poseídas. Devuelve el motivo de la primera que falla, con sus datos (la
     * cantidad actual o los códigos), o vacío si el deck es jugable.
     *
     * <p>No lanza porque el error que corresponde al buscar partida es {@code INVALID_DECK}, de la historia
     * 4.1.1: quien implemente Buscar partida convierte el motivo en ese error. Así esta clase no depende de
     * tipos de otra historia y las reglas siguen escritas una sola vez (decisión del equipo).
     *
     * @param cardCodes  códigos del deck guardado
     * @param ownedCodes códigos que el jugador posee entre ellos
     * @return el motivo por el que el deck no es jugable, o vacío si lo es
     */
    public static Optional<String> validateForPlay(Collection<String> cardCodes, Collection<String> ownedCodes) {
        return sizeProblem(cardCodes)
                .or(() -> duplicatesProblem(cardCodes))
                .or(() -> notOwnedProblem(cardCodes, ownedCodes))
                .map(Problem::message);
    }

    // =========================================================================================
    // Reglas, escritas una sola vez.
    // =========================================================================================

    private static String validateName(String name) {
        String cleanName = FieldChecks.requireText(DeckException.NAME_FIELD, name, "Debe indicar el nombre del deck.")
                .strip();
        // Se cuentan caracteres (puntos de código), no unidades UTF-16: un emoji cuenta como uno.
        int length = cleanName.codePointCount(0, cleanName.length());
        if (length > NAME_MAX_LENGTH) {
            throw ValidationException.invalidField(DeckException.NAME_FIELD,
                    "El nombre del deck debe tener entre " + NAME_MIN_LENGTH + " y " + NAME_MAX_LENGTH
                            + " caracteres; tiene " + length + ".",
                    List.of(NAME_MIN_LENGTH + ".." + NAME_MAX_LENGTH));
        }
        return cleanName;
    }

    /** Un elemento nulo o vacío no es un código de carta: no se puede comprobar ni guardar. */
    private static void requireCodes(Collection<String> cardCodes) {
        if (cardCodes.stream().anyMatch(code -> code == null || code.isBlank())) {
            throw ValidationException.invalidField(DeckException.CARDS_FIELD,
                    "Cada carta del deck debe indicarse por su código.", List.of());
        }
    }

    private static Optional<Problem> sizeProblem(Collection<String> cardCodes) {
        if (cardCodes.size() == DECK_SIZE) {
            return Optional.empty();
        }
        return Optional.of(new Problem("El deck debe tener exactamente " + DECK_SIZE + " cartas y tiene "
                + cardCodes.size() + ".", DeckException::invalidSize));
    }

    private static Optional<Problem> duplicatesProblem(Collection<String> cardCodes) {
        Set<String> seen = new HashSet<>();
        Set<String> repeated = new TreeSet<>();
        for (String code : cardCodes) {
            if (!seen.add(code)) {
                repeated.add(code);
            }
        }
        if (repeated.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new Problem("El deck tiene cartas repetidas: " + String.join(CODE_SEPARATOR, repeated)
                + ".", DeckException::duplicateCards));
    }

    private static Optional<Problem> notOwnedProblem(Collection<String> cardCodes, Collection<String> ownedCodes) {
        Set<String> owned = new HashSet<>(ownedCodes);
        Set<String> missing = new TreeSet<>();
        for (String code : cardCodes) {
            if (!owned.contains(code)) {
                missing.add(code);
            }
        }
        if (missing.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new Problem("No posees estas cartas: " + String.join(CODE_SEPARATOR, missing) + ".",
                DeckException::cardNotOwned));
    }

    private static void throwIfPresent(Optional<Problem> problem) {
        if (problem.isPresent()) {
            throw problem.get().toError();
        }
    }
}
