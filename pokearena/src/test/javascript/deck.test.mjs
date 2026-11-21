/*
 * Propósito: verificar el comportamiento de las pantallas Armar deck y Mis decks (historia 3.1.3) sin navegador.
 * Responsabilidades: controlar respuestas y opciones para probar la selección sin repetidas y con tope, la
 * paginación de la colección y de mis decks, los casos rápidos (cartas suficientes, nombre único, dos claves)
 * y que los textos del servidor se escriben como texto, nunca como HTML.
 * Ejecución: node --test src/test/javascript/deck.test.mjs
 */
import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';

// Importa exactamente el módulo servido al navegador sin exigir package.json ni dependencias.
const source = await readFile(new URL('../../main/resources/static/deck.js', import.meta.url), 'utf8');
const { DeckBuilder, MyDecks, deckQuickTests, ownedCollectionPath, deckListPath, deckDetailPath, outcomeText,
  uniqueName, collectionCardNode, deckSummaryNode, deckDetailNodes } = await import('data:text/javascript;base64,'
  + Buffer.from(source).toString('base64'));

// Valores de prueba: las reglas reales llegan de GET /api/v1/opciones/juego; aquí se inyectan.
const OPTIONS = { deckSize: 3, deckNameMaxLength: 10 };
const card = (code) => ({ code, name: 'Carta ' + code, imageLargeUrl: '/images/' + code + '.png',
  imageSmallUrl: '/images/' + code + '-small.png', owned: true, copies: 1 });
const page = (items, nextCursor = null) => ({ status: 200, body: { items, nextCursor, limit: 2 } });

/** Cada prueba tiene su propia sesión, opciones, claves y cola de respuestas. */
function harness({ options = OPTIONS } = {}) {
  const requests = [];
  const responses = [];
  const views = [];
  let keys = 0;
  const builder = new DeckBuilder({
    getSession: () => ({ token: 'token-propio' }), getGameOptions: () => options,
    newKey: () => 'clave-' + (++keys) + '-0000-0000',
    send: async (request) => { requests.push(request); return responses.shift(); },
    render: (view) => views.push(structuredClone(view)),
  });
  return { builder, requests, responses, views };
}

test('historia 3.1.3: la colección sale del catálogo con owned=true y su cursor; los decks, de /api/v1/decks', () => {
  assert.equal(ownedCollectionPath(), '/api/v1/catalogo?owned=true');
  assert.equal(ownedCollectionPath('abc_-'), '/api/v1/catalogo?owned=true&cursor=abc_-');
  assert.equal(deckListPath(), '/api/v1/decks');
  assert.equal(deckListPath('MQ'), '/api/v1/decks?cursor=MQ');
  assert.equal(deckDetailPath('7/../x'), '/api/v1/decks/7%2F..%2Fx');
});

test('historia 3.1.3: "Cargar más" agrega la página siguiente de mi colección', async () => {
  const h = harness();
  h.responses.push(page([card('A'), card('B')], 'c1'), page([card('C')]));
  await h.builder.loadCollection();
  await h.builder.loadCollection(true);
  assert.deepEqual(h.builder.view.cards.map((c) => c.code), ['A', 'B', 'C']);
  assert.equal(h.builder.view.nextCursor, null);
  assert.equal(h.requests[1].path, '/api/v1/catalogo?owned=true&cursor=c1');
});

test('historia 3.1.3: una carta elegida dos veces se quita (sin repetidas) y no se pasa de deckSize', () => {
  const h = harness();
  assert.equal(h.builder.toggle('A'), true);
  assert.equal(h.builder.toggle('A'), true);
  assert.deepEqual(h.builder.view.selected, []);
  ['A', 'B', 'C'].forEach((code) => h.builder.toggle(code));
  assert.equal(h.builder.canAdd(), false);
  assert.equal(h.builder.toggle('D'), false);
  assert.deepEqual(h.builder.view.selected, ['A', 'B', 'C']);
});

test('historia 3.1.3: sin opciones del juego no se puede elegir (no hay valores de respaldo)', () => {
  const h = harness({ options: null });
  assert.equal(h.builder.deckSize(), null);
  assert.equal(h.builder.toggle('A'), false);
});

