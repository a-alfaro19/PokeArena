import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';

const source = await readFile(new URL('../../main/resources/static/match-search.js', import.meta.url));
const { MatchSearchScreen, MatchSearchStarter } = await import('data:text/javascript;base64,' + source.toString('base64'));
const id = '00000000-0000-4000-8000-000000000001';
const matchId = '00000000-0000-4000-8000-000000000002';
const ok = (status, fields = {}) => ({ ok: true, body: { id, status, pollIntervalSeconds: 7,
  matchId: status === 'MATCHED' ? matchId : null, botOfferAvailable: false, ...fields } });
const conflict = (type) => ({ ok: false, status: 409, body: { type, detail: type } });

function starterFixture(...responses) {
  // Controlar sesión y transporte permite probar reintentos sin arrancar la aplicación.
  const sent = [], opened = [], session = { token: 'jwt' };
  let sequence = 0;
  const starter = new MatchSearchStarter({
    send(request) {
      sent.push(request);
      assert.ok(responses.length, 'solicitud inesperada');
      return responses.shift();
    },
    getSession: () => session,
    newKey: () => 'clave-' + (++sequence),
    render() {},
    openSearch: searchId => opened.push(searchId),
  });
  return { starter, sent, opened, session };
}

test('C.8-E2: 201 y 200 abren el estado con el UUID devuelto', async () => {
  // Verificar el contrato para un alta nueva y para una búsqueda equivalente.
  for (const status of [201, 200]) {
    const f = starterFixture({ status, body: { id } });
    await f.starter.start('27');
    assert.deepEqual(f.opened, [id]);
    assert.equal(f.sent[0].path, '/api/v1/busquedas');
    assert.equal(f.sent[0].token, 'jwt');
    assert.deepEqual(f.sent[0].body, { deckId: 27, context: 'RANKED' });
  }
});

test('C.8-A1: el doble clic se ignora y un fallo de red conserva la clave', async () => {
  // Mantener la primera solicitud pendiente hasta después del segundo clic.
  let finish;
  const f = starterFixture(new Promise(resolve => { finish = resolve; }), { status: 201, body: { id } });
  const first = f.starter.start('27');
  await f.starter.start('27');
  assert.equal(f.sent.length, 1);
  finish(null);
  await first;
  assert.match(f.starter.message, /volver a intentarlo/);
  await f.starter.start('27');
  assert.equal(f.sent[1].idempotencyKey, f.sent[0].idempotencyKey);
  assert.deepEqual(f.opened, [id]);
});

test('C.8-R4: el saldo se muestra tal como lo informa el servidor y cambiar de deck cambia la clave', async () => {
  // El cliente presenta el detalle sin inferir el mínimo de cubos.
  const detail = 'El saldo no alcanza para buscar partida.';
  const f = starterFixture({ status: 422, body: { type: 'INSUFFICIENT_CUBES', detail } }, null);
  await f.starter.start('27');
  assert.equal(f.starter.message, detail);
  f.starter.select('28');
  await f.starter.start('28');
  assert.notEqual(f.sent[0].idempotencyKey, f.sent[1].idempotencyKey);
  assert.deepEqual(f.opened, []);
});

test('Un POST pendiente de otra sesión no cambia la vista actual', async () => {
  // Aunque el servidor confirme, solo la sesión que lanzó el POST puede abrir esa búsqueda.
  let finish;
  const f = starterFixture(new Promise(resolve => { finish = resolve; }));
  const first = f.starter.start('27');
  f.session.token = 'otro-jugador';
  f.starter.reset();
  finish({ status: 201, body: { id } });
  await first;
  assert.deepEqual(f.opened, []);
  assert.equal(f.starter.busy, false);
});

test('C.8-R1/R2/A1: los casos rápidos comprueban los errores y el alta duplicada', async () => {
  // Los casos rápidos envían a la API los cuerpos válidos o inválidos que necesitan comprobar.
  const f = starterFixture({ status: 400, body: { type: 'MISSING_FIELD' } },
    { status: 404, body: { type: 'DECK_NOT_FOUND' } }, { status: 201, body: { id } },
    { status: 200, body: { id } });
  await f.starter.start('');
  assert.equal(f.sent.length, 0);
  await f.starter.start('', 'missing');
  await f.starter.start('28', 'foreign');
  assert.deepEqual(f.sent[0].body, { context: 'RANKED' });
  assert.equal(f.sent[1].body.deckId, 28);
  assert.deepEqual(f.opened, []);
  await f.starter.start('27', 'twice');
  assert.equal(f.sent[3].expected.status, 200);
  assert.notEqual(f.sent[2].idempotencyKey, f.sent[3].idempotencyKey);
  assert.deepEqual(f.opened, [id]);
});

function fixture(...responses) {
  const requests = [], matches = [], timers = new Map();
  let sequence = 0;
  const screen = new MatchSearchScreen({
    send(request) {
      requests.push(request);
      assert.ok(responses.length, 'solicitud inesperada');
      return responses.shift();
    },
    render() {}, openMatch: (id) => matches.push(id), newKey: () => 'clave-' + (++sequence),
    schedule(callback, ms) { timers.set(callback, ms); return callback; },
    unschedule(callback) { timers.delete(callback); },
  });
  return { screen, requests, matches, timers, tick: () => [...timers.keys()][0]() };
}

