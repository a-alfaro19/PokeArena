package cr.ac.tec.pokearena.application.usecase;

import cr.ac.tec.pokearena.application.dto.GameOptions;
import cr.ac.tec.pokearena.domain.rule.DeckRules;

/**
 * Propósito: opciones del juego que la interfaz pide antes de mostrar las pantallas del Sprint 3 (por
 * ejemplo, Armar deck).
 * Responsabilidades: tomar el tamaño del deck y el largo máximo de su nombre de {@link DeckRules}, la misma
 * clase que valida los decks. Si cambia una regla, la interfaz la ve sin tocar su código.
 * Referencia: sección 7.4 y sección 11 de la especificación del Sprint 3; decisión D-S3-11; Anexo A.7,
 * mensajes 2 y 3.
 *
 * <h2>Misma plantilla que los demás casos de uso</h2>
 * <ol>
 *   <li><b>Autorizar:</b> ya ocurrió en la API ({@code @RequiresRole({PLAYER, ADMIN})} en
 *       {@code OptionsController}).</li>
 *   <li><b>Idempotencia:</b> no aplica; es una lectura (GET).</li>
 *   <li><b>Validar:</b> no hay parámetros que validar.</li>
 *   <li><b>Ejecutar:</b> solo lee constantes del dominio, sin {@code UnitOfWork} ni base de datos.</li>
 *   <li><b>Responder:</b> 200 con las opciones.</li>
 * </ol>
 *
 * <p>Sin dependencias por ahora: la historia de búsquedas le agregará la configuración del emparejamiento
 * (para {@code minCubesToSearch} y {@code searchPollSeconds}) por el constructor.
 */
public class GetGameOptionsUseCase {

    /** Opciones del juego. */
    public GameOptions execute() {
        return new GameOptions(DeckRules.DECK_SIZE, DeckRules.NAME_MAX_LENGTH);
    }
}
