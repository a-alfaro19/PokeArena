package cr.ac.tec.pokearena.domain.model;

/**
 * Propósito: filtros validados del catálogo del jugador (8.3).
 * Responsabilidades: conservar el tipo, rango inclusivo de costo y selección de cartas propias.
 */
public record CatalogCriteria(String type, Integer costMin, Integer costMax, boolean owned) {

    /** Reutiliza los filtros SQL existentes sin habilitar criterios administrativos en la API. */
    public CardSearchCriteria cardCriteria() {
        return CardSearchCriteria.builder().type(type).costBetween(costMin, costMax).build();
    }
}
