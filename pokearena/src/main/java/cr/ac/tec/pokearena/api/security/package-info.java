/**
 * Seguridad HTTP propia, sin Spring Security (RES-01, sección 5.2).
 *
 * <p>Responsabilidades: el filtro que lee {@code Authorization: Bearer}, distingue 401 de 403
 * y aplica el rol requerido por cada ruta.
 *
 * <p>Puede usar: los puertos de {@code application.port} para verificar credenciales.
 * No puede usar: {@code infrastructure} (regla 1 de 4.2).
 */
package cr.ac.tec.pokearena.api.security;
