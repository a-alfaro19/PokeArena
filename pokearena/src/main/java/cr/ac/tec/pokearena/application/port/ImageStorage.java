package cr.ac.tec.pokearena.application.port;

import java.util.Optional;

import cr.ac.tec.pokearena.domain.rule.ImageRules;

/**
 * Propósito: puerto del almacenamiento de imágenes (corrección 6; participante "Almacenamiento de
 * imágenes" de los diagramas A.1 y A.2).
 * Responsabilidades: guardar la versión grande y la pequeña, borrarlas como compensación,
 * encontrarlas para servirlas y, para la tarea de reintentos, leer la versión grande ya guardada y
 * guardar solo la miniatura de una entidad existente. Está detrás de un puerto para poder cambiar el sistema de archivos
 * por un almacenamiento en la nube sin tocar los casos de uso (sección 9).
 * Referencia: secciones 7.1, 7.2 (paso 9; "Operaciones de la tarea de reintentos") y 9.
 *
 * <p>Los nombres de carpetas y archivos los genera siempre el servidor: este puerto no recibe
 * ningún nombre del cliente.
 */
public interface ImageStorage {

    /**
     * Paso 9: guarda la versión grande en su formato original y, si existe, la miniatura en PNG.
     *
     * @param rules     tipo de imagen, que decide la carpeta (cartas o ubicaciones)
     * @param large     imagen validada
     * @param thumbnail bytes PNG de la miniatura, o {@code null} si su generación falló
     */
    StoredImages save(ImageRules rules, ValidatedImage large, byte[] thumbnail);

    /** Borra todo lo guardado con {@code storageKey}. No falla si ya no existe. */
    void delete(String storageKey);

    /**
     * Archivo de una URL pública, por ejemplo {@code cards/<id>/large.png}. Devuelve vacío si la ruta
     * no tiene la forma que genera el servidor o el archivo no existe.
     */
    Optional<ImageFile> find(String relativePath);

    /**
     * Reintentos (sección 7.1): bytes de la versión grande guardada en {@code largeUrl}, la URL pública
     * que tiene la entidad. Vacío si la URL no tiene la forma que genera el servidor o el archivo ya
     * no existe en disco.
     */
    Optional<byte[]> readLarge(String largeUrl);

    /**
     * Reintentos (sección 7.1): guarda la miniatura PNG en la carpeta de la entidad, la misma de su
     * versión grande, y devuelve la URL pública de la miniatura.
     *
     * @param largeUrl  URL pública de la versión grande de la entidad
     * @param thumbnail bytes PNG de la miniatura
     */
    String saveThumbnail(String largeUrl, byte[] thumbnail);
}
