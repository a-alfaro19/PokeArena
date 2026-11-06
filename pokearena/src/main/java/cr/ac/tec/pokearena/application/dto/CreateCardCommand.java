package cr.ac.tec.pokearena.application.dto;

/**
 * Propósito: datos de "Crear carta" tal como llegan del formulario (historia 2.1.1).
 * Responsabilidades: transportar los campos sin interpretar hasta el caso de uso, que los valida
 * (paso 5). Los números llegan como texto para que un valor mal escrito tenga el mismo error tipado
 * que uno fuera de rango. Un campo ausente es {@code null}.
 * Referencia: sección 7.1.
 *
 * @param name        nombre
 * @param cost        costo, como texto
 * @param power       poder, como texto
 * @param typeCode    código del tipo, por ejemplo {@code FUE}
 * @param rarityCode  código de la rareza, por ejemplo {@code COMMON}
 * @param abilityCode código de la habilidad, por ejemplo {@code HAB-000}
 * @param image       bytes de la imagen subida
 */
public record CreateCardCommand(String name, String cost, String power, String typeCode, String rarityCode,
        String abilityCode, byte[] image) {
}
