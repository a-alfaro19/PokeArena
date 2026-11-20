/*
 * Propósito: pantallas "Armar deck" y "Mis decks" de la historia 3.1.3 (sección 11 del Sprint 3).
 * Responsabilidades: cargar mi colección paginada (GET /api/v1/catalogo?owned=true), llevar la selección de
 * cartas sin repetidas y con el tope de deckSize, crear el deck (POST /api/v1/decks con Idempotency-Key),
 * listar mis decks y abrir su detalle (GET /api/v1/decks y GET /api/v1/decks/{id}), y los casos rápidos de
 * cada error. También arma los nodos de las tarjetas, siempre con textContent.
 * Referencia: sección 7.1 y 11 del Sprint 3; Anexos A.7 y A.8.
 *
 * Decisiones:
 *  - Sin dependencias ni DOM global: app.js inyecta send (el mismo adaptador HTTP del panel de respuesta), la
 *    sesión, las opciones del juego, el generador de claves y el document. Así src/test/javascript/
 *    deck.test.mjs lo prueba con node --test, y este módulo no importa nada (las pruebas lo cargan como data:).
 *  - Ningún número de las reglas está escrito aquí: deckSize y deckNameMaxLength llegan de
 *    GET /api/v1/opciones/juego. Sin ellos, armar un deck y los casos rápidos quedan deshabilitados.
 *  - La selección es un Set de códigos: una carta se agrega o se quita, nunca queda dos veces, y no se pueden
 *    elegir más de deckSize. Guardar sigue disponible con menos cartas: el servidor informa el error.
 *  - Los casos rápidos sí arman a propósito listas inválidas (una carta de menos o de más, repetidas, una
 *    carta ajena) a partir de deckSize y de mi colección real, sin pasar por la selección.
 *  - Todo texto del servidor (nombres, mensajes de error) se escribe con textContent: nunca como HTML.
 */

export const DECK_PATHS = {
  decks: '/api/v1/decks',
  catalog: '/api/v1/catalogo',
};

const HTTP_OK = 200;
const HTTP_CREATED = 201;
const HTTP_UNAUTHORIZED = 401;

/** Caracteres de la clave de idempotencia que se usan como sufijo de un nombre único de prueba. */
const NAME_SUFFIX_LENGTH = 8;

/** Mi colección para armar el deck: el filtro owned del catálogo (CatalogRules.OWNED), con su cursor. */
export function ownedCollectionPath(cursor = null) {
  const query = new URLSearchParams({ owned: 'true' });
  if (cursor) query.set('cursor', cursor);
  return DECK_PATHS.catalog + '?' + query.toString();
}

/** El catálogo completo (para encontrar una carta que no poseo en el caso rápido "Carta ajena"). */
export function catalogPagePath(cursor = null) {
  return DECK_PATHS.catalog + (cursor ? '?' + new URLSearchParams({ cursor }).toString() : '');
}

/** Página de mis decks. */
export function deckListPath(cursor = null) {
  return DECK_PATHS.decks + (cursor ? '?' + new URLSearchParams({ cursor }).toString() : '');
}

/** Detalle de un deck; el id se codifica, aunque sea un número del servidor. */
export function deckDetailPath(id) {
  return DECK_PATHS.decks + '/' + encodeURIComponent(String(id));
}

/** Resultado para mostrar: status, type, detail y valores permitidos tal como los envió el servidor. */
export function outcomeText(result) {
  if (!result) return 'sin respuesta del servidor';
  const body = result.body || {};
  const parts = [String(result.status)];
  if (typeof body.type === 'string') parts.push(body.type);
  let text = parts.join(' ');
  if (typeof body.detail === 'string') text += ': ' + body.detail;
  if (Array.isArray(body.allowedValues) && body.allowedValues.length) {
    text += ' Valores permitidos: ' + body.allowedValues.join(', ') + '.';
  }
  return text;
}

