/*
 * Propósito: pantalla de inicio de partida de la historia 5.1.1.
 * Responsabilidades: consultar una partida asignada para la sesión actual, validar su respuesta pública y
 * mantener el contador del turno usando las marcas de tiempo del servidor.
 * Referencia: sección 11; Anexo A.15.
 */

const HTTP_OK = 200;
const CLOCK_UPDATE_MS = 1000;
const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/** Ruta de la vista privada de la partida. */
export function matchViewPath(id) {
  return '/api/v1/partidas/' + encodeURIComponent(id);
}

function isNonNegativeInteger(value) {
  return Number.isSafeInteger(value) && value >= 0;
}

function isCard(card) {
  return card && typeof card.code === 'string' && typeof card.name === 'string'
    && isNonNegativeInteger(card.cost) && Number.isSafeInteger(card.power)
    && typeof card.imageSmallUrl === 'string';
}

function cardView(card) {
  return { code: card.code, name: card.name, cost: card.cost, power: card.power,
    imageSmallUrl: card.imageSmallUrl };
}

/** Verifica la forma segura que publica MatchResponse, sin aceptar estado parcial. */
function isMatchView(data, expectedId) {
  return data && typeof data.id === 'string' && UUID_PATTERN.test(data.id) && data.id === expectedId
    && typeof data.status === 'string' && typeof data.context === 'string' && typeof data.vsBot === 'boolean'
    && Number.isSafeInteger(data.turn) && data.turn > 0
    && Number.isSafeInteger(data.turnDurationSeconds) && data.turnDurationSeconds > 0
    && Number.isFinite(Date.parse(data.turnEndsAt)) && Number.isFinite(Date.parse(data.serverTime))
    && isNonNegativeInteger(data.bet)
    && Array.isArray(data.locations) && data.locations.length > 0
    && data.locations.every((location) => location
      && Number.isSafeInteger(location.slot) && Number.isSafeInteger(location.id)
      && typeof location.name === 'string' && typeof location.description === 'string'
      && isNonNegativeInteger(location.cardLimit) && typeof location.extraEnergyMode === 'string'
      && isNonNegativeInteger(location.extraEnergyAmount) && typeof location.imageLargeUrl === 'string')
    && data.you && isNonNegativeInteger(data.you.seat) && isNonNegativeInteger(data.you.energy)
    && Array.isArray(data.you.hand) && data.you.hand.every(isCard)
    && isNonNegativeInteger(data.you.deckRemaining)
    && Array.isArray(data.you.graveyard) && data.you.graveyard.every(isCard)
    && data.rival && typeof data.rival.isBot === 'boolean'
    && isNonNegativeInteger(data.rival.handCount) && isNonNegativeInteger(data.rival.deckRemaining)
    && Array.isArray(data.rival.graveyard) && data.rival.graveyard.every(isCard);
}

function publicMatchView(data) {
  return {
    id: data.id,
    status: data.status,
    context: data.context,
    vsBot: data.vsBot,
    turn: data.turn,
    turnDurationSeconds: data.turnDurationSeconds,
    turnEndsAt: data.turnEndsAt,
    serverTime: data.serverTime,
    bet: data.bet,
    locations: data.locations.map((location) => ({
    slot: location.slot,
    id: location.id,
    name: location.name,
    description: location.description,
    cardLimit: location.cardLimit,
    extraEnergyMode: location.extraEnergyMode,
    extraEnergyAmount: location.extraEnergyAmount,
    imageLargeUrl: location.imageLargeUrl,
    })),
    you: {
    seat: data.you.seat,
    energy: data.you.energy,
    hand: data.you.hand.map(cardView),
    deckRemaining: data.you.deckRemaining,
    graveyard: data.you.graveyard.map(cardView),
    },
    rival: {
    isBot: data.rival.isBot,
    handCount: data.rival.handCount,
    deckRemaining: data.rival.deckRemaining,
    graveyard: data.rival.graveyard.map(cardView),
    },
  };
}

/** Consulta y presenta el inicio de una partida asignada al jugador autenticado. */
export class MatchViewScreen {
  constructor({ send, getSession, render,
    schedule = globalThis.setInterval.bind(globalThis),
    unschedule = globalThis.clearInterval.bind(globalThis),
    now = Date.now }) {
    Object.assign(this, { send, getSession, render, schedule, unschedule, now });
    this.version = 0;
    this.reset();
  }

  reset() {
    this.version++;
    this.clearTimer();
    this.id = '';
    this.data = null;
    this.busy = false;
    this.message = '';
    this.remainingSeconds = null;
    this.render(this);
  }

  /** Carga la vista del jugador; un id inválido nunca se envía al servidor. */
  async open(value) {
    const id = String(value ?? '').trim().toLowerCase();
    if (id !== this.id) this.reset();
    if (!UUID_PATTERN.test(id)) {
      this.message = 'Introduce el identificador de la partida (UUID).';
      this.render(this);
      return null;
    }

    const token = this.getSession().token;
    if (!token) {
      this.message = 'Inicia sesión para consultar la partida.';
      this.render(this);
      return null;
    }
    if (this.busy) return null;

    this.clearTimer();
    this.id = id;
    this.busy = true;
    this.message = '';
    const version = ++this.version;
    const startedAt = this.now();
    const isCurrent = () => version === this.version && token === this.getSession().token;
    this.render(this);

    const result = await this.send({ method: 'GET', path: matchViewPath(id), token,
      label: 'Inicio de partida', isCurrent });
    if (!isCurrent()) return result;

    this.busy = false;
    if (result?.status !== HTTP_OK || !isMatchView(result.body, id)) {
      if (result?.status === HTTP_OK || result?.status === 404 || result?.status === 401
          || result?.status === 403) this.data = null;
      this.message = result?.body?.detail
        || (result?.status === HTTP_OK
          ? 'La respuesta de la partida no tiene el formato esperado.'
          : 'No se pudo consultar la partida.');
      this.remainingSeconds = null;
      this.clearTimer();
      this.render(this);
      return result;
    }

    this.data = publicMatchView(result.body);
    const responseAt = this.now();
    const turnDuration = Date.parse(this.data.turnEndsAt) - Date.parse(this.data.serverTime);
    this.deadline = responseAt + Math.max(0, turnDuration - Math.max(0, responseAt - startedAt));
    this.updateClock();
    if (this.remainingSeconds > 0) {
      this.timer = this.schedule(() => this.updateClock(), CLOCK_UPDATE_MS);
    }
    this.render(this);
    return result;
  }

  /** Vuelve a consultar la partida actualmente abierta. */
  refresh() {
    return this.id ? this.open(this.id) : null;
  }

  updateClock() {
    if (!this.data) return;
    this.remainingSeconds = Math.ceil(Math.max(0, this.deadline - this.now()) / CLOCK_UPDATE_MS);
    if (this.remainingSeconds === 0) this.clearTimer();
    this.render(this);
  }

  clearTimer() {
    if (this.timer !== undefined && this.timer !== null) this.unschedule(this.timer);
    this.timer = null;
  }
}
