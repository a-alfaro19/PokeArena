package cr.ac.tec.pokearena.application.dto;

import java.util.Map;

import cr.ac.tec.pokearena.domain.model.Location;

/**
 * Propósito: ficha de una ubicación, resultado de "Crear ubicación" (sección 7.2, paso 11).
 * Responsabilidades: reunir lo que ve el administrador: identificador, nombre, descripción, efecto,
 * límite de cartas, energía extra, imágenes, estado de imagen y disponibilidad. Es también lo que se
 * guarda con la clave de idempotencia para repetir la respuesta.
 * Referencia: sección 7.2 y criterio C.2.
 *
 * @param id            identificador de la ubicación
 * @param name          nombre
 * @param description   descripción
 * @param effect        efecto, o {@code null} si no tiene
 * @param cardLimit     límite de cartas por jugador
 * @param extraEnergy   energía extra por turno
 * @param imageLargeUrl URL de la versión grande
 * @param imageSmallUrl URL de la miniatura, o {@code null} si está pendiente
 * @param imageStatus   {@code READY}, {@code PENDING_RETRY} o {@code FAILED}
 * @param available     si se puede asignar a partidas nuevas
 */
public record LocationDetails(long id, String name, String description, EffectValue effect, int cardLimit,
        EnergyValue extraEnergy, String imageLargeUrl, String imageSmallUrl, String imageStatus, boolean available) {

    /** Efecto: código y parámetros. */
    public record EffectValue(String code, Map<String, Object> parameters) {
    }

    /** Energía extra: modalidad y monto. */
    public record EnergyValue(String mode, int amount) {
    }

    public static LocationDetails of(long id, Location location) {
        EffectValue effect = location.optionalEffect()
                .map(value -> new EffectValue(value.code(), value.parameters()))
                .orElse(null);
        return new LocationDetails(id, location.fields().name(), location.fields().description(), effect,
                location.fields().cardLimit(),
                new EnergyValue(location.fields().extraEnergy().mode().name(), location.fields().extraEnergy().amount()),
                location.imageLargeUrl(), location.imageSmallUrl(), location.imageStatus().name(),
                location.available());
    }
}
