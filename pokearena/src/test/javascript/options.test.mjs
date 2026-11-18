/*
 * Propósito: verificar la lectura de las opciones de la API en la interfaz (GET /api/v1/opciones/catalogo y
 * GET /api/v1/opciones/creacion; Anexos A.1, A.2, A.3 y A.6), sin navegador ni backend.
 * Responsabilidades: forma de las respuestas, error claro sin valores de respaldo, textos de rangos y de
 * requisitos de imagen, energía extra y JSON del efecto armado con la descripción de parámetros.
 * Ejecución: node --test src/test/javascript/options.test.mjs
 */
import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';

// Importa exactamente el módulo servido al navegador sin exigir package.json ni dependencias.
const source = await readFile(new URL('../../main/resources/static/options.js', import.meta.url), 'utf8');
const { OPTIONS_PATHS, catalogOptionsFrom, creationOptionsFrom, optionsErrorMessage, rangeText, imageRequirementText,
  acceptedTypes, energyAmountState, integersBetween, effectJson } = await import('data:text/javascript;base64,' + Buffer.from(source).toString('base64'));

// Respuestas de ejemplo con la forma del contrato. Los números son datos de la prueba, no de la interfaz.
const catalogBody = () => ({
  types: [{ code: 'AGU', name: 'Agua' }], rarities: [{ code: 'COMMON', name: 'Común' }],
  cost: { min: 0, max: 10 }, pagination: { defaultLimit: 20, minLimit: 1, maxLimit: 100 },
});
const creationBody = () => ({
  abilities: [{ code: 'HAB-000', name: 'Sin Habilidad', limitedToType: null, trigger: null, effect: null, parameters: {} }],
  card: { cost: { min: 0, max: 10 }, power: { min: -20, max: 20 } },
  location: {
    cardLimit: { min: 0, max: 5 },
    extraEnergy: { defaultMode: 'NONE', modes: [
      { code: 'NONE', name: 'Sin energía extra', minAmount: 0, maxAmount: 0 },
      { code: 'FIXED', name: 'Fija por turno', minAmount: 0, maxAmount: 3 }] },
    effects: [
      { code: 'TURN_RESTRICTION', name: 'Restricción por turno', parameters: [
        { name: 'turns', label: 'Turnos', kind: 'INTEGER_SET', required: true, min: 1, max: 6 }] },
      { code: 'TYPE_BONUS', name: 'Bonificación por tipo', parameters: [
        { name: 'type', label: 'Tipo', kind: 'CARD_TYPE', required: true, min: null, max: null },
        { name: 'amount', label: 'Monto', kind: 'INTEGER', required: true, min: -5, max: 5 }] }],
  },
  images: { formats: ['PNG', 'JPEG', 'WEBP'], maxFileBytes: 5 * 1024 * 1024,
    card: { ratioWidth: 5, ratioHeight: 7, minWidth: 600, minHeight: 840 },
    location: { ratioWidth: 4, ratioHeight: 3, minWidth: 800, minHeight: 600 } },
});

test('opciones: una respuesta 200 con la forma del contrato se acepta tal cual', () => {
  assert.deepEqual(catalogOptionsFrom({ status: 200, body: catalogBody() }), catalogBody());
  assert.deepEqual(creationOptionsFrom({ status: 200, body: creationBody() }), creationBody());
});

test('opciones: un error HTTP, sin conexión o una forma inesperada no producen opciones (sin valores de respaldo)', () => {
  assert.equal(catalogOptionsFrom(null), null);
  assert.equal(catalogOptionsFrom({ status: 403, body: { type: 'FORBIDDEN' } }), null);
  assert.equal(catalogOptionsFrom({ status: 200, body: { ...catalogBody(), types: [] } }), null);
  assert.equal(creationOptionsFrom({ status: 200, body: { ...creationBody(), abilities: undefined } }), null);
  const badDefault = creationBody();
  badDefault.location.extraEnergy.defaultMode = 'OTRA';
  assert.equal(creationOptionsFrom({ status: 200, body: badDefault }), null);
  const badParameter = creationBody();
  badParameter.location.effects[1].parameters[0].min = 0;
  assert.equal(creationOptionsFrom({ status: 200, body: badParameter }), null);
});

test('opciones: el error es claro, nombra la ruta y la causa, y avisa que el formulario queda deshabilitado', () => {
  const forbidden = optionsErrorMessage(OPTIONS_PATHS.creation,
    { status: 403, body: { type: 'FORBIDDEN', detail: 'No tiene permiso.' } });
  assert.match(forbidden, /GET \/api\/v1\/opciones\/creacion/);
  assert.match(forbidden, /HTTP 403 FORBIDDEN: No tiene permiso\./);
  assert.match(forbidden, /deshabilitado/);
  assert.match(optionsErrorMessage(OPTIONS_PATHS.catalog, null), /no se pudo conectar/);
  assert.match(optionsErrorMessage(OPTIONS_PATHS.catalog, { status: 200, body: {} }), /forma del contrato/);
});

test('opciones: rangos y requisitos de imagen se arman con los valores del servidor', () => {
  const images = creationBody().images;
  assert.equal(rangeText({ min: -20, max: 20 }), '-20 a 20');
  assert.equal(imageRequirementText(images, 'card'), 'PNG, JPEG o WEBP · 5:7 · mínimo 600 × 840 · máximo 5 MB');
  assert.equal(imageRequirementText({ ...images, maxFileBytes: 3 * 1024 * 1024 }, 'location'),
    'PNG, JPEG o WEBP · 4:3 · mínimo 800 × 600 · máximo 3 MB');
  assert.equal(acceptedTypes(images.formats), 'image/png,image/jpeg,image/webp');
});

test('opciones: una modalidad sin rango de monto (NONE) deshabilita el monto; las demás lo piden con su rango', () => {
  const [none, fixed] = creationBody().location.extraEnergy.modes;
  assert.deepEqual(energyAmountState(none), { enabled: false, hint: 'no aplica con NONE' });
  assert.deepEqual(energyAmountState(fixed), { enabled: true, hint: 'obligatorio, 0 a 3' });
});

test('opciones: el JSON del efecto sigue la descripción de parámetros; los vacíos se omiten', () => {
  const [turns, bonus] = creationBody().location.effects;
  assert.deepEqual(integersBetween(1, 6), [1, 2, 3, 4, 5, 6]);
  assert.deepEqual(effectJson(turns, () => ['1', '3']), { code: 'TURN_RESTRICTION', parameters: { turns: [1, 3] } });
  assert.deepEqual(effectJson(bonus, (parameter) => (parameter.name === 'type' ? 'AGU' : '-2')),
    { code: 'TYPE_BONUS', parameters: { type: 'AGU', amount: -2 } });
  assert.deepEqual(effectJson(bonus, () => ''), { code: 'TYPE_BONUS', parameters: {} });
  assert.equal(effectJson(undefined, () => ''), null);
});
