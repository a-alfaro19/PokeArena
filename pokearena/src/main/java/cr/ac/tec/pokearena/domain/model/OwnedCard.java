package cr.ac.tec.pokearena.domain.model;

/**
 * Propósito: fila de la colección con la carta y sus copias, obtenidas juntas (Anexo A.6).
 * Responsabilidades: conservar el id para paginar sin consultar las copias por cada carta.
 */
public record OwnedCard(StoredCard stored, int copies) {
}
