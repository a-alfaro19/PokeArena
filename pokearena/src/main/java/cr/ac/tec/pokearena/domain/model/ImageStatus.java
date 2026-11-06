package cr.ac.tec.pokearena.domain.model;

/**
 * Propósito: estado de las imágenes de una carta o ubicación (sección 6, columna image_status).
 * Responsabilidades: decir si ambas versiones existen. Solo con {@link #READY} la entidad puede
 * estar disponible.
 * Referencia: sección 6 ("Reglas del modelo") y sección 7.1 ("Si falla la miniatura").
 */
public enum ImageStatus {
    /** Existen la versión grande y la pequeña. */
    READY,
    /** Falló la miniatura; la tarea programada la reintentará. */
    PENDING_RETRY,
    /** Se agotaron los reintentos de la miniatura. */
    FAILED
}
