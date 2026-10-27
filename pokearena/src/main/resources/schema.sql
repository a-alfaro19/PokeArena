-- 1. Tabla de secuencias para los códigos autoincrementables por tipo (PKA-TIPO-NNN)
CREATE TABLE IF NOT EXISTS card_sequences (
    card_type VARCHAR(10) PRIMARY KEY,
    last_sequence INT NOT NULL DEFAULT 0
);

-- Insertar los tipos iniciales para que el generador de códigos funcione
INSERT INTO card_sequences (card_type, last_sequence) VALUES 
('FUE', 0), ('AGU', 0), ('PLA', 0), ('ELE', 0), ('PSI', 0), ('LUC', 0)
ON CONFLICT (card_type) DO NOTHING;

-- 2. Tabla de habilidades válidas (necesaria para validar el abilityCode)
CREATE TABLE IF NOT EXISTS abilities (
    code VARCHAR(50) PRIMARY KEY,
    name VARCHAR(100) NOT NULL
);

-- Insertar una habilidad de prueba (ej: HAB-000)
INSERT INTO abilities (code, name) VALUES ('HAB-000', 'Habilidad Base')
ON CONFLICT (code) DO NOTHING;

-- 3. Tabla principal de cartas (Cards)
CREATE TABLE IF NOT EXISTS cards (
    code VARCHAR(50) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    cost INT NOT NULL,
    power INT NOT NULL,
    type VARCHAR(10) NOT NULL,
    rarity VARCHAR(20) NOT NULL,
    ability_code VARCHAR(50) REFERENCES abilities(code),
    large_image_url TEXT NOT NULL,
    small_image_url TEXT NOT NULL,
    image_status VARCHAR(20) NOT NULL,
    idempotency_key VARCHAR(100) UNIQUE,
    created_at TIMESTAMP NOT NULL
);
