/**
 * Filtros HTTP transversales que no son de seguridad, como la correlación de solicitudes
 * (sección 5.6).
 *
 * <p>Responsabilidades: preparar cada solicitud antes de que llegue a los controladores (por
 * ejemplo, asignar su {@code correlationId}). No contienen reglas de negocio.
 *
 * <p>Puede usar: {@code application} y la API de Servlet. No puede usar: {@code infrastructure}
 * ni repositorios (regla 1 de 4.2).
 */
package cr.ac.tec.pokearena.api.web;
