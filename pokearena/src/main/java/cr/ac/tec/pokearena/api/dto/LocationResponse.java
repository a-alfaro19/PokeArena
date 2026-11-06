package cr.ac.tec.pokearena.api.dto;

import java.util.Map;

import cr.ac.tec.pokearena.application.dto.LocationDetails;

/**
 * Propósito: cuerpo HTTP de una ubicación (respuesta 201 de {@code POST /api/v1/ubicaciones}).
 * Responsabilidades: definir la forma JSON del contrato, separada de la ficha de la aplicación.
 * Referencia: sección 7.2, paso 11.
 *
 * @param id            identificador
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
public record LocationResponse(long id, String name, String description, EffectValue effect, int cardLimit,
        EnergyValue extraEnergy, String imageLargeUrl, String imageSmallUrl, String imageStatus, boolean available) {

    /** Efecto: código y parámetros. */
    public record EffectValue(String code, Map<String, Object> parameters) {
    }

    /** Energía extra: modalidad y monto. */
    public record EnergyValue(String mode, int amount) {
    }

    public static LocationResponse from(LocationDetails details) {
        EffectValue effect = details.effect() == null ? null
                : new EffectValue(details.effect().code(), details.effect().parameters());
        return new LocationResponse(details.id(), details.name(), details.description(), effect, details.cardLimit(),
                new EnergyValue(details.extraEnergy().mode(), details.extraEnergy().amount()),
                details.imageLargeUrl(), details.imageSmallUrl(), details.imageStatus(), details.available());
    }
}
