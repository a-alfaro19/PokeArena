package cr.ac.tec.pokearena.domain.error;

/**
 * Propósito: indicar que el catálogo no permite preparar las ubicaciones requeridas para una partida.
 * Responsabilidades: detener la preparación antes de guardar datos y comunicar la causa a quien inicia la
 * partida para que revierta su transacción.
 * Referencia: sección 7.3, paso 3; Anexo A.14; criterio C.10-R1.
 */
public final class LocationsUnavailableException extends RuntimeException {

    public LocationsUnavailableException(String message) {
        super(message);
    }
}
