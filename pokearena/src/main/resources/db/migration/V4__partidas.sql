-- =============================================================================
-- Propósito: persistir las partidas preparadas (historia 5.1.1).
-- Responsabilidades: guardar el estado inicial, asientos, ubicaciones y cartas por zona.
-- Referencia: sección 6.2 y decisiones D-S3-03, D-S3-05 y D-S3-09.
-- =============================================================================

CREATE TABLE game_match (
    id                    UUID        NOT NULL,
    context               TEXT        NOT NULL,
    vs_bot                BOOLEAN     NOT NULL,
    status                TEXT        NOT NULL,
    seed                  BIGINT      NOT NULL,
    bet                   SMALLINT    NOT NULL,
    turn                  SMALLINT    NOT NULL,
    turn_started_at       TIMESTAMPTZ NOT NULL,
    turn_duration_seconds SMALLINT    NOT NULL,
    created_at            TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_game_match PRIMARY KEY (id),
    CONSTRAINT ck_game_match_context CHECK (context IN ('RANKED', 'TOURNAMENT')),
    CONSTRAINT ck_game_match_status CHECK (status IN ('ACTIVE', 'FINISHED')),
    CONSTRAINT ck_game_match_bet_non_negative CHECK (bet >= 0),
    CONSTRAINT ck_game_match_turn_positive CHECK (turn >= 1),
    CONSTRAINT ck_game_match_turn_duration_positive CHECK (turn_duration_seconds > 0)
);

CREATE TABLE game_match_player (
    match_id  UUID     NOT NULL,
    seat      SMALLINT NOT NULL,
    player_id UUID,
    is_bot    BOOLEAN  NOT NULL,
    deck_id   BIGINT,
    energy    SMALLINT NOT NULL,
    active    BOOLEAN  NOT NULL,
    CONSTRAINT pk_game_match_player PRIMARY KEY (match_id, seat),
    CONSTRAINT fk_game_match_player_match FOREIGN KEY (match_id) REFERENCES game_match (id),
    CONSTRAINT fk_game_match_player_player FOREIGN KEY (player_id) REFERENCES player (id),
    CONSTRAINT fk_game_match_player_deck FOREIGN KEY (deck_id) REFERENCES deck (id),
    CONSTRAINT ck_game_match_player_seat CHECK (seat IN (1, 2)),
    CONSTRAINT ck_game_match_player_energy_non_negative CHECK (energy >= 0),
    CONSTRAINT ck_game_match_player_bot CHECK (
        (is_bot AND player_id IS NULL) OR (NOT is_bot AND player_id IS NOT NULL)
    )
);

CREATE UNIQUE INDEX uq_match_player_active
    ON game_match_player (player_id) WHERE active AND player_id IS NOT NULL;

CREATE TABLE game_match_location (
    match_id    UUID     NOT NULL,
    slot        SMALLINT NOT NULL,
    location_id BIGINT   NOT NULL,
    CONSTRAINT pk_game_match_location PRIMARY KEY (match_id, slot),
    CONSTRAINT fk_game_match_location_match FOREIGN KEY (match_id) REFERENCES game_match (id),
    CONSTRAINT fk_game_match_location_location FOREIGN KEY (location_id) REFERENCES location (id),
    CONSTRAINT uq_match_location UNIQUE (match_id, location_id),
    CONSTRAINT ck_game_match_location_slot CHECK (slot BETWEEN 1 AND 3)
);

CREATE TABLE game_match_card (
    match_id  UUID     NOT NULL,
    seat      SMALLINT NOT NULL,
    position  SMALLINT NOT NULL,
    card_code TEXT     NOT NULL,
    zone      TEXT     NOT NULL,
    CONSTRAINT pk_game_match_card PRIMARY KEY (match_id, seat, position),
    CONSTRAINT uq_match_card UNIQUE (match_id, seat, card_code),
    CONSTRAINT fk_game_match_card_player FOREIGN KEY (match_id, seat)
        REFERENCES game_match_player (match_id, seat),
    CONSTRAINT fk_game_match_card_card FOREIGN KEY (card_code) REFERENCES card (code),
    CONSTRAINT ck_game_match_card_position_non_negative CHECK (position >= 0),
    CONSTRAINT ck_game_match_card_zone CHECK (zone IN ('DECK', 'HAND', 'GRAVEYARD'))
);