test('historia 3.1.3: guardar envía nombre, cartas e Idempotency-Key, y muestra el error del servidor tal cual', async () => {
  const h = harness();
  ['A', 'B'].forEach((code) => h.builder.toggle(code));
  h.responses.push({ status: 400, body: { type: 'INVALID_DECK_SIZE', detail: 'El deck debe tener exactamente 3 cartas y tiene 2.' } });
  await h.builder.save('Fuego');
  assert.deepEqual(h.requests[0].body, { name: 'Fuego', cards: ['A', 'B'] });
  assert.equal(h.requests[0].method, 'POST');
  assert.equal(h.requests[0].path, '/api/v1/decks');
  assert.match(h.requests[0].idempotencyKey, /^clave-/);
  assert.equal(h.builder.view.error, true);
  assert.equal(h.builder.view.message, 'No se guardó el deck: 400 INVALID_DECK_SIZE: El deck debe tener exactamente 3 cartas y tiene 2.');
  assert.deepEqual(h.builder.view.selected, ['A', 'B']);
});

test('historia 3.1.3: un deck guardado limpia la selección y cada guardado usa una clave nueva', async () => {
  const h = harness();
  ['A', 'B', 'C'].forEach((code) => h.builder.toggle(code));
  h.responses.push({ status: 201, body: { id: 1, name: 'Fuego', cards: [card('A'), card('B'), card('C')] } });
  await h.builder.save('Fuego');
  assert.deepEqual(h.builder.view.selected, []);
  assert.equal(h.builder.view.error, false);
  h.responses.push({ status: 409, body: { type: 'DECK_NAME_TAKEN', detail: 'Ya tienes un deck llamado "Fuego".' } });
  await h.builder.save('Fuego');
  assert.notEqual(h.requests[0].idempotencyKey, h.requests[1].idempotencyKey);
});

test('outcomeText muestra status, type, detail y valores permitidos', () => {
  assert.equal(outcomeText(null), 'sin respuesta del servidor');
  assert.equal(outcomeText({ status: 201, body: { id: 1 } }), '201');
  assert.equal(outcomeText({ status: 400, body: { type: 'INVALID_FIELD', detail: 'Largo inválido.', allowedValues: ['1..10'] } }),
    '400 INVALID_FIELD: Largo inválido. Valores permitidos: 1..10.');
});

test('uniqueName agrega un sufijo de la clave para repetir los casos sin chocar', () => {
  assert.equal(uniqueName('Repetido', '1a2b3c4d-5e6f-0000-0000-000000000000'), 'Repetido 1a2b3c4d');
});

// ---------------------------------------------------------------------------------------------
// Casos rápidos
// ---------------------------------------------------------------------------------------------

function quick(h, foreign = 'AJENA') {
  const reports = [];
  const tests = deckQuickTests(h.builder, async () => foreign, (text) => reports.push(text));
  const byLabel = (label) => tests.find((item) => item.label === label);
  return { tests, reports, byLabel };
}

test('casos rápidos: hay uno por cada error de la historia y cada uno espera su status y type', () => {
  const { tests } = quick(harness());
  assert.deepEqual(tests.map((item) => item.expected.type), ['INVALID_DECK_SIZE', 'INVALID_DECK_SIZE',
    'DUPLICATE_DECK_CARDS', 'CARD_NOT_OWNED', 'DECK_NAME_TAKEN']);
});

test('casos rápidos: una carta de menos y de más se calculan con deckSize y la colección real', async () => {
  const h = harness();
  h.responses.push(page([card('A'), card('B'), card('C'), card('D')]));
  await h.builder.loadCollection();
  const q = quick(h);
  h.responses.push({ status: 400, body: { type: 'INVALID_DECK_SIZE', detail: 'tiene 2' } },
    { status: 400, body: { type: 'INVALID_DECK_SIZE', detail: 'tiene 4' } });
  await q.byLabel('Deck con una carta de menos').run(q.byLabel('Deck con una carta de menos').expected);
  await q.byLabel('Deck con una carta de más').run(q.byLabel('Deck con una carta de más').expected);
  assert.deepEqual(h.requests.slice(1).map((request) => request.body.cards.length), [OPTIONS.deckSize - 1, OPTIONS.deckSize + 1]);
  assert.match(q.reports[0], /\(2 cartas\): 400 INVALID_DECK_SIZE/);
  assert.match(q.reports[1], /\(4 cartas\): 400 INVALID_DECK_SIZE/);
});

test('casos rápidos: con pocas cartas propias, avisa cuántas hacen falta en vez de enviar otra cosa', async () => {
  const h = harness();
  h.responses.push(page([card('A'), card('B'), card('C')]));
  await h.builder.loadCollection();
  const q = quick(h);
  const result = await q.byLabel('Deck con una carta de más').run(q.byLabel('Deck con una carta de más').expected);
  assert.equal(result, null);
  assert.equal(h.requests.length, 1);
  assert.equal(q.reports[0], 'Una carta de más: necesitas al menos ' + (OPTIONS.deckSize + 1)
    + ' cartas en tu colección para este caso; tienes 3.');
});

