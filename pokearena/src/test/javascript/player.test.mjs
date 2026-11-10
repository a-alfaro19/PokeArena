/*
 * Propósito: verificar comportamiento de la interfaz del jugador sin frameworks ni backend simulado como producción.
 * Responsabilidades: controlar respuestas y reloj para probar filtros, aislamiento, paginación y sesiones (US 43, 35 y 42).
 * Ejecución: node --test src/test/javascript/player.test.mjs
 */
import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';

// Importa exactamente el módulo servido al navegador sin exigir package.json ni dependencias.
const source = await readFile(new URL('../../main/resources/static/player.js', import.meta.url), 'utf8');
const { PlayerCatalog, catalogPath, collectionMessage, sessionFromResponse, responseForDisplay,
  mountGoogleButton, googleSessionRequest, CatalogFilterForm, catalogQuickTests } = await import('data:text/javascript;base64,' + Buffer.from(source).toString('base64'));
const card = (code, copies = 0) => ({ code, name: code, imageLargeUrl: '/images/large.png', owned: copies > 0, copies });
const page = (items, nextCursor = null) => ({ status: 200, body: { items, nextCursor, limit: 2 } });
const deferred = () => { let resolve; const promise = new Promise((done) => { resolve = done; }); return { promise, resolve }; };

/** Cada prueba tiene su propio reloj, sesión y cola de respuestas; nunca usa credenciales reales. */
function harness() {
  let session = { token: 'token-propio-A', expiresAt: 10000 };
  let clock = 1000;
  let expired = 0;
  const requests = [];
  const responses = [];
  const views = [];
  const catalog = new PlayerCatalog({
    getSession: () => session, now: () => clock,
    send: async (request) => { requests.push(request); return responses.shift(); },
    onExpired: () => { expired++; session = { token: null, expiresAt: 0 }; catalog.clear('Sesión vencida.'); },
    render: (view) => views.push(structuredClone(view)),
  });
  return { catalog, requests, responses, views, setSession: (value) => { session = value; },
    setClock: (value) => { clock = value; }, expired: () => expired };
}

test('US 43: combina tipo, rango y propias con rutas relativas; el cursor es opaco', () => {
  assert.equal(catalogPath({ type: 'FUE', costMin: '0', costMax: '7', owned: 'true' }, 'abc_-'),
    '/api/v1/catalogo?type=FUE&costMin=0&costMax=7&owned=true&cursor=abc_-');
  assert.equal(catalogPath({}), '/api/v1/catalogo');
});

test('US 43: muestra carga y conserva propiedad, copias e imagen grande del backend', async () => {
  const h = harness();
  h.responses.push(page([card('A', 3), card('B')]));
  await h.catalog.load({});
  assert.equal(h.views.at(-2).loading, true);
  assert.deepEqual(h.catalog.view.items, [card('A', 3), card('B')]);
  assert.equal(h.catalog.view.loading, false);
  assert.equal(h.requests[0].token, 'token-propio-A');
});

test('US 43: cargar más conserva filtros y añade la página sin repetir el inicio', async () => {
  const h = harness();
  h.responses.push(page([card('A')], 'MQ'), page([card('B')]));
  await h.catalog.load({ type: 'AGU', costMin: '2', owned: 'true' });
  await h.catalog.load({}, true);
  assert.equal(h.requests[1].path, '/api/v1/catalogo?type=AGU&costMin=2&owned=true&cursor=MQ');
  assert.deepEqual(h.catalog.view.items.map((item) => item.code), ['A', 'B']);
  assert.equal(h.catalog.view.nextCursor, null);
  await h.catalog.load({}, true);
  assert.equal(h.requests.length, 2);
});

test('US 43: aplicar nuevos filtros elimina resultados y cursor anteriores', async () => {
  const h = harness();
  h.responses.push(page([card('A')], 'MQ'), page([card('B')]));
  await h.catalog.load({ type: 'FUE' });
  await h.catalog.load({ type: 'AGU' });
  assert.equal(h.requests[1].path, '/api/v1/catalogo?type=AGU');
  assert.deepEqual(h.catalog.view.items.map((item) => item.code), ['B']);
});

test('US 43: lista vacía es un estado normal, también en solo mis cartas', async () => {
  const h = harness();
  h.responses.push(page([]));
  await h.catalog.load({ owned: 'true' });
  assert.equal(h.catalog.view.error, false);
  assert.match(h.catalog.view.message, /No hay cartas/);
  assert.equal(h.catalog.view.nextCursor, null);
});

