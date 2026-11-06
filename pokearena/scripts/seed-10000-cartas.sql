-- =============================================================================
-- Propósito: cargar 10.000 cartas válidas para las pruebas de rendimiento de la
-- búsqueda y del catálogo, y para la colección inicial (secciones 13 y 14).
-- Responsabilidades: insertar las cartas repartidas entre los 6 tipos, con rarezas
-- variadas (la mitad Comunes), costo y poder en todo su rango, y dejar el consecutivo
-- de cada tipo (card_type_counter) en su último número, para que la siguiente carta
-- creada por la API no repita un código.
-- Referencia: sección 14 ("scripts/seed-10000-cartas.sql") y sección 7.1 ("Código único").
--
-- Uso: psql -h localhost -U pokearena -d pokearena -f scripts/seed-10000-cartas.sql
-- La prueba CardSearchPerformanceIT lo ejecuta tal cual contra la base de pruebas.
--
-- Decisiones:
--   * NO es una migración: son datos de prueba, no van en producción (sección 14).
--   * Se puede ejecutar sobre una base que ya tiene cartas: cada tipo sigue numerando
--     desde su card_type_counter.last_number actual, así no choca con códigos existentes.
--   * Código PKA-<TIPO>-<NNN> con MÍNIMO 3 dígitos y sin máximo, igual que la
--     aplicación (CardCode): PKA-FUE-001 ... PKA-FUE-999, PKA-FUE-1000. Ojo:
--     lpad(n::text, 3, '0') no sirve sola, porque lpad CORTA los textos más largos
--     (lpad('1000', 3, '0') = '100'); por eso el largo es greatest(3, largo del número).
--   * Los nombres son ASCII, así name_normalized (minúsculas y sin tildes, que en la
--     aplicación calcula el dominio) es simplemente lower(name).
--   * Habilidad HAB-000 (Sin Habilidad), la única que existe en la semilla de V1.
--   * Todas quedan READY y disponibles. Las URLs de imagen son de ejemplo: no hay
--     archivos detrás (sirven para medir la consulta, no para mostrar imágenes).
--   * Al final, VACUUM ANALYZE (fuera de la transacción, porque VACUUM no puede ir
--     dentro de una):
--       - ANALYZE actualiza las estadísticas, para que el planificador elija los
--         índices con la cantidad real de filas;
--       - VACUUM pasa al índice trigram (GIN) las filas que quedaron en su "lista
--         pendiente". GIN acumula ahí las inserciones para hacerlas rápidas, y
--         mientras no se vacía el planificador lo considera caro y lee la tabla
--         completa. En una base en uso lo hace solo autovacuum; aquí se fuerza para
--         medir enseguida.
-- =============================================================================

BEGIN;

WITH parametros AS (
    -- Cantidad de cartas a crear.
    SELECT 10000 AS cantidad
),
tipo AS (
    -- Los tipos en orden fijo, con su posición (0 a 5) y su último consecutivo.
    SELECT t.code,
           (row_number() OVER (ORDER BY t.code) - 1)::int AS posicion,
           k.last_number
    FROM card_type t
    JOIN card_type_counter k ON k.type_code = t.code
),
cantidad_tipos AS (
    SELECT count(*)::int AS n FROM tipo
),
nueva AS (
    -- La carta i va al tipo (i - 1) mod 6 y recibe el siguiente número de ese tipo.
    SELECT s.i,
           tp.code AS type_code,
           tp.last_number + (s.i - 1) / ct.n + 1 AS numero
    FROM parametros p
    CROSS JOIN cantidad_tipos ct
    CROSS JOIN LATERAL generate_series(1, p.cantidad) AS s(i)
    JOIN tipo tp ON tp.posicion = (s.i - 1) % ct.n
)
INSERT INTO card (code, name, name_normalized, cost, power, type_code, rarity_code, ability_code,
                  image_large_url, image_small_url, image_status, available, created_at)
SELECT 'PKA-' || type_code || '-' || lpad(numero::text, greatest(3, length(numero::text)), '0'),
       'Carta de prueba ' || type_code || ' ' || numero,
       lower('Carta de prueba ' || type_code || ' ' || numero),
       i % 11,                                   -- costo de 0 a 10
       (i * 7) % 41 - 20,                        -- poder de -20 a 20
       type_code,
       CASE i % 10                               -- 50 % Comunes, 30 % Poco comunes, 10 % y 10 %
           WHEN 0 THEN 'COMMON' WHEN 1 THEN 'COMMON' WHEN 2 THEN 'COMMON'
           WHEN 3 THEN 'COMMON' WHEN 4 THEN 'COMMON'
           WHEN 5 THEN 'UNCOMMON' WHEN 6 THEN 'UNCOMMON' WHEN 7 THEN 'UNCOMMON'
           WHEN 8 THEN 'RARE'
           ELSE 'LEGENDARY'
       END,
       'HAB-000',
       '/images/semilla/large.png',
       '/images/semilla/small.png',
       'READY',
       TRUE,
       now()
FROM nueva
ORDER BY i;

-- El consecutivo de cada tipo queda en su número más alto, para que la API siga desde ahí.
UPDATE card_type_counter k
SET last_number = m.maximo
FROM (
    SELECT type_code, max(substring(code FROM '[0-9]+$')::int) AS maximo
    FROM card
    GROUP BY type_code
) m
WHERE k.type_code = m.type_code
  AND m.maximo > k.last_number;

COMMIT;

VACUUM ANALYZE card;
