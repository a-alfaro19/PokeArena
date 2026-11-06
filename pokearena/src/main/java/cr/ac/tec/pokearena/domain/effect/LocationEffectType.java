package cr.ac.tec.pokearena.domain.effect;

import java.util.Map;

import cr.ac.tec.pokearena.domain.error.ValidationException;

/**
 * Propósito: un tipo de efecto de ubicación, como plug-in (patrón Strategy, sección 4.4; ADR-001,
 * refinamiento 1: el motor usa microkernel por dentro).
 * Responsabilidades: decir su código y validar sus propios parámetros. Cada efecto es una clase;
 * agregar uno nuevo es agregar una clase y registrarla, SIN modificar el caso de uso.
 * Referencia: sección 7.2 ("Plug-ins de efecto") y criterio C.2.
 */
public interface LocationEffectType {

    /** Código del efecto, por ejemplo {@code TYPE_BONUS}. */
    String code();

    /**
     * Valida los parámetros del efecto.
     *
     * @param parameters parámetros recibidos, nunca nulos (sin parámetros es un mapa vacío)
     * @throws ValidationException INVALID_EFFECT si faltan, sobran o tienen un valor inválido
     */
    void validate(Map<String, Object> parameters);
}
