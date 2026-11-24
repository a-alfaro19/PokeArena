// Historia 4.1.2. La pantalla que crea la búsqueda llama a showMatchSearch con el id del POST.
export function showMatchSearch(id) {
  document.dispatchEvent(new CustomEvent('pokearena:busqueda', { detail: { id } }));
}

// La vista de inicio de partida recibe este evento con el identificador confirmado por la API.
export function showAssignedMatch(matchId) {
  document.dispatchEvent(new CustomEvent('pokearena:partida', { detail: { matchId } }));
}

const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

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

  open(id) {
    id = String(id ?? '').trim().toLowerCase();
    if (id === this.id && this.busy) return;
    if (id !== this.id) this.reset();
    if (!uuid.test(id)) {
      this.message = 'Introduce el identificador de la búsqueda (UUID).';
      this.render(this);
      return;
    }
    this.id = id;
    return this.request('GET');
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
