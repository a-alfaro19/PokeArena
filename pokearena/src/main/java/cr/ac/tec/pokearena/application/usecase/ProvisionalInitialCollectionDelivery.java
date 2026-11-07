package cr.ac.tec.pokearena.application.usecase;

import java.util.UUID;

import cr.ac.tec.pokearena.application.port.InitialCollectionDelivery;
import cr.ac.tec.pokearena.application.port.InitialCollectionStatus;

/**
 * Propósito: implementación TEMPORAL del puerto {@link InitialCollectionDelivery}, para que el inicio
 * de sesión (1.1.1) siga el diagrama A.4 completo antes de que exista la historia 3.1.1.
 * Responsabilidades: no entregar nada y responder siempre {@link InitialCollectionStatus#PENDING_NO_CARDS},
 * que es el estado verdadero de una cuenta sin colección: la cuenta existe, sin cartas ni cubos, y el
 * jugador ve el mensaje de "el juego aún no está disponible".
 * Referencia: sección 8.1, paso 6.
 *
 * <p><b>Temporal:</b> se elimina en la tarea de la historia 3.1.1 "Recibir colección inicial", donde
 * {@code DeliverInitialCollectionUseCase} implementa este mismo puerto (sección 8.2) y reemplaza este
 * bean en {@code IdentityConfiguration}. El inicio de sesión no cambia.
 */
public class ProvisionalInitialCollectionDelivery implements InitialCollectionDelivery {

    @Override
    public InitialCollectionStatus deliver(UUID playerId) {
        return InitialCollectionStatus.PENDING_NO_CARDS;
    }
}
