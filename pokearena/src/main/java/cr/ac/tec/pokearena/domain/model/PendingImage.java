package cr.ac.tec.pokearena.domain.model;

import java.util.Objects;

import cr.ac.tec.pokearena.domain.rule.ImageRules;

/**
 * Propósito: carta o ubicación a la que le falta la miniatura (estado {@code PENDING_RETRY}).
 * Responsabilidades: llevar lo que necesita la tarea de reintentos: de qué tipo de entidad se trata,
 * su identificador, la URL de su versión grande y cuántos intentos lleva.
 * Referencia: sección 7.1 ("Si falla la miniatura" y "Operaciones de la tarea de reintentos") y ADR-002.
 *
 * @param kind          carta o ubicación (decide la tabla y el tamaño de la miniatura)
 * @param id            identificador de la fila
 * @param largeUrl      URL pública de la versión grande, ya guardada
 * @param retryCount    intentos de la tarea ya hechos ({@code retry_count})
 */
public record PendingImage(ImageRules.Kind kind, long id, String largeUrl, int retryCount) {

    public PendingImage {
        Objects.requireNonNull(kind, "El tipo de entidad es obligatorio");
        Objects.requireNonNull(largeUrl, "La URL de la versión grande es obligatoria");
    }
}
