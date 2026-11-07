package cr.ac.tec.pokearena.application.usecase;

import java.util.List;
import java.util.UUID;

import cr.ac.tec.pokearena.application.port.Clock;
import cr.ac.tec.pokearena.application.port.InitialCollectionDelivery;
import cr.ac.tec.pokearena.application.port.InitialCollectionStatus;
import cr.ac.tec.pokearena.application.port.RandomSource;
import cr.ac.tec.pokearena.application.port.UnitOfWork;
import cr.ac.tec.pokearena.domain.model.CubeMovement;
import cr.ac.tec.pokearena.domain.model.CubeMovementReason;
import cr.ac.tec.pokearena.domain.model.InitialCollectionState;
import cr.ac.tec.pokearena.domain.repository.CollectibleCardRepository;
import cr.ac.tec.pokearena.domain.repository.CubeRepository;
import cr.ac.tec.pokearena.domain.repository.PlayerCardRepository;
import cr.ac.tec.pokearena.domain.repository.PlayerRepository;
import cr.ac.tec.pokearena.domain.rule.InitialCollectionRules;

/**
 * Propósito: caso de uso de la historia 3.1.1 "Recibir colección inicial".
 * Responsabilidades: entregar, una sola vez, las cartas Comunes distintas elegidas al azar en el servidor
 * y los cubos iniciales de un jugador, en una sola transacción y en el orden del diagrama de secuencia.
 * Referencia: sección 8.2 (tabla de pasos) y Anexo A.5 (diagrama de secuencia).
 *
 * <h2>Plantilla de los casos de uso (sección 4.4)</h2>
 * Sigue el mismo esqueleto que {@link CreateCardUseCase}:
 * <ol>
 *   <li><b>Autorizar:</b> no aplica aquí. No tiene ruta propia: lo invoca el inicio de sesión (sección
 *       8.1, paso 6) con el id de un jugador ya autenticado. Ningún dato del cliente llega a este caso de
 *       uso, solo ese id (criterio C.5).</li>
 *   <li><b>Idempotencia</b> (pasos 1 y 2): no usa {@code Idempotency-Key}. La garantiza el bloqueo de la
 *       fila del jugador más la marca {@code DELIVERED}: un segundo intento la encuentra y termina.</li>
 *   <li><b>Validar</b> (paso 3): que haya cartas Comunes suficientes.</li>
 *   <li><b>Ejecutar en UnitOfWork</b> (pasos 1 a 7): TODO ocurre en una sola transacción. Si cualquier
 *       paso falla, se revierte todo: nunca queda una entrega parcial (criterio C.5).</li>
 *   <li><b>Responder:</b> el estado de la entrega.</li>
 * </ol>
 *
 * <h2>Decisiones</h2>
 * <ul>
 *   <li>Exactamente una vez: {@code SELECT ... FOR UPDATE} sobre la fila del jugador. Dos entregas
 *       simultáneas se ponen en fila; la segunda espera a que la primera confirme y entonces ve
 *       {@code DELIVERED} (en READ COMMITTED, PostgreSQL vuelve a leer la fila bloqueada).</li>
 *   <li>Sin cartas suficientes se confirma la transacción sin escribir nada y se devuelve
 *       {@code PENDING_NO_CARDS}: la cuenta queda sin colección ni cubos y el siguiente ingreso reintenta.</li>
 *   <li>La selección al azar ocurre en la base con la semilla del puerto {@link RandomSource}: con 10.000
 *       cartas no se cargan en memoria, y con una semilla fija las pruebas son reproducibles.</li>
 *   <li>Si algo falla, la excepción se propaga sin convertirse en un estado: quien invoca (el inicio de
 *       sesión) decide cómo informarla ({@code PENDING_ERROR}) y el siguiente ingreso reintenta.</li>
 *   <li>Implementa el puerto {@link InitialCollectionDelivery}: así el inicio de sesión (paso 6 del
 *       diagrama A.4) depende del puerto y no de esta clase, y cada historia se prueba por separado.</li>
 * </ul>
 */
public class DeliverInitialCollectionUseCase implements InitialCollectionDelivery {

