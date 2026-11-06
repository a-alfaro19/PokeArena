package cr.ac.tec.pokearena.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import cr.ac.tec.pokearena.domain.rule.LocationFieldRules;

/**
 * Propósito: ubicación donde se juegan las cartas (historia 2.2.1; sección 6, tabla location).
 * Responsabilidades: reunir los datos de una ubicación y proteger sus invariantes: nombre y
 * descripción no vacíos, límite de cartas en rango, energía extra coherente con su modalidad, y que
 * solo esté disponible para partidas nuevas cuando sus dos imágenes existen.
 * Referencia: sección 6 ("Reglas del modelo"), sección 7.2 y criterio C.2.
 *
 * <p>Los invariantes repiten, como segunda barrera, lo que ya validó {@code LocationFieldRules}.
 *
 * @param fields        nombre, descripción, límite de cartas y energía extra ya validados
 * @param effect        efecto validado por su plug-in, o {@code null} si no tiene
 * @param imageLargeUrl URL de la versión grande
 * @param imageSmallUrl URL de la miniatura, o {@code null} si todavía no existe
 * @param imageStatus   estado de las imágenes
 * @param available     si se puede asignar a partidas nuevas
 * @param createdAt     momento de creación (puerto Clock)
 */
public record Location(LocationFields fields, LocationEffect effect, String imageLargeUrl, String imageSmallUrl,
        ImageStatus imageStatus, boolean available, Instant createdAt) {

    public Location {
        Objects.requireNonNull(fields, "Los campos son obligatorios");
        Objects.requireNonNull(imageLargeUrl, "La versión grande es obligatoria");
        Objects.requireNonNull(imageStatus, "El estado de la imagen es obligatorio");
        Objects.requireNonNull(createdAt, "La fecha de creación es obligatoria");
        if (fields.name().isBlank() || fields.description().isBlank()
                || fields.cardLimit() < LocationFieldRules.MIN_CARD_LIMIT
                || fields.cardLimit() > LocationFieldRules.MAX_CARD_LIMIT) {
            throw new IllegalStateException("Los campos de la ubicación no cumplen sus invariantes");
        }
        // READY significa que ambas versiones existen; solo así puede estar disponible (criterio C.2).
        if (imageStatus == ImageStatus.READY && imageSmallUrl == null) {
            throw new IllegalStateException("Una ubicación READY necesita su miniatura");
        }
        if (available && imageStatus != ImageStatus.READY) {
            throw new IllegalStateException("Una ubicación solo está disponible cuando sus dos imágenes existen");
        }
    }

    /**
     * Ubicación nueva. Con miniatura queda READY y disponible; sin miniatura (su generación falló) queda
     * PENDING_RETRY y no disponible, igual que una carta (sección 7.2, paso 8).
     */
    public static Location create(LocationFields fields, LocationEffect effect, String imageLargeUrl,
            String imageSmallUrl, Instant createdAt) {
        boolean ready = imageSmallUrl != null;
        return new Location(fields, effect, imageLargeUrl, imageSmallUrl,
                ready ? ImageStatus.READY : ImageStatus.PENDING_RETRY, ready, createdAt);
    }

    /** Efecto, si tiene. */
    public Optional<LocationEffect> optionalEffect() {
        return Optional.ofNullable(effect);
    }
}