/** Nombre único por ejecución, para repetir un caso rápido sin chocar con decks de ejecuciones anteriores. */
export function uniqueName(base, key) {
  return base + ' ' + String(key).replaceAll('-', '').slice(0, NAME_SUFFIX_LENGTH);
}

/** Estado de "Armar deck": mi colección, la selección y el resultado de guardar. */
export class DeckBuilder {
  constructor({ send, getSession, getGameOptions, newKey, render }) {
    this.send = send;
    this.getSession = getSession;
    this.getGameOptions = getGameOptions;
    this.newKey = newKey;
    this.render = render;
    this.version = 0;
    this.clear();
  }

  /** Vacía la colección y la selección; invalida las cargas pendientes de la sesión anterior. */
  clear(message = 'Inicia sesión para armar un deck con tu colección.') {
    this.version++;
    this.selected = new Set();
    this.view = { cards: [], nextCursor: null, loading: false, message, error: false, selected: [], saving: false };
    this.render(this.view);
  }

  /** Cantidad exacta de cartas de un deck, del servidor; null si las opciones no se cargaron. */
  deckSize() {
    return this.getGameOptions()?.deckSize ?? null;
  }

  /** Carga la primera página de mi colección, o la siguiente con "Cargar más". */
  async loadCollection(append = false) {
    if (append && (this.view.loading || !this.view.nextCursor)) return null;
    const token = this.getSession().token;
    if (!token) {
      this.clear();
      return null;
    }
    const version = ++this.version;
    const cursor = append ? this.view.nextCursor : null;
    this.view = { ...this.view, cards: append ? this.view.cards : [], loading: true, error: false,
      message: 'Cargando tu colección…' };
    this.render(this.view);
    const result = await this.send({ method: 'GET', path: ownedCollectionPath(cursor), token,
      label: append ? 'Cargar más de mi colección' : 'Mi colección para armar un deck',
      isCurrent: () => version === this.version });
    if (version !== this.version) return null;
    this.view.loading = false;
    if (!result || result.status !== HTTP_OK || !Array.isArray(result.body?.items)) {
      this.view.error = true;
      this.view.message = 'No se pudo cargar tu colección: ' + outcomeText(result);
    } else {
      this.view.cards = [...this.view.cards, ...result.body.items];
      this.view.nextCursor = result.body.nextCursor ?? null;
      this.view.message = this.view.cards.length
        ? this.view.cards.length + ' carta(s) de tu colección' + (this.view.nextCursor ? ' · hay más.' : '.')
        : 'Tu colección está vacía.';
    }
    this.render(this.view);
    return result;
  }

  /** Carga páginas de mi colección hasta tener al menos count cartas o no haber más. */
  async ensureOwned(count) {
    while (this.view.cards.length < count && this.view.nextCursor) {
      const before = this.view.cards.length;
      await this.loadCollection(true);
      if (this.view.cards.length === before) break;
    }
    return this.view.cards.map((card) => card.code);
  }

  /** ¿Se puede agregar otra carta? Solo si hay opciones y aún no se eligieron deckSize. */
  canAdd() {
    const size = this.deckSize();
    return size !== null && this.selected.size < size;
  }

  /** Agrega o quita una carta. Devuelve false si no se pudo agregar porque ya hay deckSize. */
  toggle(code) {
    if (this.selected.has(code)) {
      this.selected.delete(code);
    } else if (this.canAdd()) {
      this.selected.add(code);
    } else {
      return false;
    }
    this.view.selected = [...this.selected];
    this.render(this.view);
    return true;
  }

  /** Quita todas las cartas elegidas. */
  clearSelection() {
    this.selected = new Set();
    this.view.selected = [];
    this.render(this.view);
  }

