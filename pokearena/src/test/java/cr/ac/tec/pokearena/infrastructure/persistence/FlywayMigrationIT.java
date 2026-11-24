package cr.ac.tec.pokearena.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import cr.ac.tec.pokearena.support.PostgresIntegrationTestBase;

/**
 * Propósito: verificar las migraciones V1 a V5 sobre un PostgreSQL real.
 * Responsabilidades: comprobar que Flyway crea todo el esquema de la sección 6, siembra los
 * catálogos de la sección 14 y que la base rechaza los datos que violan sus restricciones.
 * Referencia: secciones 6 y 14 de la especificación del Sprint 3.
 *
 * <p>Cada prueba corre en una transacción que se revierte al terminar ({@code @Transactional}),
 * así las filas insertadas no afectan a otras pruebas que compartan el contenedor.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
@DisplayName("Migraciones de la base de datos (Flyway V1 a V5)")
class FlywayMigrationIT extends PostgresIntegrationTestBase {

    // Las tablas de V1 a V5 y la tabla de control de Flyway.
    private static final List<String> EXPECTED_TABLES = List.of(
            "card_type", "rarity", "ability", "card_type_counter", "card", "location",
            "player", "player_identity", "player_card", "cube_movement", "idempotency_key",
            "deck", "deck_card", "game_match", "game_match_player", "game_match_location",
            "game_match_card", "match_search",
            "flyway_schema_history");

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("Las migraciones V1 a V5 se aplican en orden sobre una base vacía y quedan registradas como exitosas")
    void migrationIsAppliedOnEmptyDatabase() {
        // El contenedor arranca vacío y Flyway registra el esquema, decks, partidas y búsquedas.
        List<Map<String, Object>> history = jdbc.queryForList(
                "SELECT installed_rank, version, success FROM flyway_schema_history ORDER BY installed_rank");

        assertThat(history).extracting(row -> row.get("version")).containsExactly("1", "2", "3", "4", "5");
        assertThat(history).allSatisfy(row -> assertThat(row).containsEntry("success", true));
        assertThat(history.getFirst()).containsEntry("installed_rank", 1);
    }

    @Test
    @DisplayName("El código de carta admite 3 dígitos o más (V2): PKA-FUE-001, PKA-FUE-999 y PKA-FUE-1000")
    void cardCodeAcceptsThreeOrMoreDigits() {
        insertCard("PKA-FUE-001", "READY", "/images/cards/1/small.png", true);
        insertCard("PKA-FUE-999", "READY", "/images/cards/1/small.png", true);
        insertCard("PKA-FUE-1000", "READY", "/images/cards/1/small.png", true);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM card WHERE code LIKE 'PKA-FUE-%'", Integer.class))
                .isEqualTo(3);
    }

    @Test
    @DisplayName("El CHECK del código rechaza menos de 3 dígitos y un tipo fuera del catálogo (V2)")
    void cardCodeCheckRejectsInvalidFormats() {
        assertThatThrownBy(() -> insertCard("PKA-FUE-01", "READY", "/images/cards/1/small.png", true))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_card_code_format");
    }

    @Test
    @DisplayName("El CHECK del código rechaza un tipo que no es FUE, AGU, PLA, ELE, PSI ni LUC (V2)")
    void cardCodeCheckRejectsUnknownType() {
        assertThatThrownBy(() -> insertCard("PKA-XYZ-001", "READY", "/images/cards/1/small.png", true))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_card_code_format");
    }

    @Test
    @DisplayName("Una habilidad de evolución puede apuntar a una carta que todavía no existe (V2)")
    void evolutionTargetNeedNotExist() {
        jdbc.update("""
                INSERT INTO ability (code, name, evolves_to_card_code, created_at)
                VALUES ('HAB-PRUEBA-V2', 'Evoluciona a una carta futura', 'PKA-FUE-999', now())
                """);

        assertThat(jdbc.queryForObject("SELECT evolves_to_card_code FROM ability WHERE code = 'HAB-PRUEBA-V2'",
                String.class)).isEqualTo("PKA-FUE-999");
    }

    @Test
    @DisplayName("Existen todas las tablas de la sección 6, y ninguna otra")
    void allTablesExist() {
        List<String> tables = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables "
                        + "WHERE table_schema = 'public' AND table_type = 'BASE TABLE'",
                String.class);

        assertThat(tables).containsExactlyInAnyOrderElementsOf(EXPECTED_TABLES);
    }

    @Test
    @DisplayName("Están sembrados los 6 tipos de carta con su consecutivo en 0")
    void cardTypesAreSeeded() {
        List<String> types = jdbc.queryForList("SELECT code FROM card_type", String.class);
        List<Integer> counters = jdbc.queryForList(
                "SELECT last_number FROM card_type_counter ORDER BY type_code", Integer.class);

        assertThat(types).containsExactlyInAnyOrder("FUE", "AGU", "PLA", "ELE", "PSI", "LUC");
        assertThat(counters).hasSize(types.size()).containsOnly(0);
    }

    @Test
    @DisplayName("Están sembradas las 4 rarezas con sus nombres en español")
    void raritiesAreSeeded() {
        List<Map<String, Object>> rarities = jdbc.queryForList("SELECT code, name FROM rarity");

        assertThat(rarities).extracting(row -> row.get("code") + "=" + row.get("name"))
                .containsExactlyInAnyOrder(
                        "COMMON=Común", "UNCOMMON=Poco común", "RARE=Rara", "LEGENDARY=Legendaria");
    }

    @Test
    @DisplayName("Existe la habilidad HAB-000 \"Sin Habilidad\", sin efecto, activa y en su versión 1")
    void withoutAbilitySeedExists() {
        Map<String, Object> ability = jdbc.queryForMap(
                "SELECT name, trigger, effect_type, parameters::text AS parameters, limited_to_type, "
                        + "evolves_to_card_code, version, active, created_at FROM ability WHERE code = 'HAB-000'");

        assertThat(ability)
                .containsEntry("name", "Sin Habilidad")
                .containsEntry("trigger", null)
                .containsEntry("effect_type", null)
                .containsEntry("parameters", "{}")
                .containsEntry("limited_to_type", null)
                .containsEntry("evolves_to_card_code", null)
                .containsEntry("version", 1)
                .containsEntry("active", true);
        assertThat(ability.get("created_at")).isNotNull();
    }

    @Test
    @DisplayName("FAILED es un estado de imagen válido para cartas y ubicaciones no disponibles")
    void failedImageStatusIsAccepted() {
        insertCard("PKA-AGU-001", "FAILED", null, false);
        insertLocation("FAILED", null, false);

        Integer failed = jdbc.queryForObject(
                "SELECT (SELECT count(*) FROM card WHERE image_status = 'FAILED') "
                        + "+ (SELECT count(*) FROM location WHERE image_status = 'FAILED')",
                Integer.class);
        assertThat(failed).isEqualTo(2);
    }

    @Test
    @DisplayName("Una carta con la miniatura fallida (FAILED) no puede quedar disponible")
    void failedCardCannotBeAvailable() {
        assertThatThrownBy(() -> insertCard("PKA-AGU-001", "FAILED", null, true))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_card_available_requires_ready");
    }

    @Test
    @DisplayName("Una ubicación con la miniatura fallida (FAILED) no puede quedar disponible")
    void failedLocationCannotBeAvailable() {
        assertThatThrownBy(() -> insertLocation("FAILED", null, true))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_location_available_requires_ready");
    }

    @Test
    @DisplayName("La base rechaza una clave de idempotencia de un usuario que no existe en player")
    void idempotencyKeyRequiresExistingPlayer() {
        UUID unknownPlayer = UUID.randomUUID();

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO idempotency_key (key, user_id, endpoint, request_hash, response_status,
                                             response_body, created_at)
                VALUES ('clave-1', ?, 'POST /api/v1/cartas', 'hash', 201, '{}'::jsonb, now())
                """, unknownPlayer))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_idempotency_key_player");
    }

    @Test
    @DisplayName("La base rechaza registrar dos veces la misma identidad (proveedor, sub)")
    void sameProviderIdentityCannotBeInsertedTwice() {
        UUID firstPlayer = insertPlayer(0);
        UUID secondPlayer = insertPlayer(0);
        insertIdentity("google", "sub-123", firstPlayer);

        // Aunque sea otro jugador, la misma identidad del proveedor no puede repetirse.
        assertThatThrownBy(() -> insertIdentity("google", "sub-123", secondPlayer))
                .isInstanceOf(DuplicateKeyException.class)
                .hasMessageContaining("pk_player_identity");
    }

    @Test
    @DisplayName("La base rechaza un saldo de cubos negativo")
    void negativeCubeBalanceIsRejected() {
        assertThatThrownBy(() -> insertPlayer(-1))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_player_cube_balance_non_negative");
    }

    @Test
    @DisplayName("La base rechaza una carta en la colección con cero copias")
    void zeroCopiesAreRejected() {
        UUID player = insertPlayer(0);
        String cardCode = insertReadyCard();

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO player_card (player_id, card_code, copies) VALUES (?, ?, 0)", player, cardCode))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_player_card_copies_positive");
    }

    @Test
    @DisplayName("Existe el índice de trigramas (GIN) sobre name_normalized")
    void trigramIndexExists() {
        Integer extensions = jdbc.queryForObject(
                "SELECT count(*) FROM pg_extension WHERE extname = 'pg_trgm'", Integer.class);
        String definition = jdbc.queryForObject(
                "SELECT indexdef FROM pg_indexes WHERE tablename = 'card' "
                        + "AND indexname = 'idx_card_name_normalized_trgm'",
                String.class);

        assertThat(extensions).isEqualTo(1);
        assertThat(definition).contains("USING gin", "name_normalized gin_trgm_ops");
    }

    // ---------------------------------------------------------------------------------------
    // V3: decks (Sprint 3, historia 3.1.3, sección 6.1).
    // ---------------------------------------------------------------------------------------

    @Test
    @DisplayName("V3: un deck guarda sus cartas y empieza en la versión 1, activo")
    void deckIsStoredWithItsCards() {
        UUID player = insertPlayer(0);
        String cardCode = insertReadyCard();
        long deck = insertDeck(player, "Fuego", "fuego");
        jdbc.update("INSERT INTO deck_card (deck_id, card_code) VALUES (?, ?)", deck, cardCode);

        assertThat(jdbc.queryForMap("SELECT version, active FROM deck WHERE id = ?", deck))
                .containsEntry("version", 1).containsEntry("active", true);
        assertThat(jdbc.queryForList("SELECT card_code FROM deck_card WHERE deck_id = ?", String.class, deck))
                .containsExactly(cardCode);
    }

    @Test
    @DisplayName("V3: un jugador no puede tener dos decks con el mismo nombre normalizado (uq_deck_player_name)")
    void deckNameIsUniquePerPlayer() {
        UUID player = insertPlayer(0);
        insertDeck(player, "Fuego", "fuego");

        assertThatThrownBy(() -> insertDeck(player, "FUEGO", "fuego"))
                .isInstanceOf(DuplicateKeyException.class)
                .hasMessageContaining("uq_deck_player_name");
    }

    @Test
    @DisplayName("V3: dos jugadores distintos sí pueden usar el mismo nombre de deck")
    void sameDeckNameForDifferentPlayers() {
        insertDeck(insertPlayer(0), "Fuego", "fuego");
        insertDeck(insertPlayer(0), "Fuego", "fuego");

        assertThat(jdbc.queryForObject("SELECT count(*) FROM deck WHERE name_normalized = 'fuego'", Integer.class))
                .isEqualTo(2);
    }

    @Test
    @DisplayName("V3: un deck no puede repetir una carta (pk_deck_card)")
    void deckCannotRepeatCard() {
        UUID player = insertPlayer(0);
        String cardCode = insertReadyCard();
        long deck = insertDeck(player, "Fuego", "fuego");
        jdbc.update("INSERT INTO deck_card (deck_id, card_code) VALUES (?, ?)", deck, cardCode);

        assertThatThrownBy(() -> jdbc.update("INSERT INTO deck_card (deck_id, card_code) VALUES (?, ?)", deck,
                cardCode))
                .isInstanceOf(DuplicateKeyException.class)
                .hasMessageContaining("pk_deck_card");
    }

    @Test
    @DisplayName("V3: un deck no puede incluir una carta inexistente (fk_deck_card_card)")
    void deckCardMustExist() {
        long deck = insertDeck(insertPlayer(0), "Fuego", "fuego");

        // Prueba aparte: tras un error, PostgreSQL aborta la transacción de la prueba y no evalúa más sentencias.
        assertThatThrownBy(() -> jdbc.update("INSERT INTO deck_card (deck_id, card_code) VALUES (?, ?)", deck,
                "PKA-AGU-999"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_deck_card_card");
    }

    @Test
    @DisplayName("V3: un deck exige un jugador existente (fk_deck_player)")
    void deckRequiresExistingPlayer() {
        UUID unknownPlayer = UUID.randomUUID();

        assertThatThrownBy(() -> insertDeck(unknownPlayer, "Fuego", "fuego"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_deck_player");
    }

    @Test
    @DisplayName("V3: existe el índice idx_deck_player sobre (player_id, id) para listar mis decks por cursor")
    void deckPlayerIndexExists() {
        String definition = jdbc.queryForObject(
                "SELECT indexdef FROM pg_indexes WHERE tablename = 'deck' AND indexname = 'idx_deck_player'",
                String.class);

        assertThat(definition).contains("(player_id, id)");
    }

    // ---------------------------------------------------------------------------------------
    // Datos mínimos para las pruebas de restricciones.
    // ---------------------------------------------------------------------------------------

    private UUID insertPlayer(long cubeBalance) {
        return jdbc.queryForObject(
                "INSERT INTO player (role, cube_balance, created_at) VALUES ('PLAYER', ?, now()) RETURNING id",
                UUID.class, cubeBalance);
    }

    private void insertIdentity(String provider, String subject, UUID player) {
        jdbc.update("INSERT INTO player_identity (provider, provider_subject, player_id) VALUES (?, ?, ?)",
                provider, subject, player);
    }

    private String insertReadyCard() {
        String code = "PKA-FUE-001";
        insertCard(code, "READY", "/images/cards/1/small.png", true);
        return code;
    }

    private void insertCard(String code, String imageStatus, String smallImageUrl, boolean available) {
        jdbc.update("""
                INSERT INTO card (code, name, name_normalized, cost, power, type_code, rarity_code,
                                  ability_code, image_large_url, image_small_url, image_status,
                                  available, created_at)
                VALUES (?, 'Charmander', 'charmander', 1, 2, 'FUE', 'COMMON', 'HAB-000',
                        '/images/cards/1/large.png', ?, ?, ?, now())
                """, code, smallImageUrl, imageStatus, available);
    }

    private void insertLocation(String imageStatus, String smallImageUrl, boolean available) {
        jdbc.update("""
                INSERT INTO location (name, description, card_limit, extra_energy_mode, extra_energy_amount,
                                      image_large_url, image_small_url, image_status, available, created_at)
                VALUES ('Volcán', 'Un volcán activo', 4, 'NONE', 0,
                        '/images/locations/1/large.png', ?, ?, ?, now())
                """, smallImageUrl, imageStatus, available);
    }

    private long insertDeck(UUID player, String name, String nameNormalized) {
        return jdbc.queryForObject(
                "INSERT INTO deck (player_id, name, name_normalized, created_at) VALUES (?, ?, ?, now()) RETURNING id",
                Long.class, player, name, nameNormalized);
    }
}
