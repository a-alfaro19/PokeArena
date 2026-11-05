-- =============================================================================
-- Propósito: esquema inicial completo de PokéArena y sus datos semilla.
-- Responsabilidades: crear las tablas de la sección 6 de la especificación (en
-- singular), sus restricciones e índices, y sembrar los catálogos fijos.
-- Referencia: secciones 6 y 14 de la especificación.
--
-- Convenciones de esta migración:
-- * Texto libre y códigos usan TEXT: PostgreSQL no gana nada con VARCHAR(n) y así
--   no se inventan longitudes máximas que la especificación no define.
-- * Las restricciones llevan nombre (pk_, fk_, uq_, ck_, idx_) para que un error
--   de la base indique qué regla se rompió.
-- * created_at no tiene valor por defecto: la aplicación lo envía desde el puerto
--   Clock (regla 5 de 4.2), y así se puede probar con un reloj fijo.
-- =============================================================================

-- Extensión para búsqueda parcial por nombre con índice de trigramas (sección 6).
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- -----------------------------------------------------------------------------
-- Catálogos fijos: tipos y rarezas (datos semilla al final del archivo).
-- -----------------------------------------------------------------------------
CREATE TABLE card_type (
    code TEXT NOT NULL,
    name TEXT NOT NULL,
    CONSTRAINT pk_card_type PRIMARY KEY (code),
    CONSTRAINT uq_card_type_name UNIQUE (name)
);

CREATE TABLE rarity (
    code TEXT NOT NULL,
    name TEXT NOT NULL,
    CONSTRAINT pk_rarity PRIMARY KEY (code),
    CONSTRAINT uq_rarity_name UNIQUE (name)
);

-- -----------------------------------------------------------------------------
-- Habilidades. evolves_to_card_code apunta a una carta; su clave foránea se agrega
-- después de crear card, porque card también apunta a ability.
-- Lleva version, active y created_at porque la historia "Crear habilidad" (backlog)
-- las hará entidades de administrador, editables y desactivables.
-- -----------------------------------------------------------------------------
CREATE TABLE ability (
    code                 TEXT        NOT NULL,
    name                 TEXT        NOT NULL,
    trigger              TEXT,
    effect_type          TEXT,
    parameters           JSONB       NOT NULL DEFAULT '{}'::jsonb,
    limited_to_type      TEXT,
    evolves_to_card_code TEXT,
    version              INTEGER     NOT NULL DEFAULT 1,
    active               BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at           TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_ability PRIMARY KEY (code),
    CONSTRAINT fk_ability_limited_to_type FOREIGN KEY (limited_to_type) REFERENCES card_type (code),
    CONSTRAINT ck_ability_parameters_object CHECK (jsonb_typeof(parameters) = 'object'),
    CONSTRAINT ck_ability_version_positive CHECK (version >= 1)
);

-- Consecutivo por tipo para el código PKA-<TIPO>-<NNN> (sección 7.1). El límite de
-- 999 lo controla el dominio con un error claro; aquí solo se impide un valor negativo.
CREATE TABLE card_type_counter (
    type_code   TEXT    NOT NULL,
    last_number INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT pk_card_type_counter PRIMARY KEY (type_code),
    CONSTRAINT fk_card_type_counter_type FOREIGN KEY (type_code) REFERENCES card_type (code),
    CONSTRAINT ck_card_type_counter_non_negative CHECK (last_number >= 0)
);

-- -----------------------------------------------------------------------------
-- Cartas (historia 2.1.1). id da el orden estable de la paginación por cursor (5.5).
-- -----------------------------------------------------------------------------
CREATE TABLE card (
    id              BIGSERIAL   NOT NULL,
    code            TEXT        NOT NULL,
    name            TEXT        NOT NULL,
    name_normalized TEXT        NOT NULL,
    cost            INTEGER     NOT NULL,
    power           INTEGER     NOT NULL,
    type_code       TEXT        NOT NULL,
    rarity_code     TEXT        NOT NULL,
    ability_code    TEXT        NOT NULL,
    image_large_url TEXT        NOT NULL,
    image_small_url TEXT,
    image_status    TEXT        NOT NULL,
    retry_count     INTEGER     NOT NULL DEFAULT 0,
    available       BOOLEAN     NOT NULL,
    version         INTEGER     NOT NULL DEFAULT 1,
    active          BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_card PRIMARY KEY (id),
    CONSTRAINT uq_card_code UNIQUE (code),
    CONSTRAINT fk_card_type FOREIGN KEY (type_code) REFERENCES card_type (code),
    CONSTRAINT fk_card_rarity FOREIGN KEY (rarity_code) REFERENCES rarity (code),
    CONSTRAINT fk_card_ability FOREIGN KEY (ability_code) REFERENCES ability (code),
    CONSTRAINT ck_card_code_format CHECK (code ~ '^PKA-[A-Z]{3}-[0-9]{3}$'),
    CONSTRAINT ck_card_name_not_blank CHECK (btrim(name) <> ''),
    CONSTRAINT ck_card_cost_range CHECK (cost BETWEEN 0 AND 10),
    CONSTRAINT ck_card_power_range CHECK (power BETWEEN -20 AND 20),
    -- FAILED: se agotaron los reintentos de la miniatura; la carta sigue no disponible.
    CONSTRAINT ck_card_image_status CHECK (image_status IN ('READY', 'PENDING_RETRY', 'FAILED')),
    CONSTRAINT ck_card_retry_count_non_negative CHECK (retry_count >= 0),
    CONSTRAINT ck_card_version_positive CHECK (version >= 1),
    -- READY significa que la miniatura existe (ADR-002).
    CONSTRAINT ck_card_ready_has_small_image CHECK (image_status <> 'READY' OR image_small_url IS NOT NULL),
    -- Criterio C.1: la carta solo queda disponible cuando ambas versiones existen.
    CONSTRAINT ck_card_available_requires_ready CHECK (NOT available OR image_status = 'READY')
);

