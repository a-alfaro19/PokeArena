-- =============================================================================
-- Propósito: tablas de la historia 3.1.3 "Crear Deck" (Sprint 3).
-- Responsabilidades: guardar los decks de cada jugador (deck) y sus cartas
-- (deck_card), con nombre único por jugador y un índice para listarlos paginados.
-- Referencia: sección 6.1 de la especificación del Sprint 3 y Anexos A.7 y A.8.
--
-- Decisiones:
-- * deck.id es BIGSERIAL (no UUID) para reutilizar la paginación por cursor
--   "id > cursor ORDER BY id" del Sprint 2 (sección 5.5, D-S3-09).
-- * La cantidad exacta de cartas (DeckRules.DECK_SIZE) la impone la aplicación, no
--   un CHECK: así el número vive una sola vez, como regla del dominio (sección 6.1).
--   Por la misma razón el largo máximo del nombre (DeckRules.NAME_MAX_LENGTH) no
--   está en el esquema.
-- * La clave primaria de deck_card (deck_id, card_code) impide repetir una carta
--   dentro del mismo deck, aunque DeckRules ya lo rechaza antes de guardar.
-- * name_normalized (minúsculas, sin tildes) lo calcula el dominio con
--   NameNormalizer, igual que en las cartas; la unicidad por jugador se aplica
--   sobre él, así "Fuego" y "fuégo" chocan. El choque se detecta al guardar y se
--   traduce a DECK_NAME_TAKEN (sección 7.1), lo que es correcto bajo concurrencia.
-- * created_at no tiene valor por defecto: llega desde el puerto Clock, como en V1.
-- * Mismas convenciones que V1: restricciones con nombre (pk_, fk_, uq_, ck_, idx_).
-- =============================================================================

CREATE TABLE deck (
    id              BIGSERIAL   NOT NULL,
    player_id       UUID        NOT NULL,
    name            TEXT        NOT NULL,
    name_normalized TEXT        NOT NULL,
    version         INTEGER     NOT NULL DEFAULT 1,
    active          BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_deck PRIMARY KEY (id),
    CONSTRAINT fk_deck_player FOREIGN KEY (player_id) REFERENCES player (id),
    CONSTRAINT uq_deck_player_name UNIQUE (player_id, name_normalized),
    CONSTRAINT ck_deck_name_not_blank CHECK (btrim(name) <> ''),
    CONSTRAINT ck_deck_version_positive CHECK (version >= 1)
);

CREATE TABLE deck_card (
    deck_id   BIGINT NOT NULL,
    card_code TEXT   NOT NULL,
    CONSTRAINT pk_deck_card PRIMARY KEY (deck_id, card_code),
    CONSTRAINT fk_deck_card_deck FOREIGN KEY (deck_id) REFERENCES deck (id),
    CONSTRAINT fk_deck_card_card FOREIGN KEY (card_code) REFERENCES card (code)
);

-- "Mis decks" paginado por cursor: WHERE player_id = ? AND id > ? ORDER BY id LIMIT ?.
CREATE INDEX idx_deck_player ON deck (player_id, id);
