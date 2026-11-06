package cr.ac.tec.pokearena.domain.model;

import java.time.Instant;
import java.util.Objects;

import cr.ac.tec.pokearena.domain.rule.CardFieldRules;

/**
 * Propósito: carta del catálogo (historia 2.1.1; sección 6, tabla card).
 * Responsabilidades: reunir los datos de una carta y proteger sus invariantes: costo y poder en
 * rango, nombre no vacío, y que solo esté disponible cuando sus dos imágenes existen.
 * Referencia: sección 6 ("Reglas del modelo") y criterio C.1.
 *
 * <p>Los invariantes repiten, como segunda barrera, lo que ya validó {@code CardFieldRules}: si
 * alguna vez se construyera una carta inválida, falla aquí y no llega a la base.
 *
 * @param code          código único e inmutable
 * @param fields        nombre, costo, poder, tipo y rareza ya validados
 * @param ability       habilidad asignada
 * @param imageLargeUrl URL de la versión grande
 * @param imageSmallUrl URL de la miniatura, o {@code null} si todavía no existe
 * @param imageStatus   estado de las imágenes
 * @param available     si los jugadores pueden verla
 * @param createdAt     momento de creación (puerto Clock)
 */
public record Card(CardCode code, CardFields fields, Ability ability, String imageLargeUrl, String imageSmallUrl,
        ImageStatus imageStatus, boolean available, Instant createdAt) {

    public Card {
        Objects.requireNonNull(code, "El código es obligatorio");
        Objects.requireNonNull(fields, "Los campos son obligatorios");
        Objects.requireNonNull(ability, "La habilidad es obligatoria");
        Objects.requireNonNull(imageLargeUrl, "La versión grande es obligatoria");
        Objects.requireNonNull(imageStatus, "El estado de la imagen es obligatorio");
        Objects.requireNonNull(createdAt, "La fecha de creación es obligatoria");
        if (fields.name().isBlank()
                || fields.cost() < CardFieldRules.MIN_COST || fields.cost() > CardFieldRules.MAX_COST
                || fields.power() < CardFieldRules.MIN_POWER || fields.power() > CardFieldRules.MAX_POWER) {
            throw new IllegalStateException("Los campos de la carta " + code + " no cumplen sus invariantes");
        }
        // READY significa que ambas versiones existen; solo así puede estar disponible (criterio C.1).
        if (imageStatus == ImageStatus.READY && imageSmallUrl == null) {
            throw new IllegalStateException("Una carta READY necesita su miniatura");
        }
        if (available && imageStatus != ImageStatus.READY) {
            throw new IllegalStateException("Una carta solo está disponible cuando sus dos imágenes existen");
        }
    }

    /**
     * Carta nueva. Con miniatura queda READY y disponible; sin miniatura (su generación falló) queda
     * PENDING_RETRY y no disponible, a la espera de la tarea de reintentos (sección 7.1).
     */
    public static Card create(CardCode code, CardFields fields, Ability ability, String imageLargeUrl,
            String imageSmallUrl, Instant createdAt) {
        boolean ready = imageSmallUrl != null;
        return new Card(code, fields, ability, imageLargeUrl, imageSmallUrl,
                ready ? ImageStatus.READY : ImageStatus.PENDING_RETRY, ready, createdAt);
    }
}
