package cr.ac.tec.pokearena.application.dto;

/**
 * Propósito: combinar la ficha pública de una carta con la colección de la sesión (8.3).
 * Responsabilidades: conservar copias sin introducir información personal en CatalogCache.
 */
public record CatalogCardDetails(CardDetails card, int copies) {

    /** La propiedad se deriva de las copias para evitar respuestas contradictorias. */
    public boolean owned() {
        return copies > 0;
    }
}