test('casos rápidos: carga más páginas de la colección si hace falta antes de rendirse', async () => {
  const h = harness();
  h.responses.push(page([card('A'), card('B')], 'c1'));
  await h.builder.loadCollection();
  const q = quick(h);
  h.responses.push(page([card('C'), card('D')]), { status: 400, body: { type: 'INVALID_DECK_SIZE' } });
  await q.byLabel('Deck con una carta de más').run(q.byLabel('Deck con una carta de más').expected);
  assert.equal(h.requests[1].path, '/api/v1/catalogo?owned=true&cursor=c1');
  assert.equal(h.requests[2].body.cards.length, OPTIONS.deckSize + 1);
});

test('casos rápidos: repetidas y carta ajena arman deckSize cartas con la regla rota', async () => {
  const h = harness();
  h.responses.push(page([card('A'), card('B'), card('C')]));
  await h.builder.loadCollection();
  const q = quick(h, 'AJENA');
  h.responses.push({ status: 400, body: { type: 'DUPLICATE_DECK_CARDS' } }, { status: 400, body: { type: 'CARD_NOT_OWNED' } });
  await q.byLabel('Cartas repetidas').run(q.byLabel('Cartas repetidas').expected);
  await q.byLabel('Carta ajena').run(q.byLabel('Carta ajena').expected);
  assert.deepEqual(h.requests[1].body.cards, ['A', 'B', 'A']);
  assert.deepEqual(h.requests[2].body.cards, ['A', 'B', 'AJENA']);
});

test('casos rápidos: nombre repetido usa un nombre único por ejecución y dos claves distintas', async () => {
  const h = harness();
  h.responses.push(page([card('A'), card('B'), card('C')]));
  await h.builder.loadCollection();
  const q = quick(h);
  h.responses.push({ status: 201, body: { id: 1 } }, { status: 409, body: { type: 'DECK_NAME_TAKEN', detail: 'En uso.' } });
  await q.byLabel('Nombre repetido').run(q.byLabel('Nombre repetido').expected);
  const [first, second] = h.requests.slice(1);
  assert.equal(first.body.name, second.body.name);
  assert.match(first.body.name, /^Repetido [0-9a-z]+$/);
  assert.notEqual(first.idempotencyKey, second.idempotencyKey);
  assert.deepEqual(second.expected, { status: 409, type: 'DECK_NAME_TAKEN' });
  assert.equal(q.reports.at(-1), 'Nombre repetido: primero 201, después 409 DECK_NAME_TAKEN: En uso.');

  // Otra ejecución usa otro nombre.
  h.responses.push({ status: 201, body: { id: 2 } }, { status: 409, body: { type: 'DECK_NAME_TAKEN' } });
  await q.byLabel('Nombre repetido').run(q.byLabel('Nombre repetido').expected);
  assert.notEqual(h.requests[3].body.name, first.body.name);
});

test('panel de respuesta: tras un caso rápido con 400, la recarga de Mis decks no reemplaza su status y type', async () => {
  // Envío compartido que imita el panel de app.js: lo escribe toda solicitud que no sea silenciosa.
  const panel = { method: null, path: null, status: null, type: null };
  const responses = [];
  const requests = [];
  const send = async (request) => {
    requests.push(request);
    const result = responses.shift();
    if (!request.silent) {
      Object.assign(panel, { method: request.method, path: request.path, status: result.status,
        type: result.body?.type ?? null });
    }
    return result;
  };
  const builder = new DeckBuilder({ send, getSession: () => ({ token: 't' }), getGameOptions: () => OPTIONS,
    newKey: () => 'clave-1-0000', render: () => {} });
  const decks = new MyDecks({ send, getSession: () => ({ token: 't' }), render: () => {} });
  responses.push(page([card('A'), card('B'), card('C')]));
  await builder.loadCollection();
  const tests = deckQuickTests(builder, async () => null, () => {}, () => decks.refresh());
  const fewer = tests.find((item) => item.label === 'Deck con una carta de menos');

  responses.push({ status: 400, body: { type: 'INVALID_DECK_SIZE', detail: 'tiene 2' } }, page([{ id: 1, name: 'Uno', size: 3 }]));
  const result = await fewer.run(fewer.expected);

  assert.equal(result.status, 400);
  // La lista sí se recargó (GET silencioso) y tiene el deck...
  assert.equal(requests.at(-1).method, 'GET');
  assert.equal(requests.at(-1).silent, true);
  assert.deepEqual(decks.view.items.map((deck) => deck.id), [1]);
  // ...pero el panel conserva la respuesta del caso rápido: el POST con 400 INVALID_DECK_SIZE.
  assert.deepEqual(panel, { method: 'POST', path: '/api/v1/decks', status: 400, type: 'INVALID_DECK_SIZE' });
});

