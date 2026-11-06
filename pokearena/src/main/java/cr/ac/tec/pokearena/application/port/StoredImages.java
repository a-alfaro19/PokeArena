package cr.ac.tec.pokearena.application.port;

import java.util.Objects;

/**
 * Propósito: resultado de guardar las imágenes de una carta o ubicación (paso 9 de A.1 y A.2).
 * Responsabilidades: devolver las URLs públicas que se guardan en la entidad y la clave con la que
 * se borran si la transacción falla (compensación, sección 9).
 * Referencia: sección 6 ("URLs públicas relativas, no rutas de disco") y sección 9.
 *
 * @param storageKey clave para borrar lo guardado; la genera el servidor
 * @param largeUrl   URL relativa de la versión grande, por ejemplo {@code /images/cards/<id>/large.png}
 * @param smallUrl   URL relativa de la miniatura, o {@code null} si no se pudo generar
 */
public record StoredImages(String storageKey, String largeUrl, String smallUrl) {

    public StoredImages {
        Objects.requireNonNull(storageKey, "La clave de almacenamiento es obligatoria");
        Objects.requireNonNull(largeUrl, "La URL de la versión grande es obligatoria");
    }
}
