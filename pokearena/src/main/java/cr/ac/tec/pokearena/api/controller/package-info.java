/**
 * Controladores REST: un controlador por recurso (cartas, ubicaciones, sesiones, catálogo).
 *
 * <p>Responsabilidades: leer parámetros y encabezados, invocar un caso de uso y devolver el
 * código HTTP correcto. No contienen reglas de negocio.
 *
 * <p>Puede usar: {@code application} y {@code api.dto}. No puede usar: {@code infrastructure}
 * ni repositorios (regla 1 de 4.2).
 */
package cr.ac.tec.pokearena.api.controller;
