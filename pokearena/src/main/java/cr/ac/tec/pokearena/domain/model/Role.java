package cr.ac.tec.pokearena.domain.model;

import java.util.Arrays;
import java.util.List;

import cr.ac.tec.pokearena.domain.error.ValidationException;

/**
 * Propósito: rol de un jugador en PokéArena (sección 5.2).
 * Responsabilidades: nombrar los dos roles del sistema y convertir el texto recibido en un rol,
 * rechazando con un error tipado un valor ausente o desconocido.
 * Referencia: secciones 5.2 y 6 (columna {@code player.role}).
 */
public enum Role {
    PLAYER,
    ADMIN;

    /**
     * Convierte un texto en rol.
     *
     * @param field nombre del campo que trae el valor, para el error
     * @param value texto recibido, por ejemplo {@code "ADMIN"}
     * @throws ValidationException MISSING_FIELD si falta, INVALID_FIELD si no es un rol
     */
    public static Role fromCode(String field, String value) {
        if (value == null || value.isBlank()) {
            throw ValidationException.missingField(field, "Debe indicar el rol.");
        }
        return Arrays.stream(values())
                .filter(role -> role.name().equals(value))
                .findFirst()
                .orElseThrow(() -> ValidationException.invalidField(field,
                        "El rol '" + value + "' no existe.", allowedValues()));
    }

    /** Nombres de todos los roles, para informarlos como valores permitidos. */
    public static List<String> allowedValues() {
        return Arrays.stream(values()).map(Role::name).toList();
    }
}
