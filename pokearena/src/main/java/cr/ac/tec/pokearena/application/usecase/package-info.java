/**
 * Casos de uso: una clase por historia con la plantilla común (autorizar, idempotencia,
 * validar, ejecutar en {@code UnitOfWork}, responder).
 *
 * <p>Responsabilidades: ejecutar los pasos del diagrama del Anexo A en el mismo orden,
 * citando cada paso en un comentario.
 *
 * <p>Puede usar: {@code domain}, {@code application.port} y {@code application.dto}.
 * No puede usar: {@code api}, {@code infrastructure}, Spring, ni llamar directamente a
 * {@code LocalDateTime.now()}, {@code Random} o {@code UUID.randomUUID()} (reglas 2 y 5 de 4.2).
 */
package cr.ac.tec.pokearena.application.usecase;
