package cr.ac.tec.pokearena.application.dto;

/**
 * Propósito: rango de enteros, con ambos extremos inclusivos, tal como lo ve la interfaz.
 * Responsabilidades: llevar el mínimo y el máximo de un campo (costo, poder, límite de cartas) desde las
 * reglas del dominio hasta las opciones de la interfaz, sin copiar los números.
 * Referencia: {@code GET /api/v1/opciones/catalogo} y {@code GET /api/v1/opciones/creacion}.
 *
 * @param min mínimo inclusivo
 * @param max máximo inclusivo
 */
public record ValueRange(int min, int max) {

    public ValueRange {
        if (min > max) {
            throw new IllegalArgumentException("Rango inválido: " + min + " > " + max);
        }
    }
}