  /** Guarda el deck con las cartas elegidas; el servidor valida y su respuesta se muestra tal cual. */
  async save(name) {
    const token = this.getSession().token;
    if (!token || this.view.saving) return null;
    this.view.saving = true;
    this.render(this.view);
    const result = await this.create(name, [...this.selected], { label: 'Crear deck' });
    this.view.saving = false;
    if (result?.status === HTTP_CREATED) {
      this.selected = new Set();
      this.view.selected = [];
      this.view.error = false;
      this.view.message = 'Deck guardado: "' + result.body.name + '" con ' + result.body.cards.length + ' cartas.';
    } else if (result?.status !== HTTP_UNAUTHORIZED) {
      this.view.error = true;
      this.view.message = 'No se guardó el deck: ' + outcomeText(result);
    }
    this.render(this.view);
    return result;
  }

  /** POST /api/v1/decks con una Idempotency-Key nueva generada en el navegador. */
  create(name, cards, { label, expected = null } = {}) {
    return this.send({ method: 'POST', path: DECK_PATHS.decks, token: this.getSession().token,
      body: { name, cards }, idempotencyKey: this.newKey(), label, expected });
  }
}

/** Estado de "Mis decks": la lista paginada y el detalle abierto. */
export class MyDecks {
  constructor({ send, getSession, render }) {
    this.send = send;
    this.getSession = getSession;
    this.render = render;
    this.version = 0;
    this.clear();
  }

  clear(message = 'Inicia sesión para ver tus decks.') {
    this.version++;
    this.view = { items: [], nextCursor: null, loading: false, message, error: false, detail: null,
      detailMessage: '' };
    this.render(this.view);
  }

  /**
   * Recarga interna de la lista (después de guardar un deck o de un caso rápido): actualiza la lista sin
   * escribir en el panel de respuesta, que conserva la última respuesta que pidió el usuario.
   */
  refresh() {
    return this.load(false, { silent: true });
  }

  /**
   * Primera página de mis decks, o la siguiente con "Cargar más".
   *
   * @param silent true en las recargas internas: la solicitud no se muestra en el panel de respuesta
   */
  async load(append = false, { silent = false } = {}) {
    if (append && (this.view.loading || !this.view.nextCursor)) return null;
    const token = this.getSession().token;
    if (!token) {
      this.clear();
      return null;
    }
    const version = ++this.version;
    const cursor = append ? this.view.nextCursor : null;
    this.view = { ...this.view, items: append ? this.view.items : [], loading: true, error: false,
      message: 'Cargando tus decks…' };
    this.render(this.view);
    const result = await this.send({ method: 'GET', path: deckListPath(cursor), token,
      label: append ? 'Cargar más decks' : 'Mis decks', silent, isCurrent: () => version === this.version });
    if (version !== this.version) return null;
    this.view.loading = false;
    if (!result || result.status !== HTTP_OK || !Array.isArray(result.body?.items)) {
      this.view.error = true;
      this.view.message = 'No se pudo cargar tus decks: ' + outcomeText(result);
    } else {
      this.view.items = [...this.view.items, ...result.body.items];
      this.view.nextCursor = result.body.nextCursor ?? null;
      this.view.message = this.view.items.length ? this.view.items.length + ' deck(s)'
        + (this.view.nextCursor ? ' · hay más.' : '.') : 'Todavía no tienes decks.';
    }
    this.render(this.view);
    return result;
  }

  /** Abre el detalle de un deck con sus cartas. */
  async open(id) {
    const token = this.getSession().token;
    if (!token) return null;
    const version = this.version;
    const result = await this.send({ method: 'GET', path: deckDetailPath(id), token, label: 'Detalle del deck',
      isCurrent: () => version === this.version });
    if (version !== this.version) return null;
    if (result?.status === HTTP_OK) {
      this.view.detail = result.body;
      this.view.detailMessage = '';
    } else {
      this.view.detail = null;
      this.view.detailMessage = 'No se pudo abrir el deck: ' + outcomeText(result);
    }
    this.render(this.view);
    return result;
  }
}

