package cr.ac.tec.pokearena.application.port;

/**
 * Propósito: convertir el resultado de un caso de uso a texto y de vuelta, para guardarlo junto
 * con su clave de idempotencia (sección 5.4).
 * Responsabilidades: aislar la aplicación del formato concreto (JSON) y de la biblioteca que lo
 * implementa; la aplicación no puede depender de JSON (sección 4.2).
 * Referencia: sección 5.4 (columna response_body).
 */
public interface ResponseSerializer {

    /** Convierte {@code value} en texto. */
    String serialize(Object value);

    /** Reconstruye un valor de tipo {@code type} a partir de {@code text}. */
    <T> T deserialize(String text, Class<T> type);
}
