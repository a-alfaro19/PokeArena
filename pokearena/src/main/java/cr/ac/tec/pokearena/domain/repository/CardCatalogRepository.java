package cr.ac.tec.pokearena.domain.repository;

import java.util.List;

import cr.ac.tec.pokearena.domain.model.CardType;
import cr.ac.tec.pokearena.domain.model.Rarity;

/**
 * Propósito: puerto de lectura de los catálogos fijos de cartas: tipos y rarezas (sección 6).
 * Responsabilidades: entregar los valores existentes, que sirven para validar y para informar los
 * valores permitidos. Son las tablas card_type y rarity, sembradas en V1.
 * Referencia: sección 7.1, paso 5 ("tipo y rareza existentes").
 *
 * <p>Tipos y rarezas van en un mismo puerto porque son catálogos de solo lectura que se usan juntos.
 */
public interface CardCatalogRepository {

    /** Todos los tipos de carta. */
    List<CardType> findAllTypes();

    /** Todas las rarezas. */
    List<Rarity> findAllRarities();
}
