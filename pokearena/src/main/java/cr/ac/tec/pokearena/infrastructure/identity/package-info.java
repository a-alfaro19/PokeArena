/**
 * Identidad: verificador de tokens de Google (JWKS) y emisor de las credenciales propias.
 *
 * <p>Responsabilidades: verificar firma, emisor, vencimiento y audiencia, y firmar los JWT de
 * PokéArena con una biblioteca, sin Spring Security (RES-01).
 *
 * <p>Puede usar: {@code application.port} y la biblioteca JWT (regla 4 de 4.2).
 */
package cr.ac.tec.pokearena.infrastructure.identity;
