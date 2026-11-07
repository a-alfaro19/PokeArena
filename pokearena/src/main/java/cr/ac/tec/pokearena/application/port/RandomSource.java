package cr.ac.tec.pokearena.application.port;

/**
 * Propósito: puerto de la aleatoriedad (regla 5 de la sección 4.2; participante "Fuente de aleatoriedad"
 * del diagrama A.5).
 * Responsabilidades: entregar una semilla al azar, generada en el servidor, sin que la aplicación use
 * {@code Random} ni {@code UUID.randomUUID()} directamente. Las pruebas usan una semilla fija para que
 * la selección sea reproducible.
 * Referencia: sección 4.2 (regla 5) y sección 8.2, paso 4 ("usando RandomSource para poder probar con una
 * semilla fija; ningún dato del cliente influye").
 */
@FunctionalInterface
public interface RandomSource {

    /** Una semilla nueva al azar. */
    long seed();
}
