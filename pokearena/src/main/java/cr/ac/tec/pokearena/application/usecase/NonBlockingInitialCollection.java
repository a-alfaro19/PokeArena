package cr.ac.tec.pokearena.application.usecase;

import java.util.UUID;

import cr.ac.tec.pokearena.application.port.InitialCollectionDelivery;
import cr.ac.tec.pokearena.application.port.InitialCollectionStatus;

/**
 * Propósito: el paso 6 del inicio de sesión (diagrama A.4, mensajes 14 y 15): entregar la colección
 * inicial SIN impedir nunca el ingreso.
 * Responsabilidades: invocar el puerto {@link InitialCollectionDelivery} y, si lanza una excepción,
 * registrar un aviso y devolver {@link InitialCollectionStatus#PENDING_ERROR} en lugar de propagarla.
 * Referencia: sección 8.1 (paso 6, "no bloquea el ingreso") y sección 8.2 ("todo o nada; el siguiente
 * ingreso reintenta").
 *
 * <p>La usan el inicio de sesión con proveedor y las credenciales de prueba ({@code dev/sesiones}), para
 * que ambos traten una falla exactamente igual. Es interna del paquete: no es un caso de uso.
 *
 * <p>Decisiones:
 * <ul>
 *   <li>Atrapar la falla aquí es seguro: la entrega corre en su propia transacción, separada de la que
 *       creó la cuenta, y si falla se revierte entera. No queda nada a medias y la cuenta ya existe.</li>
 *   <li>El aviso lleva el {@code correlationId} de la solicitud sin pasarlo como parámetro: el filtro de
 *       correlación lo deja en el MDC del hilo y cada línea de log lo incluye (sección 5.6). El aviso
 *       incluye la causa para diagnosticar, pero el jugador solo ve el mensaje fijo del estado.</li>
 * </ul>
 */
final class NonBlockingInitialCollection {

    // Logger de Java (java.lang.System.Logger): la aplicación no depende de Spring ni de una biblioteca
    // de logs. En ejecución sus mensajes salen por el mismo sistema de logs de Spring Boot, con el MDC.
    private static final System.Logger LOG = System.getLogger(NonBlockingInitialCollection.class.getName());

    private final InitialCollectionDelivery delivery;

    NonBlockingInitialCollection(InitialCollectionDelivery delivery) {
        this.delivery = delivery;
    }

    /** Entrega la colección; ante cualquier falla devuelve {@code PENDING_ERROR} sin lanzar nada. */
    InitialCollectionStatus deliver(UUID playerId) {
        try {
            return delivery.deliver(playerId);
        } catch (RuntimeException failure) {
            LOG.log(System.Logger.Level.WARNING, "No se pudo entregar la colección inicial del jugador " + playerId
                    + "; el ingreso continúa con PENDING_ERROR y se reintentará en su próximo ingreso", failure);
            return InitialCollectionStatus.PENDING_ERROR;
        }
    }
}
