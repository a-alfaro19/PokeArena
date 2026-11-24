-- =============================================================================
-- Propósito: almacenar las búsquedas de partida del Sprint 3 (historias 4.1.1 y 4.1.2).
-- Responsabilidades: persistir el estado de cada búsqueda, su deck y Elo de entrada,
-- y permitir concurrencia segura con índices parciales y consultas con SKIP LOCKED.
-- Referencia: sección 6.3 de la especificación del Sprint 3 y Anexos A.9 a A.13.
--
-- Decisiones:
-- * Una búsqueda WAITING por jugador se garantiza con un índice único parcial.
-- * MATCHED si y solo si match_id está presente se garantiza con CHECK.
-- * Las fechas entran por parámetro desde Clock; ninguna usa now() de PostgreSQL.
-- =============================================================================

CREATE TABLE match_search (
    id             UUID        NOT NULL,
    player_id      UUID        NOT NULL,
    deck_id        BIGINT      NOT NULL,
    context        TEXT        NOT NULL,
    status         TEXT        NOT NULL,
    elo            INTEGER     NOT NULL,
    match_id       UUID        NULL,
    notice         TEXT        NULL,
    not_before     TIMESTAMPTZ NULL,
    created_at     TIMESTAMPTZ NOT NULL,
    last_polled_at TIMESTAMPTZ NOT NULL,
    closed_at      TIMESTAMPTZ NULL,
    CONSTRAINT pk_match_search PRIMARY KEY (id),
    CONSTRAINT fk_match_search_player FOREIGN KEY (player_id) REFERENCES player (id),
    CONSTRAINT fk_match_search_deck FOREIGN KEY (deck_id) REFERENCES deck (id),
    CONSTRAINT fk_match_search_match FOREIGN KEY (match_id) REFERENCES game_match (id),
    CONSTRAINT ck_match_search_context CHECK (context IN ('RANKED', 'TOURNAMENT')),
    CONSTRAINT ck_match_search_status CHECK (status IN ('WAITING', 'MATCHED', 'CANCELLED', 'EXPIRED')),
    CONSTRAINT ck_match_search_match CHECK ((status = 'MATCHED') = (match_id IS NOT NULL))
);

CREATE UNIQUE INDEX uq_match_search_open ON match_search (player_id) WHERE status = 'WAITING';

CREATE INDEX idx_match_search_waiting ON match_search (created_at, id) WHERE status = 'WAITING';
