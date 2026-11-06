-- =============================================================================
-- Propósito: ajustes del esquema para la historia 2.1.1 "Crear carta": que una
-- habilidad de evolución pueda apuntar a una carta que todavía no existe, y que el
-- consecutivo del código de carta no tenga máximo.
-- Responsabilidades: quitar la clave foránea ability.evolves_to_card_code -> card.code
-- y cambiar el CHECK del formato del código de carta.
-- Referencia: sección 7.1 ("EvolutionRules") y criterios de C.1 sobre evoluciones.
--
-- Motivo: la regla "una carta no puede evolucionar a sí misma" se aplica al crear la
-- carta, comparando el destino de la habilidad con el código que la carta va a recibir.
-- Ese destino es, por definición, un código que aún no existe (por ejemplo, el próximo
-- PKA-FUE-001), y la clave foránea de V1 impedía guardar una habilidad así. Las reglas
-- de evolución (autoevolución y ciclos) las garantiza EvolutionRules en el dominio.
--
-- Se hace en una migración nueva y no editando V1: V1 ya está en main y puede estar
-- aplicada en bases locales; cambiarla alteraría su checksum y Flyway las rechazaría.
-- =============================================================================

ALTER TABLE ability DROP CONSTRAINT fk_ability_evolves_to_card;

-- -----------------------------------------------------------------------------
-- Código de carta con MÍNIMO 3 dígitos y sin máximo (decisión del equipo).
-- V1 exigía exactamente 3 dígitos (001 a 999): con 6 tipos solo cabrían 5.994 cartas,
-- y el catálogo puede llegar a unas 10.000. Ahora el consecutivo se rellena con ceros
-- hasta 3 dígitos y sigue creciendo: PKA-FUE-001 ... PKA-FUE-999, PKA-FUE-1000.
-- Además, el tipo del código debe ser uno de los 6 del catálogo.
-- -----------------------------------------------------------------------------
ALTER TABLE card DROP CONSTRAINT ck_card_code_format;
ALTER TABLE card ADD CONSTRAINT ck_card_code_format
    CHECK (code ~ '^PKA-(FUE|AGU|PLA|ELE|PSI|LUC)-[0-9]{3,}$');
