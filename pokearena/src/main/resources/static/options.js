/*
 * Propósito: opciones de los filtros y formularios que la interfaz pide a la API antes de usarlos
 * (GET /api/v1/opciones/catalogo y GET /api/v1/opciones/creacion; Anexos A.1, A.2, A.3 y A.6; y, desde el
 * Sprint 3, GET /api/v1/opciones/juego, sección 7.4).
 * Responsabilidades: validar la forma de las respuestas, armar los textos de rangos y requisitos de imagen,
 * y construir el JSON del campo effect a partir de la descripción de parámetros que entrega el servidor.
 * Funciones puras, sin DOM: app.js las conecta con la página y src/test/javascript/options.test.mjs las prueba.
 *
 * Decisiones:
 *  - Ningún valor permitido está escrito aquí: tipos, rarezas, rangos, habilidades, modalidades, efectos y
 *    requisitos de imagen llegan del servidor. Si la carga falla no hay valores de respaldo: la interfaz
 *    muestra el error y deshabilita el formulario, para no ofrecer valores que el servidor rechazaría.
 *  - Una respuesta con una forma inesperada se trata igual que un error: mejor un aviso claro que un
 *    formulario a medias.
 */

export const OPTIONS_PATHS = {
  catalog: '/api/v1/opciones/catalogo',
  creation: '/api/v1/opciones/creacion',
  // Sprint 3, sección 7.4: tamaño del deck y largo máximo de su nombre (historia 3.1.3).
  game: '/api/v1/opciones/juego',
};

/** Unidad para mostrar el peso máximo de la imagen (el valor viene en bytes del servidor). */
const BYTES_PER_MEGABYTE = 1024 * 1024;

const isRange = (range) => Number.isInteger(range?.min) && Number.isInteger(range?.max) && range.min <= range.max;
const isCatalogValue = (value) => typeof value?.code === 'string' && typeof value?.name === 'string';
const isImageRequirement = (image) => ['ratioWidth', 'ratioHeight', 'minWidth', 'minHeight']
  .every((name) => Number.isInteger(image?.[name]) && image[name] > 0);
const isParameter = (parameter) => typeof parameter?.name === 'string' && typeof parameter?.label === 'string'
  && typeof parameter?.required === 'boolean'
  && (parameter.kind === 'CARD_TYPE' ? parameter.min === null && parameter.max === null
    : ['INTEGER', 'INTEGER_SET'].includes(parameter.kind) && isRange(parameter));

/** Opciones del catálogo de una respuesta 200 con la forma del contrato, o null. */
export function catalogOptionsFrom(result) {
  const body = result && result.status === 200 ? result.body : null;
  const pagination = body?.pagination;
  const valid = Array.isArray(body?.types) && body.types.length > 0 && body.types.every(isCatalogValue)
    && Array.isArray(body.rarities) && body.rarities.length > 0 && body.rarities.every(isCatalogValue)
    && isRange(body.cost)
    && Number.isInteger(pagination?.minLimit) && Number.isInteger(pagination?.maxLimit)
    && Number.isInteger(pagination?.defaultLimit)
    && pagination.minLimit <= pagination.defaultLimit && pagination.defaultLimit <= pagination.maxLimit;
  return valid ? body : null;
}

/** Opciones de creación de una respuesta 200 con la forma del contrato, o null. */
export function creationOptionsFrom(result) {
  const body = result && result.status === 200 ? result.body : null;
  const location = body?.location;
  const energy = location?.extraEnergy;
  const images = body?.images;
  const valid = Array.isArray(body?.abilities) && body.abilities.length > 0 && body.abilities.every(isCatalogValue)
    && isRange(body.card?.cost) && isRange(body.card?.power) && isRange(location?.cardLimit)
    && Array.isArray(energy?.modes) && energy.modes.length > 0
    && energy.modes.every((mode) => isCatalogValue(mode) && isRange({ min: mode.minAmount, max: mode.maxAmount }))
    && energy.modes.some((mode) => mode.code === energy.defaultMode)
    && Array.isArray(location.effects)
    && location.effects.every((effect) => isCatalogValue(effect) && Array.isArray(effect.parameters)
      && effect.parameters.every(isParameter))
    && Array.isArray(images?.formats) && images.formats.length > 0 && images.formats.every((f) => typeof f === 'string')
    && Number.isInteger(images.maxFileBytes) && images.maxFileBytes > 0
    && isImageRequirement(images.card) && isImageRequirement(images.location);
  return valid ? body : null;
}

