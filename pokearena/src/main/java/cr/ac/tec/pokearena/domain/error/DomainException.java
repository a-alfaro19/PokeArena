package cr.ac.tec.pokearena.domain.error;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Propósito: clase base de todos los errores de dominio (sección 5.3).
 * Responsabilidades: llevar el tipo de error, un mensaje en español para el usuario y, cuando
 * aplica, el campo afectado y sus valores permitidos. La API la traduce al formato único sin
 * tener que conocer cada subclase.
 * Referencia: sección 5.3 ("Las excepciones de validación son DomainException tipadas").
 *
 * <p>Decisiones:
 * <ul>
 *   <li>Es abstracta: siempre se lanza una subclase de una familia (validación, imagen, ...),
 *       y cada familia solo ofrece fábricas para sus propios tipos. Así no se puede crear,
 *       por ejemplo, un error de imagen con el tipo {@code FORBIDDEN}.</li>
 *   <li>Es {@code RuntimeException}: los casos de uso no tienen que declarar cada error, y
 *       {@code UnitOfWork} revierte la transacción cuando una de ellas escapa.</li>
 *   <li>El mensaje es seguro para mostrar al usuario: nunca lleva datos internos.</li>
 * </ul>
 */
public abstract class DomainException extends RuntimeException {

    private final ErrorType type;
    private final String field;
    private final List<String> allowedValues;

    protected DomainException(ErrorType type, String message, String field, List<String> allowedValues) {
        super(requireMessage(message));
        this.type = Objects.requireNonNull(type, "El tipo de error es obligatorio");
        this.field = field;
        this.allowedValues = allowedValues == null ? List.of() : List.copyOf(allowedValues);
    }

    protected DomainException(ErrorType type, String message) {
        this(type, message, null, null);
    }

    public ErrorType type() {
        return type;
    }

    /** Campo afectado, si el error se refiere a uno (por ejemplo {@code cost}). */
    public Optional<String> field() {
        return Optional.ofNullable(field);
    }

    /** Valores permitidos del campo (por ejemplo {@code ["0..10"]}); vacía si no aplica. */
    public List<String> allowedValues() {
        return allowedValues;
    }

    private static String requireMessage(String message) {
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("Todo error de dominio necesita un mensaje para el usuario");
        }
        return message;
    }
}