test('US 43: comunica detalle y valores permitidos para filtros inválidos', async () => {
  const h = harness();
  h.responses.push({ status: 400, body: { type: 'INVALID_FIELD', detail: 'Tipo inválido.', allowedValues: ['FUE', 'AGU'] } });
  await h.catalog.load({ type: 'INVALID' });
  assert.match(h.catalog.view.message, /Tipo inválido.*FUE, AGU/);
  assert.equal(h.catalog.view.error, true);
});

test('US 43: error de paginación preserva cartas y cursor para reintentar', async () => {
  const h = harness();
  h.responses.push(page([card('A')], 'MQ'), { status: 400, body: { type: 'INVALID_PAGINATION', detail: 'Cursor inválido.' } }, page([card('B')]));
  await h.catalog.load({});
  await h.catalog.load({}, true);
  assert.deepEqual(h.catalog.view.items.map((item) => item.code), ['A']);
  assert.equal(h.catalog.view.nextCursor, 'MQ');
  await h.catalog.load({}, true);
  assert.equal(h.requests[2].path, h.requests[1].path);
  assert.deepEqual(h.catalog.view.items.map((item) => item.code), ['A', 'B']);
});

for (const response of [null, { status: 500, body: { detail: 'Fallo del servidor.' } },
  { status: 200, body: { items: [], nextCursor: 7 } }]) {
  test('US 43: maneja red, error HTTP o página inválida sin dejar carga activa: ' + JSON.stringify(response), async () => {
    const h = harness(); h.responses.push(response);
    await h.catalog.load({});
    assert.equal(h.catalog.view.loading, false);
    assert.equal(h.catalog.view.error, true);
  });
}

test('US 43: una excepción de transporte también libera el estado de carga', async () => {
  const h = harness(); h.catalog.send = async () => { throw new Error('desconectado'); };
  await h.catalog.load({});
  assert.equal(h.catalog.view.loading, false);
  assert.match(h.catalog.view.message, /conectar/);
});

test('US 43: no consulta sin sesión y limpia datos personales', async () => {
  const h = harness(); h.responses.push(page([card('A', 2)]));
  await h.catalog.load({});
  h.setSession({ token: null, expiresAt: 0 });
  await h.catalog.load({});
  assert.deepEqual(h.catalog.view.items, []);
  assert.equal(h.requests.length, 1);
});

test('US 35: vencimiento local y 401 borran cartas y piden nuevo ingreso', async () => {
  const h = harness(); h.setClock(10000);
  await h.catalog.load({});
  assert.equal(h.expired(), 1);
  assert.equal(h.requests.length, 0);
  const other = harness(); other.responses.push({ status: 401, body: { type: 'NOT_AUTHENTICATED' } });
  await other.catalog.load({});
  assert.equal(other.expired(), 1);
  assert.deepEqual(other.catalog.view.items, []);
});

test('US 35: una respuesta recibida después del vencimiento no vuelve a mostrar cartas', async () => {
  const h = harness(); const pending = deferred(); h.responses.push(pending.promise);
  const load = h.catalog.load({}); h.setClock(10000); pending.resolve(page([card('A', 1)]));
  await load;
  assert.equal(h.expired(), 1);
  assert.deepEqual(h.catalog.view.items, []);
});

test('US 43: cambiar o cerrar sesión descarta respuestas personales pendientes', async () => {
  const h = harness(); const pending = deferred(); h.responses.push(pending.promise);
  const load = h.catalog.load({});
  h.setSession({ token: 'token-propio-B', expiresAt: 10000 }); h.catalog.clear();
  assert.equal(h.requests[0].isCurrent(), false);
  pending.resolve(page([card('AJENA', 8)])); await load;
  assert.deepEqual(h.catalog.view.items, []);
});

test('US 43: consultas fuera de orden no reemplazan los filtros más recientes', async () => {
  const h = harness(); const old = deferred(); h.responses.push(old.promise, page([card('NUEVA')]));
  const first = h.catalog.load({ type: 'FUE' }); await h.catalog.load({ type: 'AGU' });
  old.resolve(page([card('ANTERIOR')])); await first;
  assert.deepEqual(h.catalog.view.items.map((item) => item.code), ['NUEVA']);
});

