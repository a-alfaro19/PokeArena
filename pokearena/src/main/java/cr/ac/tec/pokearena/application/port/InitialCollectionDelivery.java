package cr.ac.tec.pokearena.application.port;

import java.util.UUID;

/**
 * Propósito: puerto de la entrega de la colección inicial, tal como la ve el inicio de sesión
 * (participante "Caso de uso RecibirColecciónInicial" del diagrama A.4, mensajes 14 y 15).
 * Responsabilidades: entregar, si corresponde, la colección inicial del jugador y decir en qué estado quedó.
 * Referencia: sección 8.1, paso 6, y sección 8.2.
 *
 * <p>El inicio de sesión depende de este puerto y no del caso de uso concreto, así cada historia se
 * implementa y se prueba por separado. Lo implementa {@code DeliverInitialCollectionUseCase}, el único
 * bean de este puerto.
 */
public interface InitialCollectionDelivery {

    /**
     * Entrega la colección inicial del jugador, si todavía no la recibió. Debe ser idempotente.
     *
     * @param playerId jugador que acaba de iniciar sesión
     * @return estado de la colección después del intento
     */
    InitialCollectionStatus deliver(UUID playerId);
}