    private final UnitOfWork unitOfWork;
    private final PlayerRepository players;
    private final CollectibleCardRepository cards;
    private final PlayerCardRepository playerCards;
    private final CubeRepository cubes;
    private final RandomSource random;
    private final Clock clock;
    private final InitialCollectionRules rules;

    public DeliverInitialCollectionUseCase(UnitOfWork unitOfWork, PlayerRepository players,
            CollectibleCardRepository cards, PlayerCardRepository playerCards, CubeRepository cubes,
            RandomSource random, Clock clock, InitialCollectionRules rules) {
        this.unitOfWork = unitOfWork;
        this.players = players;
        this.cards = cards;
        this.playerCards = playerCards;
        this.cubes = cubes;
        this.random = random;
        this.clock = clock;
        this.rules = rules;
    }

    /**
     * Puerto {@link InitialCollectionDelivery}, que usa el inicio de sesión (sección 8.1, paso 6). El
     * resultado de {@link #execute} ya es un {@link InitialCollectionStatus} ({@code DELIVERED},
     * {@code ALREADY_DELIVERED} o {@code PENDING_NO_CARDS}), así que pasa sin cambios. Una falla se
     * propaga: {@code PENDING_ERROR} lo decide quien invoca, porque es quien sabe que el ingreso sigue.
     */
    @Override
    public InitialCollectionStatus deliver(UUID playerId) {
        return execute(playerId);
    }

    /**
     * Entrega la colección inicial del jugador, si todavía no la recibió.
     *
     * @param playerId jugador existente (el que acaba de iniciar sesión)
     * @return {@code DELIVERED}, {@code ALREADY_DELIVERED} o {@code PENDING_NO_CARDS}
     * @throws IllegalStateException si el jugador no existe (es un error de quien invoca, no del cliente)
     */
    public InitialCollectionStatus execute(UUID playerId) {
        // Mensaje 1 del diagrama A.5: ejecutar transacción. Todo lo que sigue va en ella.
        return unitOfWork.execute(() -> {
            // Paso 1 (mensajes 2 y 3): bloquear la fila del jugador y leer su estado de colección.
            InitialCollectionState state = players.lockInitialCollectionState(playerId)
                    .orElseThrow(() -> new IllegalStateException("No existe el jugador " + playerId));

            // Paso 2 (mensaje 4): ya entregada, termina sin cambios.
            if (state == InitialCollectionState.DELIVERED) {
                return InitialCollectionStatus.ALREADY_DELIVERED;
            }

            // Paso 3 (mensajes 5 y 6): contar las Comunes disponibles y activas. Si no alcanzan, se
            // confirma sin entrega (mensaje 14): la cuenta queda sin colección ni cubos.
            int available = cards.countCollectible(InitialCollectionRules.CARD_RARITY);
            if (available < rules.cardCount()) {
                return InitialCollectionStatus.PENDING_NO_CARDS;
            }

            // Paso 4 (mensajes 7 a 10): semilla generada en el servidor y selección de cartas distintas
            // en la base. Ningún dato del cliente influye.
            long seed = random.seed();
            List<String> chosen = cards.pickRandomCollectible(InitialCollectionRules.CARD_RARITY, seed,
                    rules.cardCount());
            if (chosen.size() < rules.cardCount()) {
                // Otra transacción retiró cartas entre el conteo y la selección: todavía no se escribió
                // nada, así que se trata igual que el paso 3.
                return InitialCollectionStatus.PENDING_NO_CARDS;
            }

            // Pasos 5 a 7 (mensajes 11 y 12): una copia de cada carta, los cubos iniciales como primer
            // movimiento del historial y la marca de entregada. Si algo falla, se revierte todo.
            playerCards.addOneCopyOfEach(playerId, chosen);
            cubes.apply(new CubeMovement(playerId, rules.cubes(), CubeMovementReason.INITIAL_GRANT, clock.now()));
            players.markInitialCollectionDelivered(playerId);

            // Mensaje 13: colección entregada.
            return InitialCollectionStatus.DELIVERED;
        });
    }
}
