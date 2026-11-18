package cr.ac.tec.pokearena.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;

import cr.ac.tec.pokearena.domain.rule.ImageRules;

/**
 * Propósito: impedir que el peso máximo de una imagen tenga dos valores distintos en ejecución.
 * Responsabilidades: al arrancar, comparar {@code app.images.max-file-size} (el límite del multipart, que
 * Spring aplica antes de leer el cuerpo) con {@link ImageRules#MAX_FILE_BYTES} (el límite del dominio, que
 * valida el procesador de imágenes y que informa {@code GET /api/v1/opciones/creacion}); si no coinciden, la
 * aplicación no arranca.
 * Referencia: secciones 7.1 y 7.2 (paso 7, "máximo 5 MB") y regla "cero números mágicos" (sección 0).
 *
 * <p>Decisión: el valor del dominio es la fuente del criterio (C.1 y C.2 dicen 5 MB) y el dominio no puede
 * leer configuración (regla 3 de 4.2). La propiedad existe porque Spring la necesita para el multipart. En
 * lugar de confiar en que nadie la cambie (por ejemplo con una variable de entorno), se comprueba al
 * arrancar: un error temprano y claro es mejor que dos límites distintos con mensajes distintos.
 */
@Component
public class ImageSizeLimitCheck {

    public ImageSizeLimitCheck(@Value("${app.images.max-file-size}") DataSize multipartLimit) {
        verify(multipartLimit);
    }

    /**
     * @throws IllegalStateException si el límite configurado no es el del dominio
     */
    static void verify(DataSize multipartLimit) {
        if (multipartLimit.toBytes() != ImageRules.MAX_FILE_BYTES) {
            throw new IllegalStateException("app.images.max-file-size (" + multipartLimit + " = "
                    + multipartLimit.toBytes() + " bytes) debe ser igual al máximo del dominio, ImageRules.MAX_FILE_BYTES ("
                    + ImageRules.MAX_FILE_BYTES + " bytes). Corrija la configuración: el peso máximo tiene una sola fuente.");
        }
    }
}