test('panel de respuesta: abrir Mis decks a pedido del usuario sí se muestra; la recarga interna no', async () => {
  const requests = [];
  const decks = new MyDecks({ getSession: () => ({ token: 't' }), render: () => {},
    send: async (request) => { requests.push(request); return page([]); } });
  await decks.load();
  await decks.refresh();
  assert.deepEqual(requests.map((request) => request.silent), [false, true]);
});

// ---------------------------------------------------------------------------------------------
// Mis decks
// ---------------------------------------------------------------------------------------------

test('historia 3.1.3: Mis decks pagina con el cursor y abre el detalle; un 404 se muestra como mensaje', async () => {
  const requests = [];
  const responses = [page([{ id: 1, name: 'Uno', size: 3 }], 'c1'), page([{ id: 2, name: 'Dos', size: 3 }]),
    { status: 200, body: { id: 2, name: 'Dos', cards: [] } },
    { status: 404, body: { type: 'DECK_NOT_FOUND', detail: 'El deck no existe.' } }];
  const decks = new MyDecks({ getSession: () => ({ token: 't' }), render: () => {},
    send: async (request) => { requests.push(request); return responses.shift(); } });
  await decks.load();
  await decks.load(true);
  assert.deepEqual(decks.view.items.map((deck) => deck.id), [1, 2]);
  await decks.open(2);
  assert.equal(decks.view.detail.name, 'Dos');
  await decks.open(99);
  assert.equal(decks.view.detail, null);
  assert.equal(decks.view.detailMessage, 'No se pudo abrir el deck: 404 DECK_NOT_FOUND: El deck no existe.');
  assert.deepEqual(requests.map((request) => request.path),
    ['/api/v1/decks', '/api/v1/decks?cursor=c1', '/api/v1/decks/2', '/api/v1/decks/99']);
});

// ---------------------------------------------------------------------------------------------
// Textos del servidor: siempre como texto, nunca como HTML
// ---------------------------------------------------------------------------------------------

/** Documento mínimo: registra los nodos y falla si alguien intenta escribir HTML. */
function fakeDocument() {
  const make = (tagName) => {
    const element = {
      tagName: tagName.toUpperCase(), children: [], attributes: {}, listeners: {}, className: '', text: '',
      set textContent(value) { this.text = value; this.children = []; },
      get textContent() { return this.text + this.children.map((child) => child.textContent).join(''); },
      set innerHTML(value) { throw new Error('innerHTML no está permitido: ' + value); },
      append(...nodes) { this.children.push(...nodes); },
      setAttribute(name, value) { this.attributes[name] = value; },
      addEventListener(event, handler) { this.listeners[event] = handler; },
    };
    return element;
  };
  return { createElement: make };
}

function allElements(nodes) {
  return nodes.flatMap((node) => [node, ...allElements(node.children || [])]);
}

test('seguridad: un nombre de deck o de carta como <img src=x> se muestra como texto, sin crear elementos', () => {
  const doc = fakeDocument();
  const hostile = '<img src=x onerror=alert(1)>';
  const image = (url, alt) => Object.assign(doc.createElement('img'), { alt, src: url });
  const summary = deckSummaryNode(doc, { id: 1, name: hostile, size: 3, createdAt: '2026-11-19T12:00:00Z' }, () => {});
  const detail = deckDetailNodes(doc, { name: hostile, createdAt: 'x', cards: [{ ...card('A'), name: hostile }] }, image);
  const collection = collectionCardNode(doc, { ...card('A'), name: hostile }, { selected: false, canAdd: true,
    onToggle: () => {}, image });

  for (const roots of [[summary], detail, [collection]]) {
    const elements = allElements(roots);
    // La única imagen es la que crea la función image (con la URL del servidor), nunca una del texto.
    assert.ok(elements.filter((node) => node.tagName === 'IMG').every((node) => node.src?.startsWith('/images/')));
    // El texto hostil quedó como texto de un nodo, exactamente igual.
    assert.ok(elements.some((node) => node.text === hostile || node.text.startsWith(hostile + ' ·')));
  }
  assert.equal(summary.children[0].text, hostile);
});