-- La evolución apunta a una carta existente (EvolutionRules valida autoevolución y ciclos).
ALTER TABLE ability
    ADD CONSTRAINT fk_ability_evolves_to_card FOREIGN KEY (evolves_to_card_code) REFERENCES card (code);

-- Índices de búsqueda (sección 6). El de code ya lo crea uq_card_code.
CREATE INDEX idx_card_type_code ON card (type_code);
CREATE INDEX idx_card_rarity_code ON card (rarity_code);
CREATE INDEX idx_card_ability_code ON card (ability_code);
CREATE INDEX idx_card_cost ON card (cost);
CREATE INDEX idx_card_power ON card (power);
CREATE INDEX idx_card_name_normalized_trgm ON card USING gin (name_normalized gin_trgm_ops);

-- -----------------------------------------------------------------------------
-- Ubicaciones (historia 2.2.1).
-- -----------------------------------------------------------------------------
CREATE TABLE location (
    id                  BIGSERIAL   NOT NULL,
    name                TEXT        NOT NULL,
    description         TEXT        NOT NULL,
    effect_type         TEXT,
    effect_parameters   JSONB       NOT NULL DEFAULT '{}'::jsonb,
    card_limit          INTEGER     NOT NULL,
    extra_energy_mode   TEXT        NOT NULL,
    extra_energy_amount INTEGER     NOT NULL,
    image_large_url     TEXT        NOT NULL,
    image_small_url     TEXT,
    image_status        TEXT        NOT NULL,
    retry_count         INTEGER     NOT NULL DEFAULT 0,
    available           BOOLEAN     NOT NULL,
    version             INTEGER     NOT NULL DEFAULT 1,
    active              BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_location PRIMARY KEY (id),
    CONSTRAINT ck_location_name_not_blank CHECK (btrim(name) <> ''),
    CONSTRAINT ck_location_description_not_blank CHECK (btrim(description) <> ''),
    -- effect_type no tiene lista fija: los efectos son plug-ins y los valida
    -- LocationEffectRegistry, así un efecto nuevo no exige una migración.
    CONSTRAINT ck_location_effect_parameters_object CHECK (jsonb_typeof(effect_parameters) = 'object'),
    CONSTRAINT ck_location_no_effect_no_parameters CHECK (effect_type IS NOT NULL OR effect_parameters = '{}'::jsonb),
    CONSTRAINT ck_location_card_limit_range CHECK (card_limit BETWEEN 0 AND 5),
    -- Energía extra según su modalidad (sección 7.2).
    CONSTRAINT ck_location_extra_energy CHECK (
        (extra_energy_mode = 'NONE' AND extra_energy_amount = 0)
        OR (extra_energy_mode = 'FIXED' AND extra_energy_amount BETWEEN 0 AND 3)
        OR (extra_energy_mode = 'PER_CARD' AND extra_energy_amount BETWEEN 0 AND 2)
    ),
    -- FAILED: se agotaron los reintentos de la miniatura; la ubicación sigue no disponible.
    CONSTRAINT ck_location_image_status CHECK (image_status IN ('READY', 'PENDING_RETRY', 'FAILED')),
    CONSTRAINT ck_location_retry_count_non_negative CHECK (retry_count >= 0),
    CONSTRAINT ck_location_version_positive CHECK (version >= 1),
    CONSTRAINT ck_location_ready_has_small_image CHECK (image_status <> 'READY' OR image_small_url IS NOT NULL),
    -- Criterio C.2: solo se asigna a partidas cuando ambas versiones existen.
    CONSTRAINT ck_location_available_requires_ready CHECK (NOT available OR image_status = 'READY')
);

