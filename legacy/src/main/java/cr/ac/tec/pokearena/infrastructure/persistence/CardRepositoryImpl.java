package cr.ac.tec.pokearena.infrastructure.persistence;

import java.sql.Timestamp;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import cr.ac.tec.pokearena.domain.model.Card;
import cr.ac.tec.pokearena.domain.model.CardRarity;
import cr.ac.tec.pokearena.domain.model.CardType;
import cr.ac.tec.pokearena.domain.repository.CardRepository;
import cr.ac.tec.pokearena.infrastructure.storage.ImageService;

@Repository
public class CardRepositoryImpl implements CardRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ImageService imageService;

    public CardRepositoryImpl(JdbcTemplate jdbcTemplate, ImageService imageService) {
        this.jdbcTemplate = jdbcTemplate;
        this.imageService = imageService;
    }

    @Override
    public Optional<Card> findByIdempotencyKey(String idempotencyKey) {
        String sql = "SELECT code, name, cost, power, type, rarity, ability_code, " +
                "large_image_url, small_image_url, image_status, created_at " +
                "FROM cards WHERE idempotency_key = ?";
        try {
            Card card = jdbcTemplate.queryForObject(sql, (rs, rowNum) -> new Card(
                    rs.getString("code"),
                    rs.getString("name"),
                    rs.getInt("cost"),
                    rs.getInt("power"),
                    CardType.valueOf(rs.getString("type")),
                    CardRarity.valueOf(rs.getString("rarity")),
                    rs.getString("ability_code"),
                    rs.getString("large_image_url"),
                    rs.getString("small_image_url"),
                    rs.getString("image_status"),
                    rs.getTimestamp("created_at").toLocalDateTime()), idempotencyKey);
            return Optional.ofNullable(card);
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public boolean existsAbilityByCode(String abilityCode) {
        String sql = "SELECT COUNT(1) FROM abilities WHERE code = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, abilityCode);
        return count != null && count > 0;
    }

    @Override
    public String generateNextCodeForType(CardType type) {
        // Genera el código único e inmutable en formato PKA-TIPO-NNN de forma
        // concurrente y segura
        String sql = "UPDATE card_sequences SET last_sequence = last_sequence + 1 WHERE card_type = ? RETURNING last_sequence";
        Integer seq = jdbcTemplate.queryForObject(sql, Integer.class, type.name());
        int sequenceNum = (seq != null) ? seq : 1;
        return String.format("PKA-%s-%03d", type.name(), sequenceNum);
    }

    @Override
    public String saveImage(byte[] imageBytes, String versionType) {
        try {
            if ("large".equals(versionType)) {
                return imageService.saveLargeImage(imageBytes);
            } else {
                return imageService.saveSmallImage(imageBytes);
            }
        } catch (Exception e) {
            throw new RuntimeException("Error al procesar y guardar la imagen: " + e.getMessage());
        }
    }

    @Override
    @Transactional // Garantiza atomicidad entre el guardado de la carta y la clave de idempotencia
    public Card saveInTransaction(Card card, String idempotencyKey) {
        String sql = "INSERT INTO cards (code, name, cost, power, type, rarity, ability_code, " +
                "large_image_url, small_image_url, image_status, idempotency_key, created_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        jdbcTemplate.update(sql,
                card.getCode(),
                card.getName(),
                card.getCost(),
                card.getPower(),
                card.getType().name(),
                card.getRarity().name(),
                card.getAbilityCode(),
                card.getLargeImageUrl(),
                card.getSmallImageUrl(),
                card.getImageStatus(),
                idempotencyKey,
                Timestamp.valueOf(card.getCreatedAt()));

        return card;
    }

}
