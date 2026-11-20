package cr.ac.tec.pokearena.domain.rule;

/**
 * Propósito: reglas de forma y de posesión de un deck (historia 3.1.3 "Crear Deck").
 * Responsabilidades: ser la única fuente del tamaño del deck y del largo máximo de su nombre. La
 * interfaz los obtiene de {@code GET /api/v1/opciones/juego}; nunca los escribe a mano.
 * Referencia: secciones 5.7, 7.1 y 7.4 de la especificación del Sprint 3; Anexo A.7 (mensajes 14 y 17);
 * decisión Q5 del Anexo B.2.
 *
 * <p>Fase 1: solo las constantes. Las validaciones ({@code validateShape}, {@code checkOwnership} y
 * {@code validateForPlay}) se agregan en la fase 2, con sus pruebas unitarias.
 *
 * <p>Son constantes de dominio y no propiedades de {@code application.properties} porque son reglas del
 * juego, no parámetros de operación (sección 5.7): cambiarlas cambia qué es un deck válido.
 */
public final class DeckRules {

    /** Cantidad exacta de cartas distintas de un deck (criterio C.7-E1). */
    public static final int DECK_SIZE = 12;

    /** Largo máximo del nombre del deck, en caracteres (decisión Q5, valor por defecto). */
    public static final int NAME_MAX_LENGTH = 40;

    private DeckRules() {
    }
}