/**
 * Opciones del juego de una respuesta 200 con la forma del contrato (deckSize y deckNameMaxLength enteros
 * positivos), o null. Otras historias agregan campos a esta respuesta: se ignoran los que no se usan aquí.
 */
export function gameOptionsFrom(result) {
  const body = result && result.status === 200 ? result.body : null;
  const valid = Number.isInteger(body?.deckSize) && body.deckSize > 0
    && Number.isInteger(body?.deckNameMaxLength) && body.deckNameMaxLength > 0;
  return valid ? body : null;
}

/** Mensaje claro cuando las opciones no se pudieron cargar; nunca propone valores de respaldo. */
export function optionsErrorMessage(path, result) {
  let cause;
  if (!result) cause = 'no se pudo conectar con el servidor';
  else if (result.status !== 200) {
    cause = 'HTTP ' + result.status + (result.body?.type ? ' ' + result.body.type : '')
      + (result.body?.detail ? ': ' + result.body.detail : '');
  } else cause = 'la respuesta no tiene la forma del contrato';
  return 'No se pudieron cargar las opciones desde GET ' + path + ' (' + cause + '). '
    + 'El formulario queda deshabilitado: vuelve a iniciar sesión o recarga la página.';
}

/** Rango para mostrar, por ejemplo "0 a 10". */
export function rangeText(range) {
  return range.min + ' a ' + range.max;
}

/** "PNG, JPEG o WEBP" a partir de la lista de formatos. */
export function formatsText(formats) {
  return formats.length > 1 ? formats.slice(0, -1).join(', ') + ' o ' + formats[formats.length - 1] : formats[0];
}

/** Valor del atributo accept del campo de archivo, por ejemplo "image/png,image/jpeg,image/webp". */
export function acceptedTypes(formats) {
  return formats.map((format) => 'image/' + format.toLowerCase()).join(',');
}

/** Peso máximo en MB para mostrar; sin decimales si es entero. */
export function megabytes(bytes) {
  const value = bytes / BYTES_PER_MEGABYTE;
  return Number.isInteger(value) ? String(value) : value.toFixed(1);
}

/** Requisitos de la imagen de una carta o una ubicación ("card" o "location"), en una línea. */
export function imageRequirementText(images, kind) {
  const image = images[kind];
  return [formatsText(images.formats), image.ratioWidth + ':' + image.ratioHeight,
    'mínimo ' + image.minWidth + ' × ' + image.minHeight, 'máximo ' + megabytes(images.maxFileBytes) + ' MB']
    .join(' · ');
}

/**
 * Presentación del monto de energía extra para una modalidad: si su rango es un solo valor (NONE), el
 * campo no aplica y el monto se omite al enviar; si no, es obligatorio con su rango.
 */
export function energyAmountState(mode) {
  if (mode.minAmount === mode.maxAmount) {
    return { enabled: false, hint: 'no aplica con ' + mode.code };
  }
  return { enabled: true, hint: 'obligatorio, ' + mode.minAmount + ' a ' + mode.maxAmount };
}

/** Enteros de min a max, ambos incluidos (las casillas de un parámetro INTEGER_SET). */
export function integersBetween(min, max) {
  return Array.from({ length: max - min + 1 }, (_, index) => min + index);
}

/**
 * JSON del campo effect (sección 7.2) a partir de la descripción del efecto y de los valores del formulario.
 * readValue(parameter) devuelve el texto del control (INTEGER, CARD_TYPE) o la lista de textos marcados
 * (INTEGER_SET). Un valor vacío se omite: si era obligatorio, el servidor responde INVALID_EFFECT.
 */
export function effectJson(effect, readValue) {
  if (!effect) return null;
  const parameters = {};
  for (const parameter of effect.parameters) {
    const value = readValue(parameter);
    if (parameter.kind === 'INTEGER_SET') {
      if (value.length > 0) parameters[parameter.name] = value.map((item) => Number.parseInt(item, 10));
    } else if (value !== '') {
      parameters[parameter.name] = parameter.kind === 'INTEGER' ? Number.parseInt(value, 10) : value;
    }
  }
  return { code: effect.code, parameters };
}