/**
 * Casos rápidos de la historia 3.1.3, uno por error (sección 11). Cada uno arma su lista a partir de
 * deckSize y de mi colección real, y informa el status y el type que devolvió el servidor con report.
 *
 * @param builder      el DeckBuilder de la página (da la colección, las opciones y el envío)
 * @param findForeign  devuelve el código de una carta del catálogo que no poseo, o null
 * @param report       recibe el texto del resultado de cada caso
 * @param afterRun     se llama al terminar cada caso (por ejemplo, la recarga silenciosa de Mis decks)
 */
export function deckQuickTests(builder, findForeign, report, afterRun = async () => {}) {
  /** Las cartas que necesita el caso, o null después de explicar por qué no se puede ejecutar. */
  async function prepare(label, needed) {
    if (!builder.getSession().token) {
      report(label + ': inicia sesión como jugador o administrador para ejecutar este caso.');
      return null;
    }
    const size = builder.deckSize();
    if (size === null) {
      report(label + ': no se cargaron las opciones del juego (GET /api/v1/opciones/juego).');
      return null;
    }
    const count = needed(size);
    const codes = await builder.ensureOwned(count);
    if (codes.length < count) {
      report(label + ': necesitas al menos ' + count + ' cartas en tu colección para este caso; tienes '
        + codes.length + '.');
      return null;
    }
    return { size, codes };
  }

  async function send(label, expected, cards) {
    const key = builder.newKey();
    const result = await builder.create(uniqueName('Caso rápido', key), cards, { label, expected });
    report(label + ' (' + cards.length + ' cartas): ' + outcomeText(result));
    return result;
  }

  /** Ejecuta el caso y después afterRun, sin que afterRun cambie el resultado que se devuelve. */
  const withAfterRun = (tests) => tests.map((item) => ({ ...item, run: async (expected) => {
    const result = await item.run(expected);
    await afterRun();
    return result;
  } }));

  return withAfterRun([
    { label: 'Deck con una carta de menos', expected: { status: 400, type: 'INVALID_DECK_SIZE' },
      run: async (expected) => {
        const ready = await prepare('Una carta de menos', (size) => size - 1);
        return ready && send('Una carta de menos', expected, ready.codes.slice(0, ready.size - 1));
      } },
    { label: 'Deck con una carta de más', expected: { status: 400, type: 'INVALID_DECK_SIZE' },
      run: async (expected) => {
        const ready = await prepare('Una carta de más', (size) => size + 1);
        return ready && send('Una carta de más', expected, ready.codes.slice(0, ready.size + 1));
      } },
    { label: 'Cartas repetidas', expected: { status: 400, type: 'DUPLICATE_DECK_CARDS' },
      run: async (expected) => {
        // deckSize cartas en total, con la primera dos veces: solo cambia la regla de las repetidas.
        const ready = await prepare('Cartas repetidas', (size) => size - 1);
        if (!ready) return null;
        const cards = ready.codes.slice(0, ready.size - 1);
        return send('Cartas repetidas', expected, [...cards, cards[0]]);
      } },
    { label: 'Carta ajena', expected: { status: 400, type: 'CARD_NOT_OWNED' },
      run: async (expected) => {
        const ready = await prepare('Carta ajena', (size) => size - 1);
        if (!ready) return null;
        const foreign = await findForeign();
        if (!foreign) {
          report('Carta ajena: no hay en el catálogo ninguna carta que no poseas.');
          return null;
        }
        return send('Carta ajena', expected, [...ready.codes.slice(0, ready.size - 1), foreign]);
      } },
    { label: 'Nombre repetido', expected: { status: 409, type: 'DECK_NAME_TAKEN' },
      run: async (expected) => {
        const ready = await prepare('Nombre repetido', (size) => size);
        if (!ready) return null;
        // Nombre único por ejecución y dos claves distintas: se puede repetir el caso sin chocar con otros.
        const name = uniqueName('Repetido', builder.newKey());
        const cards = ready.codes.slice(0, ready.size);
        const first = await builder.create(name, cards, { label: 'Nombre repetido: primer deck',
          expected: { status: HTTP_CREATED } });
        if (first?.status !== HTTP_CREATED) {
          report('Nombre repetido: el primer deck no se creó: ' + outcomeText(first));
          return first;
        }
        const second = await builder.create(name, cards, { label: 'Nombre repetido: mismo nombre', expected });
        report('Nombre repetido: primero ' + outcomeText(first).split(':')[0] + ', después ' + outcomeText(second));
        return second;
      } },
  ]);
}

