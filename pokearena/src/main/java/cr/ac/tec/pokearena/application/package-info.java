/**
 * Capa de aplicación: casos de uso de las historias (sección 4.1).
 *
 * <p>Responsabilidades: orquestar cada historia siguiendo su diagrama del Anexo A y controlar
 * la transacción ({@code UnitOfWork}) y la idempotencia.
 *
 * <p>Puede usar: {@code domain} y {@code java.*}. No puede usar: {@code api},
 * {@code infrastructure} ni Spring (reglas 2 y 5 de 4.2). El reloj y la aleatoriedad entran
 * por puertos ({@code Clock}, {@code RandomSource}).
 */
package cr.ac.tec.pokearena.application;
