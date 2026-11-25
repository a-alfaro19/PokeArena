// Historia 4.1.2. La pantalla que crea la búsqueda llama a showMatchSearch con el id del POST.
export function showMatchSearch(id) {
  document.dispatchEvent(new CustomEvent('pokearena:busqueda', { detail: { id } }));
}

// La vista de inicio de partida recibe este evento con el identificador confirmado por la API.
export function showAssignedMatch(matchId) {
  document.dispatchEvent(new CustomEvent('pokearena:partida', { detail: { matchId } }));
}

const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/** Alta clasificatoria (4.1.1). Tras el POST, la pantalla de estado administra el sondeo. */
export class MatchSearchStarter {
  constructor({ send, getSession, newKey, render, openSearch }) {
    Object.assign(this, { send, getSession, newKey, render, openSearch });
    this.version = 0;
    this.reset();
  }

  reset() {
    // Invalidar las respuestas pendientes al cerrar o cambiar la sesión.
    this.version++;
    this.pending = null;
    this.busy = false;
    this.message = '';
    this.render(this);
  }

  select(deckId) {
    // Cambiar el deck inicia una solicitud diferente aunque se vuelva a elegir el anterior.
    if (this.pending?.deckId !== Number(deckId)) this.pending = null;
    this.message = '';
    this.render(this);
  }

  async start(value, quick = null) {
    const token = this.getSession().token;
    if (!token || this.busy) return;

    // Los casos rápidos envían entradas inválidas de forma deliberada para verificar la API.
    const deckId = Number(value);
    if (quick !== 'missing' && (!Number.isSafeInteger(deckId) || deckId < 1)) {
      this.message = 'Debes seleccionar un deck.';
      this.render(this);
      return;
    }

    const version = this.version;
    const isCurrent = () => version === this.version && token === this.getSession().token;
    const post = (key, body, expected) => this.send({ method: 'POST', path: '/api/v1/busquedas',
      token, body, idempotencyKey: key, label: 'Buscar partida', expected, isCurrent });
    const body = quick === 'missing' ? { context: 'RANKED' } : { deckId, context: 'RANKED' };
    if (!quick && this.pending?.deckId !== deckId) this.pending = { deckId, key: this.newKey() };

    // El bloqueo local evita el doble clic. La clave se conserva si el resultado de la solicitud es incierto.
    this.busy = true;
    this.message = '';
    this.render(this);
    const expected = quick === 'missing' ? { status: 400, type: 'MISSING_FIELD' }
      : quick === 'foreign' ? { status: 404, type: 'DECK_NOT_FOUND' } : undefined;
    let result = await post(quick ? this.newKey() : this.pending.key, body, expected);
    if (!isCurrent()) return;
    if (quick === 'twice' && establishedSearch(result)) {
      // Una segunda clave comprueba que la API reutiliza la búsqueda existente con HTTP 200.
      result = await post(this.newKey(), body, { status: 200 });
      if (!isCurrent()) return;
    }
    this.busy = false;

    // Los casos de error quedan en la pantalla para que se pueda inspeccionar la respuesta.
    if (establishedSearch(result) && !expected) {
      this.pending = null;
      this.render(this);
      this.openSearch(result.body.id);
    } else {
      this.message = result?.body?.detail || 'No se pudo iniciar la búsqueda. Puedes volver a intentarlo.';
      this.render(this);
    }
    return result;
  }
}

function establishedSearch(result) {
  // Solo trasladar a la pantalla de estado un identificador confirmado por el alta.
  return (result?.status === 200 || result?.status === 201)
    && typeof result.body?.id === 'string' && uuid.test(result.body.id);
}

export class MatchSearchScreen {
  constructor({ send, render, openMatch, newKey,
    schedule = globalThis.setTimeout.bind(globalThis), unschedule = globalThis.clearTimeout.bind(globalThis) }) {
    Object.assign(this, { send, render, openMatch, newKey, schedule, unschedule });
    this.version = 0;
    this.visible = true;
    this.reset();
  }

  reset() {
    this.version++;
    this.unschedule(this.timer);
    this.id = '';
    this.data = null;
    this.botKey = null;
    this.busy = false;
    this.message = '';
    this.render(this);
  }

  open(id, silent = false) {
    id = String(id ?? '').trim().toLowerCase();
    if (id === this.id && this.busy) return;
    if (id !== this.id) this.reset();
    if (!uuid.test(id)) {
      this.message = 'Introduce el identificador de la búsqueda (UUID).';
      this.render(this);
      return;
    }
    this.id = id;
    return this.request('GET', null, silent);
  }

  cancel(quick = false) {
    if (this.busy || this.data?.status !== (quick ? 'MATCHED' : 'WAITING')) return;
    return this.request('DELETE', quick ? 'SEARCH_ALREADY_MATCHED' : null);
  }

  accept(quick = false) {
    if (this.busy || this.data?.status !== 'WAITING' || this.data.botOfferAvailable === quick) return;
    this.botKey ??= this.newKey();
    return this.request('POST', quick ? 'BOT_OFFER_NOT_AVAILABLE' : null);
  }

  setVisible(visible) {
    if (visible === this.visible) return;
    this.visible = visible;
    this.unschedule(this.timer);
    if (!this.busy && this.id && (!this.data || this.data.status === 'WAITING')) {
      if (visible) return this.request('GET', null, true);
      this.version++;
    }
  }

  pollLater() {
    const seconds = this.data?.pollIntervalSeconds;
    if (this.visible && this.data?.status === 'WAITING' && Number.isFinite(seconds) && seconds > 0) {
      this.timer = this.schedule(() => this.request('GET', null, true), seconds * 1000);
    }
  }

  async request(method, expectedType = null, silent = false) {
    this.unschedule(this.timer);
    const version = ++this.version;
    const isCurrent = () => version === this.version;
    this.busy = method !== 'GET';
    this.render(this);
    const path = '/api/v1/busquedas/' + this.id;
    const result = await this.send({
      method, path: method === 'POST' ? path + '/bot' : path,
      idempotencyKey: method === 'POST' ? this.botKey : undefined,
      expected: expectedType ? { status: 409, type: expectedType } : undefined,
      silent, isCurrent,
    });
    if (!isCurrent()) return;
    this.busy = false;
    if (!result?.ok) {
      this.message = result?.body?.detail || 'No se pudo consultar la API. Puedes volver a intentarlo.';
      if (result?.status === 404 || result?.status === 403 || result?.status === 401) this.data = null;
      if (result?.body?.type === 'SEARCH_ALREADY_MATCHED' || result?.body?.type === 'SEARCH_NOT_OPEN') {
        // El GET entrega matchId como dato estructurado, aun cuando el DELETE solo lo trae en detail.
        this.data = null;
        this.render(this);
        if (method !== 'GET') return this.request('GET', null, true);
      }
      this.render(this);
      this.pollLater();
      return;
    }
    const previousMatch = this.data?.matchId;
    this.data = result.body;
    this.message = '';
    this.render(this);
    if (this.data.status === 'MATCHED' && this.data.matchId && this.data.matchId !== previousMatch) {
      this.openMatch(this.data.matchId);
    }
    this.pollLater();
  }
}
