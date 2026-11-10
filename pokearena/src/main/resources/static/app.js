/*
 * Propósito: comportamiento de la interfaz web de PokéArena (corrección 1; sección 11).
 * Responsabilidades: sesiones, bienvenida y catálogo del jugador, formularios de Crear carta,
 * Crear ubicación y Buscar carta contra la API real, casos rápidos y panel de respuesta.
 * Google queda preparado para recibir su clientId de la futura configuración pública del backend.
 * Referencia: sección 11 (administrador y jugador), 5.2 (sesiones), 5.3 (errores), 5.4 (idempotencia),
 * 5.5 (paginación), 7.1, 7.2 y 7.3.
 *
 * Decisiones:
 *  - Sin frameworks ni bibliotecas: JavaScript del navegador y fetch al mismo origen.
 *  - Seguridad: nunca se inserta texto con innerHTML. Todo lo que llega del servidor se escribe con
 *    textContent o en nodos creados a mano (función el), así una respuesta no puede inyectar HTML.
 *    Las imágenes solo se cargan si su URL empieza con /images/ (las que genera el servidor).
 *  - El token vive solo en memoria (variable state): se pierde al recargar la página, a propósito.
 *  - Tipos y rarezas son listas fijas (las semillas del contrato), porque la API del Sprint 1 no
 *    tiene un endpoint para listarlas; la habilidad es un texto con HAB-000 como valor inicial.
 */
'use strict';

import { PlayerCatalog, collectionMessage, sessionFromResponse, responseForDisplay, mountGoogleButton, googleSessionRequest, CatalogFilterForm, catalogQuickTests } from './player.js';

