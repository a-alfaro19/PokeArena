/*
 * Propósito: estado de la vista del jugador, independiente del DOM y de frameworks (US 43, sección 11).
 * Responsabilidades: conservar filtros y cursores, descartar respuestas antiguas y presentar únicamente
 * estados de colección entregados por el backend. Las sesiones y las solicitudes HTTP se inyectan.
 */

/** Traduce la respuesta existente; nunca inventa el nombre ni el estado de colección del jugador. */
export function sessionFromResponse(body, now = Date.now()) {
  if (!body || !body.accessToken || !body.player || !['PLAYER', 'ADMIN'].includes(body.player.role)
      || !Number.isFinite(body.expiresIn) || body.expiresIn <= 0) return null;
  return {
    role: body.player.role, token: body.accessToken, player: body.player,
    expiresAt: now + body.expiresIn * 1000,
    initialCollection: body.initialCollection || null,
    firstLogin: body.firstLogin,
  };
}

/** La ausencia de datos en dev es distinta de una entrega pendiente: no se cuentan cartas para inferirla. */
export function collectionMessage(collection) {
  if (!collection) return 'Esta sesión no informa el estado de la colección inicial.';
  switch (collection.status) {
    case 'DELIVERED': return 'Tu colección inicial fue entregada: 15 cartas y 1.000 cubos.';
    case 'ALREADY_DELIVERED': return 'Ya recibiste tu colección inicial; este ingreso no vuelve a entregarla.';
    case 'PENDING_NO_CARDS':
    case 'PENDING_ERROR': return collection.message || 'La colección inicial está pendiente. Vuelve a iniciar sesión más tarde.';
    default: return 'El servidor informó un estado de colección no reconocido.';
  }
}

/** Mantiene las consultas relativas y el mismo conjunto de filtros al seguir nextCursor. */
export function catalogPath(filters, cursor = null) {
  const query = new URLSearchParams(filters);
  if (cursor) query.set('cursor', cursor);
  return '/api/v1/catalogo' + (query.size ? '?' + query.toString() : '');
}

/** Evita que tokens de Google o PokéArena aparezcan en el panel de diagnóstico. */
export function responseForDisplay(body) {
  return JSON.stringify(body, (key, value) => ['accessToken', 'idToken'].includes(key) ? '[credencial oculta]' : value, 2);
}

/** El token externo solo se envía al inicio de sesión; las demás solicitudes usan el accessToken propio. */
export function googleSessionRequest(credential) {
  return { method: 'POST', path: '/api/v1/sesiones', body: { provider: 'google', idToken: credential } };
}

/** Configura únicamente el SDK oficial; quien provee clientId sigue siendo la integración de sesiones. */
export function mountGoogleButton(sdk, clientId, container, callback, onClick) {
  if (typeof clientId !== 'string' || !clientId.trim() || !sdk) return false;
  sdk.initialize({ client_id: clientId, callback, auto_select: false });
  sdk.renderButton(container, { type: 'standard', theme: 'outline', size: 'large', text: 'signin_with',
    locale: 'es', click_listener: onClick });
  return true;
}

/** Control de la vista: cada carga pertenece a una sesión y a una revisión de los filtros. */
export class PlayerCatalog {
  constructor({ send, getSession, onExpired, render, now = Date.now }) {
    this.send = send;
    this.getSession = getSession;
    this.onExpired = onExpired;
    this.render = render;
    this.now = now;
    this.version = 0;
    this.clear();
  }

  /** Invalida solicitudes pendientes y elimina toda propiedad del jugador anterior. */
  clear(message = 'Inicia sesión para consultar el catálogo.') {
    this.version++;
    this.view = { items: [], filters: {}, nextCursor: null, loading: false, message, error: false };
    this.render(this.view);
  }