test('US 43: doble clic en cargar más solo envía una solicitud', async () => {
  const h = harness(); const pending = deferred(); h.responses.push(page([card('A')], 'MQ'), pending.promise);
  await h.catalog.load({}); const next = h.catalog.load({}, true); await h.catalog.load({}, true);
  assert.equal(h.requests.length, 2); pending.resolve(page([card('B')])); await next;
  assert.deepEqual(h.catalog.view.items.map((item) => item.code), ['A', 'B']);
});

test('US 42: presenta entrega realizada y previa exclusivamente según initialCollection', () => {
  assert.match(collectionMessage({ status: 'DELIVERED' }), /15 cartas y 1.000 cubos/);
  assert.match(collectionMessage({ status: 'ALREADY_DELIVERED' }), /no vuelve a entregarla/);
  assert.match(collectionMessage(null), /no informa/);
  assert.match(collectionMessage({ status: 'DESCONOCIDO' }), /no reconocido/);
});

for (const status of ['PENDING_NO_CARDS', 'PENDING_ERROR']) {
  test('US 42: muestra el mensaje del backend para ' + status, () => {
    assert.equal(collectionMessage({ status, message: 'Mensaje exacto del servidor.' }), 'Mensaje exacto del servidor.');
  });
}

test('US 35 y 42: reutiliza credenciales propias y datos de sesión sin inventar colección en dev', () => {
  const body = { accessToken: 'propio', expiresIn: 900, player: { id: 'id', role: 'ADMIN', displayName: 'Nombre recibido' } };
  const session = sessionFromResponse(body, 1000);
  assert.equal(session.token, 'propio'); assert.equal(session.role, 'ADMIN');
  assert.equal(session.expiresAt, 901000); assert.equal(session.initialCollection, null);
  assert.equal(session.player.displayName, 'Nombre recibido');
  const collection = { status: 'PENDING_NO_CARDS', message: 'Pendiente.' };
  assert.deepEqual(sessionFromResponse({ ...body, initialCollection: collection }).initialCollection, collection);
  assert.equal(sessionFromResponse({}), null);
});

test('US 35: el SDK recibe el clientId proporcionado y su callback envía el token solo a sesiones', () => {
  let initialization; let rendering; let request; let clicks = 0;
  const sdk = { initialize: (config) => { initialization = config; }, renderButton: (...args) => { rendering = args; } };
  const container = {};
  assert.equal(mountGoogleButton(sdk, '', container, () => {}), false);
  assert.equal(mountGoogleButton(sdk, 'client-id-publico', container,
    (response) => { request = googleSessionRequest(response.credential); }, () => { clicks++; }), true);
  assert.equal(initialization.client_id, 'client-id-publico'); assert.equal(initialization.auto_select, false);
  assert.equal(rendering[0], container); rendering[1].click_listener();
  initialization.callback({ credential: 'token-externo' });
  assert.equal(clicks, 1);
  assert.deepEqual(request, { method: 'POST', path: '/api/v1/sesiones', body: { provider: 'google', idToken: 'token-externo' } });
});

test('US 35: diagnóstico oculta tokens propios y externos sin cambiar el cuerpo original', () => {
  const body = { accessToken: 'secreto', nested: { idToken: 'externo' }, initialCollection: { status: 'DELIVERED' } };
  const shown = responseForDisplay(body);
  assert.equal(shown.includes('secreto'), false); assert.equal(shown.includes('externo'), false);
  assert.match(shown, /DELIVERED/); assert.equal(body.accessToken, 'secreto');
});

/** Controles y eventos del formulario: el select reproduce el rechazo del navegador a opciones inexistentes. */
function filterForm() {
  const form = new EventTarget();
  const options = [{ value: '' }, { value: 'FUE' }, { value: 'AGU' }];
  let selected = '';
  const type = { options, add: (option) => options.push(option),
    get value() { return selected; },
    set value(value) { selected = options.some((option) => option.value === value) ? value : ''; },
  };
  form.elements = { type, costMin: { value: '' }, costMax: { value: '' }, limit: { value: '' }, owned: { checked: false } };
  form.ownerDocument = { createElement: () => ({ value: '', textContent: '' }) };
  return form;
}

