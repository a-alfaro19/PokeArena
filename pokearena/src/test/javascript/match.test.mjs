/*
 * Propósito: verificar el estado y la consulta de la pantalla de inicio de partida (5.1.1).
 * Responsabilidades: cubrir la ruta de entrada, la solicitud autenticada, los errores, las respuestas
 * obsoletas y el contador basado en la hora del servidor sin requerir un navegador.
 * Ejecución: node --test src/test/javascript/match.test.mjs
 */
import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';

const source = await readFile(new URL('../../main/resources/static/match.js', import.meta.url), 'utf8');
const { MatchViewer, matchPath, remainingTurnSeconds } = await import('data:text/javascript;base64,'
  + Buffer.from(source).toString('base64'));

function harness({ token = 'token-propio', now = () => 10_000 } = {}) {
  const requests = [];
  const responses = [];
  const views = [];
  const viewer = new MatchViewer({
    send: async (request) => { requests.push(request); return responses.shift(); },
    getSession: () => ({ token }),
    render: (view) => views.push(structuredClone(view)),
    now,
  });
  return { viewer, requests, responses, views };
}

test('la ruta de consulta codifica el identificador de partida', () => {
  assert.equal(matchPath('a/b'), '/api/v1/partidas/a%2Fb');
});

test('el contador usa la hora del servidor y no muestra tiempo negativo', () => {
  assert.equal(remainingTurnSeconds('2025-01-01T00:01:00Z', '2025-01-01T00:00:00Z', 10_000, 20_000), 50);
  assert.equal(remainingTurnSeconds('2025-01-01T00:00:00Z', '2025-01-01T00:00:00Z', 10_000, 20_000), 0);
  assert.equal(remainingTurnSeconds('no-es-fecha', '2025-01-01T00:00:00Z', 10_000, 20_000), null);
});

test('carga el inicio con el token de la sesión y actualiza el contador', async () => {
  let now = 10_000;
  const h = harness({ now: () => now });
  h.responses.push({ status: 200, body: { id: 'partida-1', turnEndsAt: '2025-01-01T00:01:00Z',
    serverTime: '2025-01-01T00:00:00Z' } });

  const result = await h.viewer.open('partida-1');

  assert.equal(result.status, 200);
  assert.deepEqual({ method: h.requests[0].method, path: h.requests[0].path, token: h.requests[0].token },
    { method: 'GET', path: '/api/v1/partidas/partida-1', token: 'token-propio' });
  assert.equal(h.viewer.view.secondsRemaining, 60);
  now += 1_000;
  h.viewer.tick();
  assert.equal(h.viewer.view.secondsRemaining, 59);
});

test('sin sesión no consulta el endpoint y muestra que debe autenticarse', async () => {
  const h = harness({ token: null });

  const result = await h.viewer.open('partida-1');

  assert.equal(result, null);
  assert.equal(h.requests.length, 0);
  assert.match(h.viewer.view.message, /Inicia sesión/);
});

test('un 404 del servidor se presenta como error de partida', async () => {
  const h = harness();
  h.responses.push({ status: 404, body: { type: 'MATCH_NOT_FOUND', detail: 'Partida no encontrada.' } });

  await h.viewer.open('inexistente');

  assert.equal(h.viewer.view.error, true);
  assert.equal(h.viewer.view.message,
    'No se pudo cargar el inicio de la partida: 404 MATCH_NOT_FOUND: Partida no encontrada.');
});

test('una respuesta de una partida anterior no reemplaza la vista actual', async () => {
  let resolveFirst;
  const requests = [];
  const viewer = new MatchViewer({ getSession: () => ({ token: 'token-propio' }), render: () => {},
    send: (request) => {
      requests.push(request);
      if (requests.length === 1) return new Promise((resolve) => { resolveFirst = resolve; });
      return Promise.resolve({ status: 200, body: { id: 'nueva' } });
    } });
  const first = viewer.open('anterior');
  await viewer.open('nueva');
  resolveFirst({ status: 200, body: { id: 'anterior' } });
  await first;

  assert.equal(viewer.view.match.id, 'nueva');
});