(() => {
  // ---------------------------------------------------------------------------------------------
  // Constantes
  // ---------------------------------------------------------------------------------------------

  const API = {
    cards: '/api/v1/cartas',
    locations: '/api/v1/ubicaciones',
    devSessions: '/api/v1/dev/sesiones',
    sessions: '/api/v1/sesiones',
  };

  const ROLE_LABELS = { NONE: 'Sin sesión', PLAYER: 'Jugador', ADMIN: 'Administrador' };

  /** Semillas de la migración V1, iguales al contrato (no hay endpoint para listarlas). */
  const TYPES = [['FUE', 'Fuego'], ['AGU', 'Agua'], ['PLA', 'Planta'], ['ELE', 'Eléctrico'],
    ['PSI', 'Psíquico'], ['LUC', 'Lucha']];
  const RARITIES = [['COMMON', 'Común'], ['UNCOMMON', 'Poco común'], ['RARE', 'Rara'],
    ['LEGENDARY', 'Legendaria']];
  const TURNS = [1, 2, 3, 4, 5, 6];

  /** Tamaños de las imágenes de prueba (criterios C.1 y C.2). */
  const CARD_IMAGE = { width: 600, height: 840 };
  const LOCATION_IMAGE = { width: 800, height: 600 };
  const SQUARE_IMAGE = { width: 600, height: 600 };
  const LOW_RESOLUTION_IMAGE = { width: 300, height: 420 };
  const MAX_IMAGE_BYTES = 5 * 1024 * 1024;

  const BYTES_PER_KB = 1024;
  const MS_PER_SECOND = 1000;
  const SECONDS_PER_MINUTE = 60;
  const SESSION_REFRESH_MS = 15 * MS_PER_SECOND;
  /** Margen para no usar un token que vence en medio de la solicitud. */
  const TOKEN_SAFETY_MS = 10 * MS_PER_SECOND;
  const MAX_PREVIEWS = 8;
  const IMAGE_URL_PREFIX = '/images/';

  const HTTP_OK_MIN = 200;
  const HTTP_CLIENT_ERROR_MIN = 400;
  const HTTP_SERVER_ERROR_MIN = 500;
  const HTTP_NOT_FOUND = 404;

  /** Imagen de reemplazo cuando una URL no carga (sección 11). SVG fijo, sin datos del servidor. */
  const PLACEHOLDER = 'data:image/svg+xml;charset=utf-8,' + encodeURIComponent(
    '<svg xmlns="http://www.w3.org/2000/svg" width="200" height="280" viewBox="0 0 200 280">'
    + '<rect width="200" height="280" rx="12" fill="#e9edf3"/>'
    + '<path d="M70 150l25-30 20 22 15-16 30 34H50z" fill="#b7c0cd"/>'
    + '<circle cx="78" cy="100" r="12" fill="#b7c0cd"/>'
    + '<text x="100" y="215" font-family="sans-serif" font-size="14" text-anchor="middle" fill="#6b7685">'
    + 'Imagen no disponible</text></svg>');

  // ---------------------------------------------------------------------------------------------
  // Estado (solo en memoria)
  // ---------------------------------------------------------------------------------------------

  const state = {
    /** Sesión elegida en la barra: la usan los formularios. */
    session: { role: 'NONE', token: null, player: null, expiresAt: 0, initialCollection: null },
    // Revisiones que impiden restaurar datos o sesiones por respuestas de solicitudes anteriores.
    sessionVersion: 0,
    loginVersion: 0,
    /** Tokens de prueba por rol para los casos rápidos, sin cambiar la sesión de la barra. */
    roleTokens: {},
    /** null mientras no se sabe; false si /dev/sesiones responde 404. */
    devAvailable: null,
    /** Imágenes generadas en los formularios, por tipo ("card" o "location"). */
    generated: {},
    /** Última creación enviada, para repetirla con la misma clave. */
    lastCreate: null,
    /** Búsqueda en curso, para "Cargar más". */
    search: { params: null, token: null, nextCursor: null, shown: 0 },
  };

  // ---------------------------------------------------------------------------------------------
  // Utilidades del DOM (sin innerHTML)
  // ---------------------------------------------------------------------------------------------

  let playerCatalog;
  let catalogFilterForm;
  let googleSdk = null;
  let googleAttempt = null;
  let googleConfigured = false;
  let expiryTimer = null;

  const $ = (selector, root = document) => root.querySelector(selector);
  const $$ = (selector, root = document) => Array.from(root.querySelectorAll(selector));

  /**
   * Crea un elemento. props: className, text (textContent), attrs (atributos), on (eventos).
   * Los hijos pueden ser nodos o textos (que se agregan como nodos de texto, nunca como HTML).
   */
  function el(tag, props = {}, ...children) {
    const node = document.createElement(tag);
    if (props.className) node.className = props.className;
    if (props.text !== undefined) node.textContent = String(props.text);
    Object.entries(props.attrs || {}).forEach(([name, value]) => node.setAttribute(name, value));
    Object.entries(props.on || {}).forEach(([event, handler]) => node.addEventListener(event, handler));
    children.flat().forEach((child) => {
      if (child === null || child === undefined) return;
      node.append(child instanceof Node ? child : document.createTextNode(String(child)));
    });
    return node;
  }

  /** Imagen que solo carga URLs del servidor y muestra el reemplazo si falla. */
  function safeImage(url, alt, className) {
    const image = el('img', { className, attrs: { alt, loading: 'lazy', decoding: 'async' } });
    image.addEventListener('error', () => {
      if (image.src !== PLACEHOLDER) image.src = PLACEHOLDER;
    });
    image.src = typeof url === 'string' && url.startsWith(IMAGE_URL_PREFIX) ? url : PLACEHOLDER;
    return image;
  }

  function formatBytes(bytes) {
    return bytes >= BYTES_PER_KB * BYTES_PER_KB
      ? (bytes / (BYTES_PER_KB * BYTES_PER_KB)).toFixed(2) + ' MB'
      : Math.ceil(bytes / BYTES_PER_KB) + ' KB';
  }

  /** Idempotency-Key generada en el navegador (sección 5.4). */
  function newIdempotencyKey() {
    if (window.crypto && typeof window.crypto.randomUUID === 'function') {
      return window.crypto.randomUUID();
    }
    // Respaldo para contextos no seguros (por ejemplo, http con una IP de la red): UUID v4.
    const bytes = window.crypto.getRandomValues(new Uint8Array(16));
    bytes[6] = (bytes[6] & 0x0f) | 0x40;
    bytes[8] = (bytes[8] & 0x3f) | 0x80;
    const hex = Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join('');
    return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
  }

  // ---------------------------------------------------------------------------------------------
  // HTTP y panel de respuesta
  // ---------------------------------------------------------------------------------------------

  /**
   * Envía una solicitud y muestra el resultado en el panel.
   * @param {{method: string, path: string, token?: string|null, body?: FormData|object,
   *          idempotencyKey?: string, label?: string, expected?: object}} request
   * @returns {Promise<{status: number, body: any, ok: boolean}|null>} null si hubo un error de red
   */
  async function send(request) {
    const headers = { Accept: 'application/json' };
    if (request.token) headers.Authorization = 'Bearer ' + request.token;
    if (request.idempotencyKey) headers['Idempotency-Key'] = request.idempotencyKey;
    let body = request.body;
    if (body && !(body instanceof FormData)) {
      headers['Content-Type'] = 'application/json';
      body = JSON.stringify(body);
    }

    const version = state.sessionVersion;
    const current = () => version === state.sessionVersion && (!request.isCurrent || request.isCurrent());
    if (current()) showPending(request);
    const started = performance.now();
    let response;
    let text;
    try {
      response = await fetch(request.path, { method: request.method, headers, body, credentials: 'omit' });
      text = await response.text();
    } catch (failure) {
      if (current()) showNetworkError(request, failure);
      return null;
    }
    const elapsed = Math.round(performance.now() - started);
    let json = null;
    try {
      json = text ? JSON.parse(text) : null;
    } catch (notJson) {
      json = null;
    }
    const result = {
      status: response.status,
      ok: response.ok,
      body: json,
      raw: text,
      correlationId: (json && json.correlationId) || response.headers.get('X-Correlation-Id') || '—',
    };
    if (current()) {
      showResponse(request, result, elapsed);
      // Un 401 de la sesión activa limpia también los datos privados, no solo el indicador de la barra.
      if (result.status === 401 && request.token && request.token === state.session.token && !request.expected) {
        expireSession();
      }
    }
    return result;
  }

  /** Tipo de error del formato único (5.3), o null si la respuesta no es un error. */
  function errorType(result) {
    return result && result.status >= HTTP_CLIENT_ERROR_MIN && result.body && typeof result.body.type === 'string'
      ? result.body.type : null;
  }

  function statusClass(status) {
    if (status >= HTTP_SERVER_ERROR_MIN) return 'status status-5xx';
    if (status >= HTTP_CLIENT_ERROR_MIN) return 'status status-4xx';
    if (status >= HTTP_OK_MIN) return 'status status-2xx';
    return 'status status-idle';
  }

  function requestLine(request) {
    const line = $('#response-request');
    line.replaceChildren(
      el('span', { className: 'method', text: request.method }),
      ' ',
      el('code', { text: request.path }),
      request.label ? el('span', { className: 'muted', text: ' · ' + request.label }) : null,
    );
    line.classList.remove('muted');
  }

  function showPending(request) {
    requestLine(request);
    const status = $('#response-status');
    status.className = 'status status-idle';
    status.textContent = 'Enviando…';
    $('#response-key').textContent = request.idempotencyKey || '—';
    $('#response-correlation').textContent = '—';
    $('#response-time').textContent = '—';
    $('#response-expectation').hidden = true;
  }

  function showNetworkError(request, failure) {
    const status = $('#response-status');
    status.className = 'status status-5xx';
    status.textContent = 'Sin respuesta';
    $('#response-body').textContent = 'No se pudo contactar a la API: ' + failure.message;
    $('#response-images').hidden = true;
    showExpectation(request.expected, null);
  }

  function showResponse(request, result, elapsed) {
    const status = $('#response-status');
    status.className = statusClass(result.status);
    status.textContent = String(result.status) + (errorType(result) ? ' ' + errorType(result) : '');
    $('#response-correlation').textContent = result.correlationId;
    $('#response-time').textContent = elapsed + ' ms';
    $('#response-body').textContent = result.body !== null
      ? responseForDisplay(result.body)
      : (result.raw || '(sin cuerpo)');
    showPreviews(result.body, request.path.startsWith('/api/v1/catalogo'));
    showExpectation(request.expected, result);
  }

  /** Compara con el resultado esperado de un caso rápido y lo informa. */
  function showExpectation(expected, result) {
    const box = $('#response-expectation');
    if (!expected) {
      box.hidden = true;
      return;
    }
    const wanted = expected.status + (expected.type ? ' ' + expected.type : '');
    let matches = result !== null && result.status === expected.status
      && (!expected.type || errorType(result) === expected.type);
    let extra = '';
    if (matches && typeof expected.check === 'function') {
      const verdict = expected.check(result);
      matches = verdict.ok;
      extra = verdict.message ? ' · ' + verdict.message : '';
    }
    const got = result === null ? 'sin respuesta'
      : result.status + (errorType(result) ? ' ' + errorType(result) : '');
    box.className = 'expectation ' + (matches ? 'expectation-ok' : 'expectation-bad');
    box.textContent = (matches ? 'Coincide con lo esperado: ' + wanted : 'Se esperaba ' + wanted + '; llegó ' + got)
      + extra;
    box.hidden = false;
  }

  /** Vista previa de las imágenes de la respuesta (ficha o página de resultados). */
  function showPreviews(body, large = false) {
    const box = $('#response-images');
    const previews = [];
    const add = (url, caption) => {
      if (url && previews.length < MAX_PREVIEWS) previews.push({ url, caption });
    };
    if (body && typeof body === 'object') {
      add(body.imageLargeUrl, 'Versión grande');
      add(body.imageSmallUrl, 'Miniatura');
      if (Array.isArray(body.items)) {
        body.items.forEach((item) => add(item && (large ? item.imageLargeUrl : item.imageSmallUrl), item && item.code));
      }
    }
    if (previews.length === 0) {
      box.hidden = true;
      box.replaceChildren();
      return;
    }
    box.replaceChildren(...previews.map(({ url, caption }) => el('figure', {},
      safeImage(url, caption || 'Imagen', 'preview-image'),
      el('figcaption', { text: caption || '' }))));
    box.hidden = false;
  }

  // ---------------------------------------------------------------------------------------------
  // Sesiones de prueba (sección 5.2; solo perfil dev)
  // ---------------------------------------------------------------------------------------------

  /** Pide credenciales de prueba. Devuelve la sesión, o null si no están disponibles. */
  async function requestDevSession(role, showInPanel) {
    const request = { method: 'POST', path: API.devSessions, body: { role }, label: 'Sesión de prueba: ' + ROLE_LABELS[role] };
    let result;
    if (showInPanel) {
      result = await send(request);
    } else {
      try {
        const response = await fetch(API.devSessions, {
          method: 'POST', headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
          body: JSON.stringify({ role }), credentials: 'omit',
        });
        result = { status: response.status, ok: response.ok, body: await response.json().catch(() => null) };
      } catch (failure) {
        result = null;
      }
    }
    if (result && result.status === HTTP_NOT_FOUND) {
      markDevUnavailable();
      return null;
    }
    if (!result || !result.ok || !result.body || !result.body.accessToken) {
      return null;
    }
    return sessionFromResponse(result.body);
  }

  function markDevUnavailable() {
    state.devAvailable = false;
    $('#session-buttons').hidden = true;
    $('#session-notice').hidden = false;
  }

  /** Comprueba al cargar si existe /dev/sesiones, sin crear nada: un cuerpo sin rol es 400 en dev. */
  async function probeDevSessions() {
    try {
      const response = await fetch(API.devSessions, {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, body: '{}', credentials: 'omit',
      });
      if (response.status === HTTP_NOT_FOUND) {
        markDevUnavailable();
      } else {
        state.devAvailable = true;
      }
    } catch (failure) {
      // Sin conexión: se vuelve a intentar al pulsar un botón.
    }
  }

  /** Cierra únicamente la sesión de esta página; no existe una operación backend de revocación. */
  function beginSessionChange(message) {
    const attempt = ++state.loginVersion;
    state.sessionVersion++;
    clearTimeout(expiryTimer);
    state.sessionNotice = '';
    state.session = { role: 'NONE', token: null, player: null, expiresAt: 0, initialCollection: null };
    state.roleTokens = {};
    state.lastCreate = null;
    state.search = { params: null, token: null, nextCursor: null, shown: 0 };
    $('#repeat-request').disabled = true;
    $('#results-body').replaceChildren();
    $('#results-count').textContent = 'Sin búsqueda todavía.';
    $('#load-more').hidden = true;
    $('#response-body').textContent = '{}';
    $('#response-images').replaceChildren();
    $('#response-images').hidden = true;
    $('#response-status').textContent = 'Sin solicitud';
    $('#response-status').className = 'status status-idle';
    $('#response-request').textContent = 'Todavía no se envió ninguna solicitud.';
    ['#response-correlation', '#response-key', '#response-time'].forEach((id) => { $(id).textContent = '—'; });
    $('#response-expectation').hidden = true;
    $('#player-quick-status').textContent = '';
    const form = $('#catalog-form');
    ['type', 'costMin', 'costMax', 'limit'].forEach((name) => { form.elements[name].value = ''; });
    form.elements.owned.checked = false;
    playerCatalog.clear(message);
    renderSession();
    return attempt;
  }

  /** La bienvenida usa el nombre recibido; nunca implementa las reglas de nombre del backend. */
  function acceptSession(session, source, attempt) {
    if (attempt !== state.loginVersion || !session) return;
    state.session = { ...session, source };
    expiryTimer = setTimeout(expireSession, Math.max(0, session.expiresAt - Date.now()));
    renderSession();
    $('#tab-catalog').click();
    playerCatalog.load();
  }

  function expireSession() {
    beginSessionChange('Tu sesión venció. Vuelve a iniciar sesión para consultar tus cartas.');
    state.sessionNotice = 'Tu sesión venció. Vuelve a iniciar sesión.';
    renderSession();
  }

  async function chooseSession(role) {
    const attempt = beginSessionChange();
    if (role === 'NONE') {
      googleSdk?.disableAutoSelect();
      return;
    }
    $('#session-detail').textContent = 'Iniciando sesión…';
    const session = await requestDevSession(role, true);
    if (attempt !== state.loginVersion) return;
    if (session) acceptSession(session, 'dev', attempt);
    else $('#session-detail').textContent = 'No se pudo iniciar sesión. Revisa la respuesta e intenta de nuevo.';
  }

  /**
   * Token para un caso rápido: undefined si no se puede obtener (sin perfil dev), null para "sin sesión".
   * No cambia la sesión de la barra.
   */
  async function tokenFor(role) {
    const version = state.sessionVersion;
    if (role === 'NONE') return null;
    const cached = state.roleTokens[role];
    if (cached && cached.expiresAt - TOKEN_SAFETY_MS > Date.now()) return cached.token;
    const session = await requestDevSession(role, false);
    if (version !== state.sessionVersion) return undefined;
    if (!session) {
      showMessage('Este caso necesita una sesión de prueba de ' + ROLE_LABELS[role]
        + ', y POST /api/v1/dev/sesiones no está disponible (la aplicación no corre con el perfil dev).');
      return undefined;
    }
    state.roleTokens[role] = session;
    return session.token;
  }

  function renderSession() {
    const { role, player, expiresAt } = state.session;
    if (state.session.token && expiresAt <= Date.now()) {
      expireSession();
      return;
    }
    $('#sign-out').hidden = !state.session.token;
    $('#welcome-title').textContent = player ? 'Bienvenido, ' + player.displayName : 'Bienvenido a PokéArena';
    $('#collection-status').textContent = player ? collectionMessage(state.session.initialCollection)
      : state.sessionNotice || 'Inicia sesión para explorar el catálogo y tu colección.';
    const adminTabs = ['#tab-card', '#tab-location', '#tab-search'];
    adminTabs.forEach((id) => { $(id).hidden = role === 'PLAYER'; });
    if (role === 'PLAYER' && adminTabs.some((id) => $(id).getAttribute('aria-selected') === 'true')) {
      $('#tab-catalog').click();
    }
    const badge = $('#session-badge');
    badge.textContent = ROLE_LABELS[role];
    badge.className = 'badge badge-' + role.toLowerCase();
    const detail = $('#session-detail');
    if (role === 'NONE') {
      detail.textContent = state.sessionNotice || 'Inicia sesión para acceder a tus cartas.';
    } else {
      const remaining = expiresAt - Date.now();
      const minutes = Math.max(0, Math.ceil(remaining / (MS_PER_SECOND * SECONDS_PER_MINUTE)));
      const who = player?.displayName ?? '';
      detail.textContent = remaining > 0
        ? who + ' · vence en ' + minutes + ' min'
        : who + ' · sesión vencida: elija el rol otra vez';
    }
    $$('#session-buttons [data-session]').forEach((button) => {
      button.setAttribute('aria-pressed', String(button.dataset.session === role));
    });
  }

  /** Mensaje de la interfaz en el panel (no viene del servidor). */
  function showMessage(message) {
    const status = $('#response-status');
    status.className = 'status status-4xx';
    status.textContent = 'Aviso';
    $('#response-body').textContent = message;
    $('#response-images').hidden = true;
    $('#response-expectation').hidden = true;
  }

  // ---------------------------------------------------------------------------------------------
  // Imágenes de prueba con canvas
  // ---------------------------------------------------------------------------------------------

  /** PNG del tamaño exacto pedido, con un diseño simple para reconocerlo en las vistas previas. */
  function generatePng({ width, height }, title) {
    const canvas = document.createElement('canvas');
    canvas.width = width;
    canvas.height = height;
    const context = canvas.getContext('2d');
    const gradient = context.createLinearGradient(0, 0, width, height);
    gradient.addColorStop(0, '#1d4ed8');
    gradient.addColorStop(1, '#db2777');
    context.fillStyle = gradient;
    context.fillRect(0, 0, width, height);
    context.fillStyle = 'rgba(255, 255, 255, 0.15)';
    context.beginPath();
    context.arc(width * 0.7, height * 0.3, Math.min(width, height) * 0.35, 0, Math.PI * 2);
    context.fill();
    context.fillStyle = '#ffffff';
    context.textAlign = 'center';
    const fontSize = Math.round(Math.min(width, height) / 12);
    context.font = `bold ${fontSize}px sans-serif`;
    context.fillText(title, width / 2, height / 2);
    context.font = `${Math.round(fontSize * 0.6)}px sans-serif`;
    context.fillText(`${width} × ${height}`, width / 2, height / 2 + fontSize * 1.2);
    return new Promise((resolve, reject) => canvas.toBlob(
      (blob) => (blob ? resolve(blob) : reject(new Error('No se pudo generar la imagen'))), 'image/png'));
  }

  /** PNG válido de 600 × 840 con relleno hasta superar los 5 MB: solo falla por peso. */
  async function oversizedPng() {
    const png = await generatePng(CARD_IMAGE, 'Más de 5 MB');
    return new Blob([png, new Uint8Array(MAX_IMAGE_BYTES)], { type: 'image/png' });
  }

  /** Un archivo de texto con nombre y tipo de PNG: el servidor detecta el formato por el contenido. */
  function textAsPng() {
    return new Blob(['Esto es texto, no una imagen.'], { type: 'image/png' });
  }

  // ---------------------------------------------------------------------------------------------
  // Formularios: utilidades comunes
  // ---------------------------------------------------------------------------------------------

  function fillOptions() {
    $$('select[data-options]').forEach((select) => {
      const values = select.dataset.options === 'types' ? TYPES : RARITIES;
      const options = values.map(([code, name]) => el('option', { text: `${code} · ${name}`, attrs: { value: code } }));
      if (select.dataset.empty) {
        options.unshift(el('option', { text: select.dataset.empty, attrs: { value: '' } }));
      }
      select.replaceChildren(...options);
    });
  }

  /** Imagen elegida en el formulario: un archivo del usuario o la generada. */
  function chosenImage(form, kind) {
    const file = form.elements.image.files[0];
    if (file) return { blob: file, name: file.name };
    return state.generated[kind] || null;
  }

  function wireImageInputs() {
    $$('[data-generate]').forEach((button) => {
      const kind = button.dataset.generate;
      const form = button.closest('form');
      button.addEventListener('click', async () => {
        const size = kind === 'card' ? CARD_IMAGE : LOCATION_IMAGE;
        const blob = await generatePng(size, kind === 'card' ? 'Carta' : 'Ubicación');
        state.generated[kind] = { blob, name: kind === 'card' ? 'carta.png' : 'ubicacion.png' };
        form.elements.image.value = '';
        $(`[data-file-note="${kind}"]`).textContent =
          `Imagen generada: ${size.width} × ${size.height}, ${formatBytes(blob.size)}.`;
      });
      form.elements.image.addEventListener('change', () => {
        const file = form.elements.image.files[0];
        state.generated[kind] = null;
        $(`[data-file-note="${kind}"]`).textContent = file
          ? `Archivo: ${file.name}, ${formatBytes(file.size)}.`
          : 'Sin imagen seleccionada.';
      });
    });
  }

  /** Envía una creación (POST multipart) con una clave nueva y la recuerda para repetirla. */
  async function sendCreate({ path, token, form, label, expected, idempotencyKey }) {
    // Una imagen generada por un caso rápido puede terminar después de cerrar o cambiar la sesión.
    if (token && token !== state.session.token
        && !Object.values(state.roleTokens).some((session) => session.token === token)) return null;
    const request = {
      method: 'POST', path, token, body: form, label, expected,
      idempotencyKey: idempotencyKey || newIdempotencyKey(),
    };
    state.lastCreate = request;
    $('#repeat-request').disabled = false;
    return send(request);
  }

  function repeatLastCreate() {
    if (!state.lastCreate) return;
    // Mismo cuerpo (el mismo FormData), misma clave y misma sesión: debe volver la respuesta guardada.
    send({ ...state.lastCreate, label: 'Repetición con la misma clave', expected: null });
  }

  // ---------------------------------------------------------------------------------------------
  // Crear carta (2.1.1)
  // ---------------------------------------------------------------------------------------------

  const VALID_CARD = { name: 'Charmander', cost: '3', power: '4', type: 'FUE', rarity: 'COMMON', ability: 'HAB-000' };

  /** Arma el multipart. Un campo con valor null no se envía (para probar MISSING_FIELD). */
  function cardForm(fields, image) {
    const form = new FormData();
    Object.entries(fields).forEach(([name, value]) => {
      if (value !== null && value !== undefined) form.append(name, value);
    });
    if (image) form.append('image', image.blob, image.name);
    return form;
  }

  function wireCardForm() {
    const form = $('#card-form');
    form.addEventListener('submit', (event) => {
      event.preventDefault();
      const fields = {};
      ['name', 'cost', 'power', 'type', 'rarity', 'ability'].forEach((name) => {
        fields[name] = form.elements[name].value.trim();
      });
      sendCreate({
        path: API.cards, token: state.session.token, form: cardForm(fields, chosenImage(form, 'card')),
        label: 'Formulario',
      });
    });
  }

  async function quickCard(label, expected, { role = 'ADMIN', fields = {}, image }) {
    const token = await tokenFor(role);
    if (token === undefined) return null;
    const imageFile = image === undefined
      ? { blob: await generatePng(CARD_IMAGE, 'Carta'), name: 'carta.png' }
      : await image();
    return sendCreate({
      path: API.cards, token, label, expected,
      form: cardForm({ ...VALID_CARD, name: VALID_CARD.name + ' ' + Date.now().toString(36), ...fields }, imageFile),
    });
  }

  async function quickCardRetry() {
    const token = await tokenFor('ADMIN');
    if (token === undefined) return;
    const form = cardForm({ ...VALID_CARD, name: 'Reintento ' + Date.now().toString(36) },
      { blob: await generatePng(CARD_IMAGE, 'Reintento'), name: 'carta.png' });
    const idempotencyKey = newIdempotencyKey();
    const first = await sendCreate({ path: API.cards, token, form, idempotencyKey, label: 'Reintento: primer envío' });
    if (!first || first.status !== 201) return;
    await sendCreate({
      path: API.cards, token, form, idempotencyKey, label: 'Reintento: segundo envío, misma clave',
      expected: {
        status: 201,
        check: (second) => ({
          ok: second.body && second.body.code === first.body.code,
          message: second.body && second.body.code === first.body.code
            ? 'misma carta ' + first.body.code + ' en los dos envíos: no se creó otra ni se consumió otro código'
            : 'el segundo envío devolvió otra carta',
        }),
      },
    });
  }

  const CARD_QUICK_TESTS = [
    { label: 'Carta válida (600 × 840)', expected: { status: 201 }, run: (e) => quickCard('Carta válida', e, {}) },
    { label: 'Costo 11', expected: { status: 400, type: 'INVALID_FIELD' }, run: (e) => quickCard('Costo 11', e, { fields: { cost: '11' } }) },
    { label: 'Sin habilidad', expected: { status: 400, type: 'MISSING_FIELD' }, run: (e) => quickCard('Sin habilidad', e, { fields: { ability: null } }) },
    { label: 'Habilidad inexistente', expected: { status: 400, type: 'UNKNOWN_ABILITY' }, run: (e) => quickCard('Habilidad inexistente', e, { fields: { ability: 'HAB-999' } }) },
    {
      label: 'Proporción incorrecta (600 × 600)', expected: { status: 400, type: 'INVALID_IMAGE_RATIO' },
      run: (e) => quickCard('Proporción incorrecta', e, { image: async () => ({ blob: await generatePng(SQUARE_IMAGE, 'Cuadrada'), name: 'cuadrada.png' }) }),
    },
    {
      label: 'Resolución baja (300 × 420)', expected: { status: 400, type: 'IMAGE_RESOLUTION_TOO_LOW' },
      run: (e) => quickCard('Resolución baja', e, { image: async () => ({ blob: await generatePng(LOW_RESOLUTION_IMAGE, 'Baja'), name: 'baja.png' }) }),
    },
    {
      label: 'Imagen mayor a 5 MB', expected: { status: 400, type: 'IMAGE_TOO_LARGE' },
      run: (e) => quickCard('Imagen mayor a 5 MB', e, { image: async () => ({ blob: await oversizedPng(), name: 'grande.png' }) }),
    },
    {
      label: 'Archivo de texto como .png', expected: { status: 400, type: 'INVALID_IMAGE_FORMAT' },
      run: (e) => quickCard('Texto como .png', e, { image: async () => ({ blob: textAsPng(), name: 'texto.png' }) }),
    },
    { label: 'Reintento con la misma clave', expected: { status: 201, note: 'misma respuesta, sin carta nueva' }, run: () => quickCardRetry() },
    { label: 'Sin sesión', expected: { status: 401, type: 'NOT_AUTHENTICATED' }, run: (e) => quickCard('Sin sesión', e, { role: 'NONE' }) },
    { label: 'Como jugador', expected: { status: 403, type: 'FORBIDDEN' }, run: (e) => quickCard('Como jugador', e, { role: 'PLAYER' }) },
  ];

  // ---------------------------------------------------------------------------------------------
  // Crear ubicación (2.2.1)
  // ---------------------------------------------------------------------------------------------

  /** JSON del campo effect según el tipo elegido, o null sin efecto (sección 7.2). */
  function effectFromForm(form) {
    const code = form.elements.effectCode.value;
    const integer = (name) => Number.parseInt(form.elements[name].value, 10);
    switch (code) {
      case 'TYPE_BONUS':
        return { code, parameters: { type: form.elements.tbType.value, amount: integer('tbAmount') } };
      case 'COST_MODIFIER': {
        const parameters = { amount: integer('cmAmount') };
        if (form.elements.cmType.value) parameters.affectedType = form.elements.cmType.value;
        return { code, parameters };
      }
      case 'TURN_RESTRICTION':
        return {
          code,
          parameters: { turns: $$('input[name="turn"]:checked', form).map((box) => Number.parseInt(box.value, 10)) },
        };
      default:
        return null;
    }
  }

  function renderEffectEditor(form) {
    const code = form.elements.effectCode.value;
    $$('[data-effect]', form).forEach((fieldset) => {
      fieldset.hidden = fieldset.dataset.effect !== code;
    });
    const effect = effectFromForm(form);
    $('#effect-preview').textContent = effect === null
      ? '(sin efecto: el campo no se envía)'
      : JSON.stringify(effect);
  }

  function renderEnergy(form) {
    const mode = form.elements.extraEnergyMode.value;
    const amount = form.elements.extraEnergyAmount;
    const hint = $('[data-energy-hint]', form);
    amount.disabled = mode === 'NONE';
    if (mode === 'NONE') {
      amount.value = '';
      hint.textContent = 'no aplica con NONE';
    } else {
      hint.textContent = mode === 'FIXED' ? 'obligatorio, 0 a 3' : 'obligatorio, 0 a 2';
      if (amount.value === '') amount.value = '1';
    }
  }

  /** Multipart de la ubicación. Un campo con valor null no se envía. */
  function locationForm(fields, image) {
    return cardForm(fields, image);
  }

  function wireLocationForm() {
    const form = $('#location-form');
    $('[data-turns]', form).replaceChildren(...TURNS.map((turn) => el('label', { className: 'turn' },
      el('input', { attrs: { type: 'checkbox', name: 'turn', value: String(turn) } }), ' Turno ' + turn)));
    $$('[name="turn"]', form).slice(0, 1).forEach((box) => { box.checked = true; });
    form.addEventListener('input', () => renderEffectEditor(form));
    form.addEventListener('change', () => renderEffectEditor(form));
    form.elements.extraEnergyMode.addEventListener('change', () => renderEnergy(form));
    renderEffectEditor(form);
    renderEnergy(form);

    form.addEventListener('submit', (event) => {
      event.preventDefault();
      const mode = form.elements.extraEnergyMode.value;
      const effect = effectFromForm(form);
      const amount = form.elements.extraEnergyAmount.value.trim();
      const fields = {
        name: form.elements.name.value.trim(),
        description: form.elements.description.value.trim(),
        cardLimit: form.elements.cardLimit.value.trim(),
        extraEnergyMode: mode,
        // Con NONE el monto se omite (vale 0); con FIXED o PER_CARD, vacío también se omite: MISSING_FIELD.
        extraEnergyAmount: mode === 'NONE' || amount === '' ? null : amount,
        effect: effect === null ? null : JSON.stringify(effect),
      };
      sendCreate({
        path: API.locations, token: state.session.token, form: locationForm(fields, chosenImage(form, 'location')),
        label: 'Formulario',
      });
    });
  }

  const VALID_LOCATION = {
    name: 'Volcán Ardiente', description: 'Potencia a las cartas de fuego.', cardLimit: '4',
    extraEnergyMode: 'NONE', extraEnergyAmount: null,
    effect: JSON.stringify({ code: 'TYPE_BONUS', parameters: { type: 'FUE', amount: 2 } }),
  };

  async function quickLocation(label, expected, fields) {
    const token = await tokenFor('ADMIN');
    if (token === undefined) return null;
    const image = { blob: await generatePng(LOCATION_IMAGE, 'Ubicación'), name: 'ubicacion.png' };
    return sendCreate({ path: API.locations, token, label, expected, form: locationForm({ ...VALID_LOCATION, ...fields }, image) });
  }

  const LOCATION_QUICK_TESTS = [
    { label: 'Ubicación válida (800 × 600)', expected: { status: 201 }, run: (e) => quickLocation('Ubicación válida', e, {}) },
    { label: 'Límite de cartas 6', expected: { status: 400, type: 'INVALID_FIELD' }, run: (e) => quickLocation('Límite 6', e, { cardLimit: '6' }) },
    {
      label: 'Efecto inexistente', expected: { status: 400, type: 'INVALID_EFFECT' },
      run: (e) => quickLocation('Efecto inexistente', e, { effect: JSON.stringify({ code: 'LLUVIA', parameters: {} }) }),
    },
  ];

  // ---------------------------------------------------------------------------------------------
  // Buscar carta (2.1.2)
  // ---------------------------------------------------------------------------------------------

  const SEARCH_FIELDS = ['code', 'name', 'cost', 'costMin', 'costMax', 'power', 'powerMin', 'powerMax',
    'type', 'rarity', 'ability', 'abilityTrigger', 'abilityEffect', 'limit'];

  function searchPath(params, cursor) {
    const query = new URLSearchParams(params);
    if (cursor) query.set('cursor', cursor);
    const text = query.toString();
    return API.cards + (text ? '?' + text : '');
  }

  /** Busca desde el principio (append = false) o la página siguiente (append = true). */
  async function runSearch({ params, token, append, label, expected }) {
    const version = state.sessionVersion;
    const cursor = append ? state.search.nextCursor : null;
    const result = await send({ method: 'GET', path: searchPath(params, cursor), token, label, expected });
    if (version !== state.sessionVersion) return null;
    if (!append) {
      state.search = { params, token, nextCursor: null, shown: 0 };
      $('#results-body').replaceChildren();
    }
    if (!result || result.status !== 200 || !result.body || !Array.isArray(result.body.items)) {
      $('#load-more').hidden = true;
      $('#results-count').textContent = append ? 'No se pudo cargar la página siguiente: vea el panel de respuesta.'
        : 'La búsqueda no devolvió resultados válidos: vea el panel de respuesta.';
      return result;
    }
    $('#results-body').append(...result.body.items.map(resultRow));
    state.search.shown += result.body.items.length;
    state.search.nextCursor = result.body.nextCursor;
    $('#load-more').hidden = !result.body.nextCursor;
    $('#results-count').textContent = state.search.shown === 0
      ? 'Sin resultados (200 con lista vacía).'
      : `${state.search.shown} carta(s) mostradas` + (result.body.nextCursor ? ' · hay más' : ' · no hay más');
    return result;
  }

  function resultRow(card) {
    const ability = card.ability || {};
    const status = String(card.imageStatus || '');
    return el('tr', {},
      el('td', {}, safeImage(card.imageSmallUrl, 'Miniatura de ' + (card.code || ''), 'thumb')),
      el('td', {}, el('code', { text: card.code })),
      el('td', { text: card.name }),
      el('td', { text: card.type ? card.type.code : '' }),
      el('td', { text: card.rarity ? card.rarity.name : '' }),
      el('td', { className: 'num', text: card.cost }),
      el('td', { className: 'num', text: card.power }),
      el('td', {}, el('code', { text: ability.code || '' }), ' ', el('span', { className: 'muted', text: ability.name || '' })),
      el('td', {}, el('span', { className: 'badge badge-image-' + status.toLowerCase().replace('_', '-'), text: status })),
      el('td', { text: card.available ? 'Sí' : 'No' }),
    );
  }

  function wireSearchForm() {
    const form = $('#search-form');
    form.addEventListener('submit', (event) => {
      event.preventDefault();
      const params = {};
      SEARCH_FIELDS.forEach((name) => {
        const value = form.elements[name].value.trim();
        if (value !== '') params[name] = value;
      });
      runSearch({ params, token: state.session.token, append: false, label: 'Formulario' });
    });
    $('#load-more').addEventListener('click', () => runSearch({
      params: state.search.params, token: state.search.token, append: true, label: 'Cargar más (cursor)',
    }));
  }

  async function quickSearch(label, expected, params) {
    const token = await tokenFor('ADMIN');
    if (token === undefined) return null;
    return runSearch({ params, token, append: false, label, expected });
  }

  const SEARCH_QUICK_TESTS = [
    {
      label: 'Búsqueda sin resultados', expected: { status: 200, check: (r) => ({ ok: r.body.items.length === 0, message: 'items vacío, sin error' }) },
      run: (e) => quickSearch('Sin resultados', e, { name: 'no-existe-' + Date.now().toString(36) }),
    },
    {
      label: 'Rango invertido (costo 5 a 1)', expected: { status: 400, type: 'INVALID_SEARCH_CRITERIA' },
      run: (e) => quickSearch('Rango invertido', e, { costMin: '5', costMax: '1' }),
    },
  ];

  // ---------------------------------------------------------------------------------------------
  // Catálogo y bienvenida del jugador (US 43 y presentación de US 35 y 42)
  // ---------------------------------------------------------------------------------------------

  /** Cada tarjeta usa la imagen grande; los textos del servidor siguen siendo nodos de texto. */
  function catalogCard(card) {
    const ability = card.ability || {};
    return el('article', { className: 'catalog-card' },
      safeImage(card.imageLargeUrl, card.name || card.code, 'catalog-image'),
      el('div', { className: 'catalog-card-body' },
        el('code', { text: card.code }), el('h3', { text: card.name }),
        el('p', { className: 'muted', text: [card.type?.name, card.rarity?.name].filter(Boolean).join(' · ') }),
        el('p', { text: 'Costo: ' + card.cost + ' · Poder: ' + card.power }),
        el('p', { text: 'Habilidad: ' + ability.code + ' · ' + ability.name }),
        el('p', { className: card.owned ? 'ownership ownership-owned' : 'ownership',
          text: (card.owned ? 'En tu colección' : 'No la posees') + ' · Copias: ' + card.copies })));
  }

  function renderCatalog(view) {
    catalogFilterForm?.update();
    $('#catalog-cards').replaceChildren(...view.items.map(catalogCard));
    $('#catalog-cards').setAttribute('aria-busy', String(view.loading));
    $('#catalog-message').textContent = view.message;
    $('#catalog-message').className = 'catalog-message' + (view.error ? ' catalog-error' : '');
    const more = $('#catalog-more');
    more.hidden = !view.nextCursor;
    more.disabled = view.loading;
    more.textContent = view.loading ? 'Cargando…' : 'Cargar más';
    $('#catalog-form button[type="submit"]').disabled = view.loading;
  }

  function wireCatalog() {
    playerCatalog = new PlayerCatalog({ send, getSession: () => state.session, onExpired: expireSession,
      render: renderCatalog });
    catalogFilterForm = new CatalogFilterForm($('#catalog-form'), playerCatalog, $('#catalog-filter-status'));
    $('#catalog-more').addEventListener('click', () => playerCatalog.load({}, true));
    $('#sign-out').addEventListener('click', () => chooseSession('NONE'));
    $('#quick-second-login').addEventListener('click', repeatPlayerLogin);
    $('#quick-invalid-google').addEventListener('click', async () => {
      const version = state.sessionVersion;
      const result = await send({ method: 'POST', path: API.sessions,
        body: { provider: 'google', idToken: 'token-invalido-de-prueba' },
        label: 'Token de Google inválido', expected: { status: 401, type: 'INVALID_IDENTITY_TOKEN' } });
      if (version === state.sessionVersion && errorType(result) === 'UNSUPPORTED_PROVIDER') {
        $('#response-expectation').hidden = true;
        $('#player-quick-status').textContent = 'Caso no ejecutable: Google no está habilitado en el servidor.';
      }
    });
  }

  /** Solo confirma ALREADY_DELIVERED si el backend lo devuelve, sin inferirlo del catálogo. */
  async function repeatPlayerLogin() {
    if (state.session.source !== 'dev' || state.session.role !== 'PLAYER') {
      $('#player-quick-status').textContent = 'Con Google, cierra e inicia sesión nuevamente para comprobar el estado recibido.';
      return;
    }
    await chooseSession('PLAYER');
    if (state.session.source !== 'dev') return;
    $('#player-quick-status').textContent = state.session.initialCollection?.status === 'ALREADY_DELIVERED'
      ? 'El backend informó ALREADY_DELIVERED: la colección ya se había entregado.'
      : 'La sesión dev no informó ALREADY_DELIVERED; no se puede confirmar ese criterio desde esta respuesta.';
  }

  // ---------------------------------------------------------------------------------------------
  // Google: transporte al backend existente; la configuración pública pertenece a otro integrante.
  // ---------------------------------------------------------------------------------------------

  /** Recibe exclusivamente el token que entrega el callback oficial de Google Identity Services. */
  async function receiveGoogleCredential(response) {
    const attempt = googleAttempt;
    if (attempt === null || attempt !== state.loginVersion || typeof response?.credential !== 'string' || !response.credential) return;
    googleAttempt = null;
    $('#google-notice').textContent = 'Iniciando sesión…';
    const result = await send({ ...googleSessionRequest(response.credential), label: 'Inicio de sesión con Google',
      isCurrent: () => attempt === state.loginVersion });
    if (attempt !== state.loginVersion) return;
    const session = result?.ok ? sessionFromResponse(result.body) : null;
    if (session) {
      acceptSession(session, 'google', attempt);
      $('#google-notice').textContent = '';
    } else {
      $('#google-notice').textContent = result?.body?.detail || 'No se pudo iniciar sesión. Intenta de nuevo.';
    }
  }

  /** Punto de integración frontend: no presupone una ruta ni un esquema de configuración inexistentes. */
  async function configureGoogle(clientId) {
    if (googleConfigured || typeof clientId !== 'string' || !clientId.trim()) return;
    googleConfigured = true;
    $('#google-notice').textContent = 'Preparando ingreso con Google…';
    try {
      if (!window.google?.accounts?.id) {
        await new Promise((resolve, reject) => {
          const script = document.createElement('script');
          script.src = 'https://accounts.google.com/gsi/client';
          script.async = true;
          script.onload = resolve;
          script.onerror = reject;
          document.head.append(script);
        });
      }
      googleSdk = window.google?.accounts?.id;
      if (!googleSdk) throw new Error('SDK de Google no disponible');
      $('#google-signin').replaceChildren();
      mountGoogleButton(googleSdk, clientId, $('#google-signin'), receiveGoogleCredential, () => {
        googleAttempt = beginSessionChange();
      });
      $('#google-notice').textContent = '';
    } catch (failure) {
      googleConfigured = false;
      $('#google-notice').textContent = 'No se pudo cargar Google. Intenta de nuevo más tarde.';
    }
  }

  // ---------------------------------------------------------------------------------------------
  // Casos rápidos, pestañas e inicio
  // ---------------------------------------------------------------------------------------------

  function renderQuickTests(kind, tests) {
    const container = $(`[data-quick="${kind}"]`);
    container.replaceChildren(...tests.map((test) => {
      const wanted = test.expected.status + (test.expected.type ? ' ' + test.expected.type : '')
        + (test.expected.note ? ' · ' + test.expected.note : '');
      const button = el('button', { className: 'quick-button', attrs: { type: 'button' } },
        el('span', { className: 'quick-label', text: test.label }),
        el('span', { className: 'quick-expected', text: '→ ' + wanted }));
      button.addEventListener('click', async () => {
        button.disabled = true;
        try {
          await test.run(test.expected);
        } finally {
          button.disabled = false;
        }
      });
      return button;
    }));
  }

  function wireTabs() {
    const tabs = $$('[role="tab"]');
    const select = (tab) => {
      tabs.forEach((other) => {
        const selected = other === tab;
        other.setAttribute('aria-selected', String(selected));
        other.tabIndex = selected ? 0 : -1;
        $('#' + other.getAttribute('aria-controls')).hidden = !selected;
      });
    };
    tabs.forEach((tab) => {
      tab.addEventListener('click', () => select(tab));
      tab.addEventListener('keydown', (event) => {
        const step = event.key === 'ArrowRight' ? 1 : event.key === 'ArrowLeft' ? -1 : 0;
        if (step !== 0) {
          const visible = tabs.filter((item) => !item.hidden);
          const position = visible.indexOf(tab);
          const next = visible[(position + step + visible.length) % visible.length];
          select(next);
          next.focus();
        }
      });
    });
  }

  function start() {
    fillOptions();
    wireTabs();
    $('#tab-catalog').click();
    wireImageInputs();
    wireCardForm();
    wireLocationForm();
    wireSearchForm();
    wireCatalog();
    // El futuro adaptador del endpoint público debe entregar este clientId; no se inventa su contrato.
    document.addEventListener('pokearena:google-config', (event) => configureGoogle(event.detail?.clientId));
    renderQuickTests('catalog', catalogQuickTests(catalogFilterForm));
    renderQuickTests('card', CARD_QUICK_TESTS);
    renderQuickTests('location', LOCATION_QUICK_TESTS);
    renderQuickTests('search', SEARCH_QUICK_TESTS);
    $$('#session-buttons [data-session]').forEach((button) => {
      button.addEventListener('click', () => chooseSession(button.dataset.session));
    });
    $('#repeat-request').addEventListener('click', repeatLastCreate);
    renderSession();
    setInterval(renderSession, SESSION_REFRESH_MS);
    probeDevSessions();
  }

  start();
})();
