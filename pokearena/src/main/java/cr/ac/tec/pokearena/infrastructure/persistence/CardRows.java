package cr.ac.tec.pokearena.infrastructure.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;

import org.springframework.stereotype.Component;

import cr.ac.tec.pokearena.domain.model.Card;
import cr.ac.tec.pokearena.domain.model.CardCode;
import cr.ac.tec.pokearena.domain.model.CardFields;
import cr.ac.tec.pokearena.domain.model.CardType;
import cr.ac.tec.pokearena.domain.model.ImageStatus;
import cr.ac.tec.pokearena.domain.model.Rarity;
import cr.ac.tec.pokearena.domain.model.StoredCard;

/**
 * Propósito: convertir una fila de las consultas de cartas en {@link StoredCard}.
 * Responsabilidades: un solo mapeo de la ficha de una carta (con su tipo, su rareza y su habilidad) para
 * todos los adaptadores que la leen: la búsqueda del administrador ({@link JdbcCardRepository}) y el
 * catálogo del jugador ({@link JdbcPlayerCatalogRepository}). Así ambas historias muestran la carta igual.
 * Referencia: sección 7.3 (cada resultado de la búsqueda) y sección 8.3 (cada carta del catálogo).
 *
 * <p>Espera las columnas que arma {@link CardSearchSql}: las de {@code card}, {@code type_name},
 * {@code rarity_name} y las de la habilidad con los alias de {@link AbilityRows#COLUMNS}.
 */
@Component
public class CardRows {

    private final AbilityRows abilityRows;

    public CardRows(AbilityRows abilityRows) {
        this.abilityRows = abilityRows;
    }

    /** Carta de la fila actual, con su {@code id} de paginación. */
    public StoredCard map(ResultSet rs) throws SQLException {
        CardFields fields = new CardFields(rs.getString("name"), rs.getString("name_normalized"),
                rs.getInt("cost"), rs.getInt("power"),
                new CardType(rs.getString("type_code"), rs.getString("type_name")),
                new Rarity(rs.getString("rarity_code"), rs.getString("rarity_name")));
        Card card = new Card(new CardCode(rs.getString("code")), fields, abilityRows.map(rs),
                rs.getString("image_large_url"), rs.getString("image_small_url"),
                ImageStatus.valueOf(rs.getString("image_status")), rs.getBoolean("available"),
                rs.getObject("created_at", OffsetDateTime.class).toInstant());
        return new StoredCard(rs.getLong("id"), card);
    }
}
