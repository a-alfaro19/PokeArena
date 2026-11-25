-- =============================================================================
-- Propósito: persistir las búsquedas y su relación atómica con las partidas.
-- Responsabilidades: guardar estado, selección de deck, Elo y avisos de preparación.
-- Referencia: sección 6.3 y decisiones D-S3-03, D-S3-04 y D-S3-06.
-- =============================================================================

CREATE TABLE match_search (
    id             UUID        NOT NULL,
    player_id      UUID        NOT NULL,
    deck_id        BIGINT      NOT NULL,
    context        TEXT        NOT NULL,
    status         TEXT        NOT NULL,
    elo            INTEGER     NOT NULL,
    match_id       UUID,
    notice         TEXT,
    not_before     TIMESTAMPTZ,
    created_at     TIMESTAMPTZ NOT NULL,
    last_polled_at TIMESTAMPTZ NOT NULL,
    closed_at      TIMESTAMPTZ,
    CONSTRAINT pk_match_search PRIMARY KEY (id),
    CONSTRAINT fk_match_search_player FOREIGN KEY (player_id) REFERENCES player (id),
    CONSTRAINT fk_match_search_deck FOREIGN KEY (deck_id) REFERENCES deck (id),
    CONSTRAINT fk_match_search_match FOREIGN KEY (match_id) REFERENCES game_match (id),
    CONSTRAINT ck_match_search_context CHECK (context IN ('RANKED', 'TOURNAMENT')),
    CONSTRAINT ck_match_search_status CHECK (status IN ('WAITING', 'MATCHED', 'CANCELLED', 'EXPIRED')),
    CONSTRAINT ck_match_search_match CHECK ((status = 'MATCHED') = (match_id IS NOT NULL))
);

CREATE UNIQUE INDEX uq_match_search_open
    ON match_search (player_id) WHERE status = 'WAITING';

CREATE INDEX idx_match_search_waiting
    ON match_search (created_at, id) WHERE status = 'WAITING';
