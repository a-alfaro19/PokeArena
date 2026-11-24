package cr.ac.tec.pokearena.domain.effect;

import java.util.List;
import java.util.Map;

import cr.ac.tec.pokearena.domain.error.ValidationException;

/**
 * Propósito: un tipo de efecto de ubicación, como plug-in (patrón Strategy, sección 4.4; ADR-001,
 * refinamiento 1: el motor usa microkernel por dentro).
 * Responsabilidades: decir su código, su nombre y la descripción de sus parámetros, y validar sus propios
 * parámetros. Cada efecto es una clase; agregar uno nuevo es agregar una clase y registrarla, SIN
 * modificar el caso de uso ni la interfaz web (que arma el formulario con {@link #parameters()}).
 * Referencia: sección 7.2 ("Plug-ins de efecto"), criterio C.2 y {@code GET /api/v1/opciones/creacion}.
 */
public interface LocationEffectType {

    /** Código del efecto, por ejemplo {@code TYPE_BONUS}. */
    String code();

    /** Nombre del efecto para el usuario, por ejemplo "Bonificación por tipo". */
    String name();

    /**
     * Parámetros que acepta, en el orden en que se muestran. Son los únicos permitidos: {@link #validate}
     * rechaza cualquier otro.
     */
    List<EffectParameterSpec> parameters();

    /**
     * Valida los parámetros del efecto.
     *
     * @param parameters parámetros recibidos, nunca nulos (sin parámetros es un mapa vacío)
     * @throws ValidationException INVALID_EFFECT si faltan, sobran o tienen un valor inválido
     */
    void validate(Map<String, Object> parameters);

    /**
     * Indica si el efecto impide jugar cartas durante todos los turnos de una partida.
     *
     * @param parameters parámetros validados del efecto
     * @return {@code true} solo si el efecto bloquea todos los turnos
     */
    default boolean blocksAllTurns(Map<String, Object> parameters) {
        return false;
    }
}
