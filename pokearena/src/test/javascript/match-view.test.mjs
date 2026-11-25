import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';

const source = await readFile(new URL('../../main/resources/static/match-view.js', import.meta.url), 'utf8');
const { MatchViewScreen, matchViewPath } = await import('data:text/javascript;base64,' + Buffer.from(source).toString('base64'));

const matchId = '00000000-0000-4000-8000-000000000002';
const card = { code: 'PKA-FUE-001', name: 'Carta visible', cost: 2, power: 3, imageSmallUrl: '/images/card.webp' };
const match = (fields = {}) => ({
  id: matchId,
  status: 'ACTIVE',
  context: 'RANKED',
  vsBot: false,
  turn: 1,
  turnDurationSeconds: 30,
  turnEndsAt: '2026-11-25T04:00:30Z',
  serverTime: '2026-11-25T04:00:00Z',
  bet: 4,
  locations: [{ slot: 1, id: 8, name: 'Ubicación', description: 'Visible', cardLimit: 3,
    extraEnergyMode: 'FIXED', extraEnergyAmount: 1, imageLargeUrl: '/images/location.webp' }],
  you: { seat: 1, energy: 3, hand: [card], deckRemaining: 8, graveyard: [] },
  rival: { isBot: false, handCount: 3, deckRemaining: 9, graveyard: [] },
  ...fields,
});
const response = (status, body) => ({ status, body, ok: status >= 200 && status < 300 });

function fixture(...results) {
  const requests = [];
  const views = [];
  const timers = new Map();
  const session = { token: 'token-del-jugador' };
  let time = 10_000;
  const screen = new MatchViewScreen({
    send: async (request) => {
      requests.push(request);
      assert.ok(results.length, 'solicitud inesperada');
      return results.shift();
    },
    getSession: () => session,
    render: (view) => views.push({ ...view }),
    now: () => time,
    schedule(callback, interval) { timers.set(callback, interval); return callback; },
    unschedule(callback) { timers.delete(callback); },
  });
  return { screen, requests, views, timers, session, advance: (milliseconds) => { time += milliseconds; } };
}

test('C.10: consulta la partida asignada con sesión y calcula el tiempo restante desde el servidor', async () => {
  const current = match();
  const f = fixture(response(200, {
    ...current,
    seed: 'no-debe-llegar-a-la-vista',
    you: { ...current.you, deckOrder: ['privado'], hand: [{ ...card, power: -2 }] },
    rival: { ...current.rival, hand: [card], deckOrder: ['privado'] },
  }));
  await f.screen.open(matchId);

  assert.equal(f.requests[0].method, 'GET');
  assert.equal(f.requests[0].path, matchViewPath(matchId));
  assert.equal(f.requests[0].token, 'token-del-jugador');
  assert.equal(f.screen.data.you.hand[0].code, card.code);
  assert.equal(f.screen.data.you.hand[0].power, -2);
  assert.equal(f.screen.data.rival.handCount, 3);
  assert.equal(Object.hasOwn(f.screen.data, 'seed'), false);
  assert.equal(Object.hasOwn(f.screen.data.you, 'deckOrder'), false);
  assert.equal(Object.hasOwn(f.screen.data.rival, 'hand'), false);
  assert.equal(Object.hasOwn(f.screen.data.rival, 'deckOrder'), false);
  assert.equal(f.screen.remainingSeconds, 30);
  assert.equal(f.timers.size, 1);
});

test('C.10: el contador se actualiza y se detiene al terminar el turno', async () => {
  const f = fixture(response(200, match()));
  await f.screen.open(matchId);
  const tick = [...f.timers.keys()][0];

  f.advance(1_200);
  tick();
  assert.equal(f.screen.remainingSeconds, 29);
  f.advance(30_000);
  tick();
  assert.equal(f.screen.remainingSeconds, 0);
  assert.equal(f.timers.size, 0);
});

test('C.10: rechaza identificadores inválidos y muestra el error uniforme de la API', async () => {
  const f = fixture(response(404, { type: 'MATCH_NOT_FOUND', detail: 'La partida no existe.' }));
  await f.screen.open('no-es-un-uuid');
  assert.equal(f.requests.length, 0);

  await f.screen.open(matchId);
  assert.equal(f.screen.data, null);
  assert.equal(f.screen.message, 'La partida no existe.');
  assert.equal(f.timers.size, 0);
});

test('C.10: ignora respuestas pendientes de una sesión anterior', async () => {
  let finish;
  const pending = new Promise((resolve) => { finish = resolve; });
  const f = fixture(pending);
  const request = f.screen.open(matchId);
  f.session.token = 'token-de-otro-jugador';
  f.screen.reset();
  finish(response(200, match()));
  await request;

  assert.equal(f.screen.data, null);
  assert.equal(f.screen.busy, false);
  assert.equal(f.timers.size, 0);
});

test('C.10: una respuesta exitosa con forma inválida se informa sin mostrar datos parciales', async () => {
  const f = fixture(response(200, { id: matchId, you: { hand: [] } }));
  await f.screen.open(matchId);

  assert.equal(f.screen.data, null);
  assert.match(f.screen.message, /formato esperado/);
});
