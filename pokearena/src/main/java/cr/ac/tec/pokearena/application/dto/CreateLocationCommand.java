package cr.ac.tec.pokearena.application.dto;

/**
 * Propósito: datos de "Crear ubicación" tal como llegan del formulario (historia 2.2.1).
 * Responsabilidades: transportar los campos sin interpretar hasta el caso de uso, que los valida
 * (pasos 5 a 7). Los números llegan como texto para que un valor mal escrito tenga el mismo error
 * tipado que uno fuera de rango, y el efecto como el texto JSON del formulario. Un campo ausente es
 * {@code null}.
 * Referencia: sección 7.2.
 *
 * @param name              nombre
 * @param description       descripción
 * @param cardLimit         límite de cartas por jugador, como texto
 * @param extraEnergyMode   modalidad de energía extra: {@code NONE}, {@code FIXED} o {@code PER_CARD}
 * @param extraEnergyAmount monto de energía extra, como texto
 * @param effect            efecto como texto JSON, o {@code null} si la ubicación no tiene efecto
 * @param image             bytes de la imagen subida
 */
public record CreateLocationCommand(String name, String description, String cardLimit, String extraEnergyMode,
        String extraEnergyAmount, String effect, byte[] image) {
}