test('C.9-E1/E3: ignorar la oferta mantiene el sondeo al intervalo recibido hasta MATCHED', async () => {
  const f = fixture(ok('WAITING', { notice: 'LOCATIONS_UNAVAILABLE' }),
    ok('WAITING', { botOfferAvailable: true }), ok('MATCHED'));
  await f.screen.open(id);
  assert.equal(f.screen.data.notice, 'LOCATIONS_UNAVAILABLE');
  assert.deepEqual([...f.timers.values()], [7000]);
  await f.tick();
  assert.equal(f.screen.data.botOfferAvailable, true);
  await f.tick();
  assert.deepEqual(f.matches, [matchId]);
  assert.equal(f.timers.size, 0);
  assert.deepEqual(f.requests.map(r => r.method), ['GET', 'GET', 'GET']);
});

test('A.9: la consulta tras el alta puede mantener el POST en el panel de respuesta', async () => {
  // La navegación automática consulta el estado sin reemplazar la respuesta del alta.
  const f = fixture(ok('WAITING'));
  await f.screen.open(id, true);
  assert.equal(f.requests[0].silent, true);
  assert.equal(f.screen.data.status, 'WAITING');
});

test('C.9-E2/A1: un GET anterior al DELETE no reabre la búsqueda cancelada', async () => {
  let finish;
  const f = fixture(ok('WAITING'), new Promise(r => { finish = r; }), ok('CANCELLED'));
  await f.screen.open(id);
  const oldPoll = f.tick();
  await f.screen.cancel();
  assert.equal(f.requests[2].idempotencyKey, undefined);
  finish(ok('WAITING'));
  await oldPoll;
  assert.equal(f.screen.data.status, 'CANCELLED');
  assert.equal(f.timers.size, 0);
});

test('C.9-E3: doble clic y reintento tras error de red conservan una clave', async () => {
  let fail;
  const f = fixture(ok('WAITING', { botOfferAvailable: true }), new Promise(r => { fail = r; }), ok('MATCHED'));
  await f.screen.open(id);
  const first = f.screen.accept();
  await f.screen.accept();
  assert.equal(f.requests.length, 2);
  fail(null);
  await first;
  await f.screen.accept();
  const posts = f.requests.slice(1);
  assert.ok(posts[0].idempotencyKey);
  assert.equal(posts[0].idempotencyKey, posts[1].idempotencyKey);
  assert.equal(posts[1].path, '/api/v1/busquedas/' + id + '/bot');
  assert.deepEqual(f.matches, [matchId]);
});

test('C.9-R2: el conflicto de salida recupera el matchId con una consulta', async () => {
  const f = fixture(ok('WAITING'), conflict('SEARCH_ALREADY_MATCHED'), ok('MATCHED'));
  await f.screen.open(id);
  await f.screen.cancel();
  assert.deepEqual(f.requests.map(r => r.method), ['GET', 'DELETE', 'GET']);
  assert.deepEqual(f.matches, [matchId]);
});

test('C.9-R1: al volver a la pantalla después de la inactividad se muestra EXPIRED', async () => {
  const f = fixture(ok('WAITING'), ok('EXPIRED'));
  await f.screen.open(id);
  f.screen.setVisible(false);
  assert.equal(f.timers.size, 0);
  await f.screen.setVisible(true);
  assert.equal(f.screen.data.status, 'EXPIRED');
  assert.equal(f.timers.size, 0);
});

test('Una respuesta de la sesión anterior se descarta al cerrar sesión', async () => {
  let finish;
  const f = fixture(new Promise(r => { finish = r; }));
  const old = f.screen.open(id);
  f.screen.reset();
  finish(ok('MATCHED'));
  await old;
  assert.equal(f.screen.data, null);
  assert.deepEqual(f.matches, []);
});

test('Casos rápidos envían las operaciones y exponen los conflictos esperados', async () => {
  const f = fixture(ok('WAITING'), conflict('BOT_OFFER_NOT_AVAILABLE'), ok('MATCHED'),
    conflict('SEARCH_ALREADY_MATCHED'), ok('MATCHED'));
  await f.screen.open(id);
  await f.screen.accept();
  assert.equal(f.requests.length, 1);
  await f.screen.accept(true);
  assert.equal(f.requests[1].expected.type, 'BOT_OFFER_NOT_AVAILABLE');
  await f.tick();
  await f.screen.cancel(true);
  assert.equal(f.requests[3].expected.type, 'SEARCH_ALREADY_MATCHED');
});

test('Una búsqueda ajena limpia el estado anterior y detiene el sondeo', async () => {
  const f = fixture(ok('WAITING'), { status: 404, body: { detail: 'La búsqueda no existe.' } });
  await f.screen.open(id);
  await f.screen.open(matchId);
  assert.equal(f.screen.data, null);
  assert.equal(f.screen.message, 'La búsqueda no existe.');
  assert.equal(f.timers.size, 0);
});

test('Arranque y sondeo: los temporizadores del navegador conservan su receptor global', async (t) => {
  const handle = 41;
  const cleared = [];
  t.mock.method(globalThis, 'clearTimeout', function (timer) {
    assert.equal(this === globalThis, true, 'clearTimeout requiere el receptor del navegador');
    cleared.push(timer);
  });
  t.mock.method(globalThis, 'setTimeout', function (callback, ms) {
    assert.equal(this === globalThis, true, 'setTimeout requiere el receptor del navegador');
    assert.equal(ms, 7000);
    return handle;
  });
  const screen = new MatchSearchScreen({ send: async () => ok('WAITING'), render() {} });
  assert.equal(screen.data, null);
  await screen.open(id);
  screen.reset();
  assert.equal(cleared.at(-1), handle);
});