test('US 43: acciones reales de casos rápidos sincronizan formulario, filtros activos y consulta enviada', async () => {
  const h = harness();
  const form = filterForm();
  const status = { textContent: '' };
  const binding = new CatalogFilterForm(form, h.catalog, status);
  const actions = catalogQuickTests(binding);
  h.catalog.render = () => binding.update();

  // Un envío del formulario conserva los filtros visibles y arranca una consulta sin cursor.
  form.elements.type.value = 'FUE';
  form.elements.costMin.value = '2';
  form.elements.costMax.value = '7';
  h.responses.push(page([card('FUE')], 'MQ'));
  const submit = new Event('submit', { cancelable: true });
  form.dispatchEvent(submit);
  await Promise.resolve();
  assert.equal(submit.defaultPrevented, true);
  assert.equal(h.requests[0].path, '/api/v1/catalogo?type=FUE&costMin=2&costMax=7');
  assert.match(status.textContent, /formulario coincide/);

  // Editar no cambia la consulta activa ni los filtros que Cargar más envía al servidor.
  form.elements.type.value = 'AGU';
  form.dispatchEvent(new Event('change'));
  assert.match(status.textContent, /Tipo: FUE.*cambios sin aplicar/);
  h.responses.push(page([card('FUE-2')]));
  await h.catalog.load({}, true);
  assert.equal(h.requests[1].path, '/api/v1/catalogo?type=FUE&costMin=2&costMax=7&cursor=MQ');
  assert.equal(form.elements.type.value, 'AGU');
  assert.match(status.textContent, /cambios sin aplicar/);

  // Se ejecuta el mismo run que recibe el botón de la página, no una llamada directa a PlayerCatalog.
  const owned = actions.find((action) => action.label === 'Solo mis cartas');
  h.responses.push(page([card('PROPIA', 1)]));
  await owned.run(owned.expected);
  assert.equal(form.elements.type.value, '');
  assert.equal(form.elements.costMin.value, '');
  assert.equal(form.elements.costMax.value, '');
  assert.equal(form.elements.owned.checked, true);
  assert.deepEqual(binding.read(), { owned: 'true' });
  assert.equal(h.requests[2].path, '/api/v1/catalogo?owned=true');
  assert.deepEqual(h.catalog.view.filters, binding.read());
  assert.match(status.textContent, /solo mis cartas.*formulario coincide/);

  // El valor inválido queda visible en el select en lugar de desaparecer y aparentar Todos los tipos.
  const invalid = actions.find((action) => action.label === 'Tipo inexistente');
  h.responses.push({ status: 400, body: { type: 'INVALID_FIELD', detail: 'Tipo inválido.' } });
  await invalid.run(invalid.expected);
  assert.equal(form.elements.type.value, 'INVALID');
  assert.equal(form.elements.owned.checked, false);
  assert.match(form.elements.type.options.find((option) => option.value === 'INVALID').textContent, /inválido de prueba/);
  assert.equal(h.requests[3].path, '/api/v1/catalogo?type=INVALID');
  assert.deepEqual(h.catalog.view.filters, binding.read());
  assert.match(status.textContent, /Tipo: INVALID.*formulario coincide/);

  const limit = actions.find((action) => action.label === 'Límite 0');
  h.responses.push({ status: 400, body: { type: 'INVALID_PAGINATION', detail: 'Límite inválido.' } });
  await limit.run(limit.expected);
  assert.equal(form.elements.type.value, '');
  assert.equal(form.elements.limit.value, '0');
  assert.equal(h.requests[4].path, '/api/v1/catalogo?limit=0');
  assert.deepEqual(h.catalog.view.filters, binding.read());
  assert.match(status.textContent, /por página: 0.*formulario coincide/);
});

test('US 43: limpiar el formulario consulta sin filtros y descarta la edición pendiente', async () => {
  const h = harness(); const form = filterForm(); const status = { textContent: '' };
  const binding = new CatalogFilterForm(form, h.catalog, status);
  form.elements.costMin.value = '4'; form.elements.owned.checked = true;
  form.dispatchEvent(new Event('input'));
  assert.match(status.textContent, /cambios sin aplicar/);
  h.responses.push(page([]));
  form.dispatchEvent(new Event('reset'));
  await Promise.resolve(); await Promise.resolve();
  assert.deepEqual(binding.read(), {});
  assert.equal(h.requests[0].path, '/api/v1/catalogo');
  assert.match(status.textContent, /formulario coincide/);
});