// ---------------------------------------------------------------------------------------------
// Nodos de la página. Reciben el document para poder probarlos sin navegador; nunca usan HTML.
// ---------------------------------------------------------------------------------------------

function node(doc, tag, className, text) {
  const element = doc.createElement(tag);
  if (className) element.className = className;
  if (text !== undefined && text !== null) element.textContent = String(text);
  return element;
}

/** Fecha del servidor en formato local, o el texto tal cual si no es una fecha. */
function dateText(value) {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? String(value) : date.toLocaleString('es-CR');
}

/**
 * Tarjeta de una carta de mi colección con su botón Elegir / Quitar.
 *
 * @param image función que crea la imagen (en app.js solo carga URLs del servidor y tiene reemplazo)
 */
export function collectionCardNode(doc, card, { selected, canAdd, onToggle, image }) {
  const article = node(doc, 'article', 'catalog-card deck-card' + (selected ? ' deck-card-selected' : ''));
  const body = node(doc, 'div', 'catalog-card-body');
  body.append(node(doc, 'code', null, card.code), node(doc, 'h3', null, card.name),
    node(doc, 'p', 'muted', [card.type?.name, card.rarity?.name].filter(Boolean).join(' · ')),
    node(doc, 'p', null, 'Costo: ' + card.cost + ' · Poder: ' + card.power + ' · Copias: ' + card.copies));
  const button = node(doc, 'button', selected ? 'btn btn-ghost' : 'btn btn-primary', selected ? 'Quitar' : 'Elegir');
  button.setAttribute('type', 'button');
  if (!selected && !canAdd) button.setAttribute('disabled', '');
  button.addEventListener('click', () => onToggle(card.code));
  body.append(button);
  article.append(image(card.imageLargeUrl, card.name || card.code), body);
  return article;
}

/** Fila de "Mis decks": nombre, cantidad de cartas y fecha, con el botón para ver el detalle. */
export function deckSummaryNode(doc, deck, onOpen) {
  const item = node(doc, 'li', 'deck-summary');
  item.append(node(doc, 'strong', null, deck.name),
    node(doc, 'span', 'muted', ' · ' + deck.size + ' cartas · ' + dateText(deck.createdAt) + ' '));
  const button = node(doc, 'button', 'btn btn-ghost', 'Ver detalle');
  button.setAttribute('type', 'button');
  button.addEventListener('click', () => onOpen(deck.id));
  item.append(button);
  return item;
}

/** Detalle de un deck: encabezado y una tarjeta por carta, con la miniatura. */
export function deckDetailNodes(doc, deck, image) {
  const head = node(doc, 'h3', null, deck.name + ' · ' + deck.cards.length + ' cartas');
  const created = node(doc, 'p', 'muted', 'Creado: ' + dateText(deck.createdAt));
  const grid = node(doc, 'div', 'catalog-grid deck-detail-grid');
  deck.cards.forEach((card) => {
    const article = node(doc, 'article', 'catalog-card');
    const body = node(doc, 'div', 'catalog-card-body');
    body.append(node(doc, 'code', null, card.code), node(doc, 'h3', null, card.name),
      node(doc, 'p', 'muted', [card.type?.name, card.rarity?.name].filter(Boolean).join(' · ')),
      node(doc, 'p', null, 'Costo: ' + card.cost + ' · Poder: ' + card.power));
    article.append(image(card.imageSmallUrl, card.name || card.code), body);
    grid.append(article);
  });
  return [head, created, grid];
}
