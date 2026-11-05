-- Conserva habilidades previas: null identifica metadatos aún no definidos.
ALTER TABLE abilities ADD COLUMN IF NOT EXISTS trigger_code VARCHAR(100);
ALTER TABLE abilities ADD COLUMN IF NOT EXISTS effect_code VARCHAR(100);
ALTER TABLE abilities ADD COLUMN IF NOT EXISTS parameters JSONB NOT NULL DEFAULT '{}'::jsonb;
ALTER TABLE abilities ADD CONSTRAINT us39_ability_parameters_object CHECK (jsonb_typeof(parameters) = 'object');

-- Unaccent y trigramas permiten coincidencia literal normalizada e indexable.
CREATE EXTENSION IF NOT EXISTS unaccent WITH SCHEMA public;
CREATE EXTENSION IF NOT EXISTS pg_trgm WITH SCHEMA public;
CREATE FUNCTION public.us39_normalize(value TEXT) RETURNS TEXT
LANGUAGE SQL IMMUTABLE PARALLEL SAFE STRICT
AS $$ SELECT lower(public.unaccent('public.unaccent', value)) $$;
CREATE INDEX us39_cards_name ON cards USING gin (public.us39_normalize(name) public.gin_trgm_ops);
CREATE INDEX us39_cards_cost ON cards(cost);
CREATE INDEX us39_cards_power ON cards(power);
CREATE INDEX us39_cards_type_rarity ON cards(type, rarity);
CREATE INDEX us39_cards_ability ON cards(ability_code);
CREATE INDEX us39_abilities_trigger_effect ON abilities(trigger_code, effect_code);

-- Cada búsqueda conserva sujeto, contrato y caducidad en la primaria, entre instancias.
CREATE TABLE card_search_snapshots (
    id UUID PRIMARY KEY,
    owner_id VARCHAR(200) NOT NULL,
    filters TEXT NOT NULL,
    page_size INT NOT NULL CHECK (page_size BETWEEN 1 AND 100),
    total INT NOT NULL DEFAULT 0 CHECK (total BETWEEN 0 AND 20000),
    payload_bytes BIGINT NOT NULL DEFAULT 0,
    expires_at TIMESTAMPTZ NOT NULL DEFAULT (clock_timestamp() + INTERVAL '10 minutes')
);
CREATE INDEX us39_snapshot_expiration ON card_search_snapshots(expires_at);

-- El contenido completo evita cambios en páginas pendientes al modificar metadatos.
CREATE TABLE card_search_results (
    search_id UUID NOT NULL REFERENCES card_search_snapshots(id) ON DELETE CASCADE,
    position INT NOT NULL CHECK (position BETWEEN 1 AND 20001),
    payload TEXT NOT NULL CHECK (octet_length(payload) <= 16384),
    PRIMARY KEY (search_id, position)
);

-- Tokens aleatorios reutilizables por página limitan el crecimiento aun con reintentos.
CREATE TABLE card_search_cursors (
    token UUID PRIMARY KEY,
    search_id UUID NOT NULL REFERENCES card_search_snapshots(id) ON DELETE CASCADE,
    position INT NOT NULL,
    UNIQUE (search_id, position)
);
