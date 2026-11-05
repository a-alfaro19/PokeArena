/**
 * Formato único de errores: el manejador global {@code ApiErrorHandler} y el cuerpo
 * {@code ApiError} (sección 5.3).
 *
 * <p>Responsabilidades: traducir cada {@code DomainException} y cada error HTTP al formato
 * único, sin exponer mensajes internos.
 *
 * <p>Puede usar: {@code application} y {@code domain.error}. No puede usar:
 * {@code infrastructure} (regla 1 de 4.2).
 */
package cr.ac.tec.pokearena.api.error;
