/*
 * Propósito: estado de la pantalla de inicio de partida (sección 11, historia 5.1.1).
 * Responsabilidades: consultar una partida con la sesión actual, descartar respuestas obsoletas y calcular
 * el tiempo restante usando las marcas del servidor; la presentación del estado vive en app.js.
 * La ruta de entrada de la interfaz es ?partida=<id>, preparada para el flujo que abre la búsqueda al emparejar.
 */
'use strict';

export const MATCH_PATHS = { matches: '/api/v1/partidas' };

const HTTP_OK = 200;
const MILLISECONDS_PER_SECOND = 1000;

export function matchPath(id) {
  return MATCH_PATHS.matches + '/' + encodeURIComponent(String(id));
}

/** Calcula el tiempo restante compensando el tiempo transcurrido desde que llegó la respuesta. */
export function remainingTurnSeconds(turnEndsAt, serverTime, receivedAt, now) {
  const end = Date.parse(turnEndsAt);
  const server = Date.parse(serverTime);
  if (!Number.isFinite(end) || !Number.isFinite(server) || !Number.isFinite(receivedAt) || !Number.isFinite(now)) {
    return null;
  }
  const serverNow = server + (now - receivedAt);
  return Math.max(0, Math.ceil((end - serverNow) / MILLISECONDS_PER_SECOND));
}

function responseText(result) {
  if (!result) return 'sin respuesta del servidor';
  const body = result.body || {};
  const type = typeof body.type === 'string' ? ' ' + body.type : '';
  const detail = typeof body.detail === 'string' ? ': ' + body.detail : '';
  return String(result.status) + type + detail;
}

export class MatchViewer {
  constructor({ send, getSession, render, now = () => Date.now() }) {
    this.send = send;
    this.getSession = getSession;
    this.render = render;
    this.now = now;
    this.version = 0;
    this.receivedAt = null;
    this.clear();
  }

  clear(message = 'Abre esta pantalla desde el enlace de una partida e inicia sesión para consultarla.') {
    this.version++;
    this.receivedAt = null;
    this.view = { match: null, secondsRemaining: null, loading: false, error: false, message };
    this.render(this.view);
  }

  async open(id) {
    const token = this.getSession()?.token;
    if (!token) {
      this.clear('Inicia sesión para consultar la partida indicada en este enlace.');
      return null;
    }

    const version = ++this.version;
    this.receivedAt = null;
    this.view = { match: null, secondsRemaining: null, loading: true, error: false,
      message: 'Cargando el inicio de la partida…' };
    this.render(this.view);
    const result = await this.send({ method: 'GET', path: matchPath(id), token, label: 'Inicio de partida',
      isCurrent: () => version === this.version });
    if (version !== this.version) return null;

    if (result?.status !== HTTP_OK || !result.body || typeof result.body.id !== 'string') {
      this.view.loading = false;
      this.view.error = true;
      this.view.message = result?.status === HTTP_OK
        ? 'La respuesta del servidor no contiene un inicio de partida válido.'
        : 'No se pudo cargar el inicio de la partida: ' + responseText(result);
    } else {
      this.receivedAt = this.now();
      this.view = { match: result.body,
        secondsRemaining: remainingTurnSeconds(result.body.turnEndsAt, result.body.serverTime,
          this.receivedAt, this.now()),
        loading: false, error: false, message: '' };
    }
    this.render(this.view);
    return result;
  }

  tick() {
    if (!this.view.match || this.receivedAt === null) return;
    this.view.secondsRemaining = this.secondsRemaining();
    this.render(this.view);
  }

  secondsRemaining() {
    return remainingTurnSeconds(this.view.match.turnEndsAt, this.view.match.serverTime,
      this.receivedAt, this.now());
  }
}