-- -----------------------------------------------------------------------------
-- Jugadores e identidades (historia 1.1.1). La identidad es (provider,
-- provider_subject), nunca el correo; un segundo proveedor solo agrega filas.
-- -----------------------------------------------------------------------------
CREATE TABLE player (
    id                        UUID        NOT NULL DEFAULT gen_random_uuid(),
    display_name              TEXT,
    email                     TEXT,
    role                      TEXT        NOT NULL,
    elo                       INTEGER     NOT NULL DEFAULT 0,
    cube_balance              BIGINT      NOT NULL DEFAULT 0,
    initial_collection_status TEXT        NOT NULL DEFAULT 'PENDING',
    created_at                TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_player PRIMARY KEY (id),
    CONSTRAINT ck_player_role CHECK (role IN ('PLAYER', 'ADMIN')),
    CONSTRAINT ck_player_cube_balance_non_negative CHECK (cube_balance >= 0),
    CONSTRAINT ck_player_initial_collection_status CHECK (initial_collection_status IN ('PENDING', 'DELIVERED'))
);

-- La clave primaria es la restricción única (provider, provider_subject): es lo que
-- garantiza una sola cuenta aunque dos inicios de sesión lleguen a la vez.
CREATE TABLE player_identity (
    provider         TEXT NOT NULL,
    provider_subject TEXT NOT NULL,
    player_id        UUID NOT NULL,
    CONSTRAINT pk_player_identity PRIMARY KEY (provider, provider_subject),
    CONSTRAINT fk_player_identity_player FOREIGN KEY (player_id) REFERENCES player (id)
);

-- Colección del jugador (historia 3.1.1). La clave primaria empieza por player_id,
-- así que su índice ya sirve para buscar por jugador (índice de la sección 6).
CREATE TABLE player_card (
    player_id UUID    NOT NULL,
    card_code TEXT    NOT NULL,
    copies    INTEGER NOT NULL,
    CONSTRAINT pk_player_card PRIMARY KEY (player_id, card_code),
    CONSTRAINT fk_player_card_player FOREIGN KEY (player_id) REFERENCES player (id),
    CONSTRAINT fk_player_card_card FOREIGN KEY (card_code) REFERENCES card (code),
    CONSTRAINT ck_player_card_copies_positive CHECK (copies > 0)
);

-- Historial de cubos. Invariante: player.cube_balance = suma de amount del jugador.
-- reason no tiene lista fija porque la especificación solo define INITIAL_GRANT por ahora.
CREATE TABLE cube_movement (
    id         BIGSERIAL   NOT NULL,
    player_id  UUID        NOT NULL,
    amount     BIGINT      NOT NULL,
    reason     TEXT        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_cube_movement PRIMARY KEY (id),
    CONSTRAINT fk_cube_movement_player FOREIGN KEY (player_id) REFERENCES player (id)
);

-- -----------------------------------------------------------------------------
-- Idempotencia de los POST de creación (sección 5.4). La clave primaria es la
-- unicidad (key, user_id, endpoint). user_id es el sub del token (id del jugador);
-- apunta a player porque todas las credenciales, incluidas las de dev/sesiones,
-- pertenecen a un jugador real.
-- -----------------------------------------------------------------------------
CREATE TABLE idempotency_key (
    key             TEXT        NOT NULL,
    user_id         UUID        NOT NULL,
    endpoint        TEXT        NOT NULL,
    request_hash    TEXT        NOT NULL,
    response_status INTEGER     NOT NULL,
    response_body   JSONB       NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_idempotency_key PRIMARY KEY (key, user_id, endpoint),
    CONSTRAINT fk_idempotency_key_player FOREIGN KEY (user_id) REFERENCES player (id)
);

-- =============================================================================
-- Datos semilla (sección 14).
-- =============================================================================
INSERT INTO card_type (code, name) VALUES
    ('FUE', 'Fuego'),
    ('AGU', 'Agua'),
    ('PLA', 'Planta'),
    ('ELE', 'Eléctrico'),
    ('PSI', 'Psíquico'),
    ('LUC', 'Lucha');

INSERT INTO rarity (code, name) VALUES
    ('COMMON', 'Común'),
    ('UNCOMMON', 'Poco común'),
    ('RARE', 'Rara'),
    ('LEGENDARY', 'Legendaria');

-- Un consecutivo en 0 por cada tipo: la primera carta de un tipo recibe el 001.
INSERT INTO card_type_counter (type_code, last_number)
SELECT code, 0 FROM card_type;

-- Habilidad obligatoria "Sin Habilidad": sin disparador, sin efecto y sin parámetros.
-- Su created_at es el momento en que se aplica la migración (no hay reloj de aplicación).
INSERT INTO ability (code, name, trigger, effect_type, parameters, limited_to_type, evolves_to_card_code,
                     version, active, created_at)
VALUES ('HAB-000', 'Sin Habilidad', NULL, NULL, '{}'::jsonb, NULL, NULL, 1, TRUE, now());
