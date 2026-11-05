package cr.ac.tec.pokearena.domain.error;

/**
 * Propósito: errores de una imagen que no cumple los requisitos (secciones 7.1 y 7.2).
 * Responsabilidades: indicar qué requisito se incumplió (formato, peso, proporción o
 * resolución). El campo es siempre el de la imagen del formulario.
 * Referencia: sección 5.3, tipos INVALID_IMAGE_FORMAT, IMAGE_TOO_LARGE, INVALID_IMAGE_RATIO e
 * IMAGE_RESOLUTION_TOO_LOW.
 */
public final class ImageValidationException extends DomainException {

    /** Nombre del campo de imagen en los formularios de carta y ubicación. */
    public static final String IMAGE_FIELD = "image";

    private ImageValidationException(ErrorType type, String message) {
        super(type, message, IMAGE_FIELD, null);
    }

    public static ImageValidationException invalidFormat(String message) {
        return new ImageValidationException(ErrorType.INVALID_IMAGE_FORMAT, message);
    }

    public static ImageValidationException tooLarge(String message) {
        return new ImageValidationException(ErrorType.IMAGE_TOO_LARGE, message);
    }

    public static ImageValidationException invalidRatio(String message) {
        return new ImageValidationException(ErrorType.INVALID_IMAGE_RATIO, message);
    }

    public static ImageValidationException resolutionTooLow(String message) {
        return new ImageValidationException(ErrorType.IMAGE_RESOLUTION_TOO_LOW, message);
    }
}
