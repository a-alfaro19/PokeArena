/**
 * Puertos de aplicación: interfaces que los casos de uso necesitan y que no son de
 * persistencia ({@code UnitOfWork}, {@code IdempotencyStore}, {@code ImageProcessor},
 * {@code ImageStorage}, {@code IdentityProvider}, {@code TokenIssuer}, {@code CatalogCache},
 * {@code RandomSource}, ...).
 *
 * <p>Responsabilidades: declarar lo que la aplicación necesita del exterior; las
 * implementaciones viven en {@code infrastructure} (regla 4 de 4.2).
 *
 * <p>Puede usar: {@code domain}. No puede usar: {@code api} ni {@code infrastructure}.
 * Los puertos de persistencia no van aquí, sino en {@code domain.repository}.
 */
package cr.ac.tec.pokearena.application.port;
