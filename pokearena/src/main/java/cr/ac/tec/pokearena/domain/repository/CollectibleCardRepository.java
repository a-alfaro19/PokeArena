package cr.ac.tec.pokearena.domain.repository;

import java.util.List;

/**
 * Propósito: puerto de lectura de las cartas que un jugador puede recibir (patrón Repository, sección
 * 4.4): las disponibles y activas del catálogo.
 * Responsabilidades: contarlas por rareza y elegir al azar una cantidad de ellas, distintas, en la base,
 * sin cargar el catálogo en memoria (el catálogo puede llegar a unas 10.000 cartas).
 * Referencia: sección 8.2, pasos 3 y 4, y diagrama A.5 (mensajes 5, 6, 9 y 10).
 *
 * <p>Es un puerto aparte de {@link CardRepository} (segregación de interfaces): Crear carta y Buscar carta
 * no necesitan elegir cartas al azar. Lo implementa la misma clase JDBC de cartas.
 */
public interface CollectibleCardRepository {

    /** Cantidad de cartas disponibles y activas de la rareza. */
    int countCollectible(String rarityCode);

    /**
     * Elige al azar, en la base, cartas disponibles y activas de la rareza. La misma semilla con el
     * mismo catálogo da siempre la misma selección; semillas distintas dan selecciones distintas.
     *
     * @param rarityCode rareza de las cartas
     * @param seed       semilla de la selección (puerto {@code RandomSource})
     * @param count      cuántas cartas elegir
     * @return códigos de cartas distintas; menos que {@code count} solo si no hay suficientes
     */
    List<String> pickRandomCollectible(String rarityCode, long seed, int count);
}