  /** Nuevos filtros reemplazan resultados; un error al cargar más conserva cartas y cursor para reintentar. */
  async load(filters = {}, append = false, expected = null) {
    if (append && (this.view.loading || !this.view.nextCursor)) return null;
    const session = this.getSession();
    if (!session.token) {
      this.clear('Inicia sesión para consultar el catálogo.');
      return null;
    }
    if (session.expiresAt <= this.now()) {
      this.onExpired();
      return null;
    }
    const version = ++this.version;
    const token = session.token;
    const activeFilters = append ? this.view.filters : { ...filters };
    const cursor = append ? this.view.nextCursor : null;
    const current = () => version === this.version && this.getSession().token === token;
    this.view = { items: append ? this.view.items : [], filters: activeFilters,
      nextCursor: cursor, loading: true, message: 'Cargando cartas…', error: false };
    this.render(this.view);
    let result;
    try {
      result = await this.send({ method: 'GET', path: catalogPath(activeFilters, cursor), token,
        label: append ? 'Cargar más cartas' : 'Catálogo del jugador', expected, isCurrent: current });
    } catch (failure) {
      // Un transporte que rechace la promesa se presenta igual que un error de red del adaptador HTTP.
      result = null;
    }
    if (!current()) return null;
    if (result && result.status === 401) {
      this.onExpired();
      return result;
    }
    if (this.getSession().expiresAt <= this.now()) {
      this.onExpired();
      return null;
    }

    // Los errores incluyen valores permitidos, sin ocultar los resultados ya cargados al paginar.
    this.view.loading = false;
    if (!result || result.status !== 200 || !Array.isArray(result.body?.items)
        || !(result.body.nextCursor === null || typeof result.body.nextCursor === 'string')) {
      const error = result?.body;
      const allowed = Array.isArray(error?.allowedValues) ? ' Valores permitidos: ' + error.allowedValues.join(', ') + '.' : '';
      this.view.message = (error?.detail || (result ? 'La API no devolvió una página válida.' : 'No se pudo conectar. Intenta de nuevo.')) + allowed;
      this.view.error = true;
    } else {
      this.view.items = [...this.view.items, ...result.body.items];
      this.view.nextCursor = result.body.nextCursor;
      this.view.message = this.view.items.length ? this.view.items.length + ' carta(s) mostradas'
        + (this.view.nextCursor ? ' · hay más resultados.' : ' · no hay más resultados.')
        : 'No hay cartas que coincidan con los filtros.';
    }
    this.render(this.view);
    return result;
  }
}

/** Conecta el formulario real con la consulta; editar no modifica los filtros usados al paginar. */
export class CatalogFilterForm {
  constructor(form, catalog, status) {
    this.form = form;
    this.catalog = catalog;
    this.status = status;
    form.addEventListener('input', () => this.update());
    form.addEventListener('change', () => this.update());
    form.addEventListener('submit', (event) => {
      event.preventDefault();
      this.apply();
    });
    form.addEventListener('reset', () => {
      // Espera a que el navegador restaure los controles antes de consultar desde la primera página.
      queueMicrotask(() => this.applyQuick({}));
    });
    this.update();
  }

  /** Lee exactamente los valores visibles; omite extremos vacíos y no envía owned=false. */
  read() {
    const filters = {};
    for (const name of ['type', 'costMin', 'costMax', 'limit']) {
      const value = this.form.elements[name].value.trim();
      if (value) filters[name] = value;
    }
    if (this.form.elements.owned.checked) filters.owned = 'true';
    return filters;
  }

  /** Una prueba rápida reemplaza también los controles, incluido un tipo inválido visible como tal. */
  applyQuick(filters, expected = null) {
    const type = this.form.elements.type;
    if (filters.type && !Array.from(type.options).some((option) => option.value === filters.type)) {
      const option = this.form.ownerDocument.createElement('option');
      option.value = filters.type;
      option.textContent = filters.type + ' · tipo inválido de prueba';
      type.add(option);
    }
    for (const name of ['type', 'costMin', 'costMax', 'limit']) {
      this.form.elements[name].value = filters[name] || '';
    }
    this.form.elements.owned.checked = filters.owned === 'true';
    return this.apply(expected);
  }

  /** Tanto el envío manual como los casos rápidos consultan a partir del mismo formulario. */
  apply(expected = null) {
    const request = this.catalog.load(this.read(), false, expected);
    this.update();
    return request;
  }

  /** El resumen representa la consulta activa, aunque los controles tengan cambios aún no enviados. */
  update() {
    const applied = this.catalog.view.filters;
    const edited = this.read();
    const dirty = ['type', 'costMin', 'costMax', 'limit', 'owned']
      .some((name) => (edited[name] || '') !== (applied[name] || ''));
    const summary = [
      'Tipo: ' + (applied.type || 'todos'),
      'costo: ' + (applied.costMin || '0') + ' a ' + (applied.costMax || '10'),
      applied.owned === 'true' ? 'solo mis cartas' : 'todo el catálogo',
      'por página: ' + (applied.limit || 'predeterminado'),
    ].join(' · ');
    this.status.textContent = 'Filtros de la consulta: ' + summary + '.'
      + (dirty ? ' Hay cambios sin aplicar en el formulario. Pulsa Aplicar filtros.' : ' El formulario coincide con la consulta.');
  }
}

/** Los botones de la página y las pruebas usan estas mismas acciones, no llamadas directas al catálogo. */
export function catalogQuickTests(form) {
  return [
    { label: 'Tipo inexistente', expected: { status: 400, type: 'INVALID_FIELD' },
      run: (expected) => form.applyQuick({ type: 'INVALID' }, expected) },
    { label: 'Límite 0', expected: { status: 400, type: 'INVALID_PAGINATION' },
      run: (expected) => form.applyQuick({ limit: '0' }, expected) },
    { label: 'Solo mis cartas', expected: { status: 200 },
      run: (expected) => form.applyQuick({ owned: 'true' }, expected) },
  ];
}
