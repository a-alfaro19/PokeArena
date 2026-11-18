package cr.ac.tec.pokearena.application.dto;

import java.util.List;

import cr.ac.tec.pokearena.domain.effect.LocationEffectType;
import cr.ac.tec.pokearena.domain.model.Ability;
import cr.ac.tec.pokearena.domain.model.ExtraEnergy;
import cr.ac.tec.pokearena.domain.model.ImageFormat;
import cr.ac.tec.pokearena.domain.rule.ImageRules;

/**
 * Propósito: opciones de los formularios Crear carta y Crear ubicación.
 * Responsabilidades: reunir los valores permitidos que la interfaz necesita antes del POST, tomados de las
 * mismas clases que validan la creación (CardFieldRules, LocationFieldRules, ExtraEnergy, los plug-ins de
 * efecto e ImageRules), para que la interfaz no tenga ningún valor escrito a mano.
 * Referencia: secciones 7.1 y 7.2; {@code GET /api/v1/opciones/creacion}.
 *
 * @param abilities         habilidades activas, ordenadas por código
 * @param cost              rango de costo de una carta
 * @param power             rango de poder de una carta
 * @param cardLimit         rango del límite de cartas de una ubicación
 * @param defaultEnergyMode modalidad de energía extra que la interfaz muestra seleccionada
 * @param energyModes       modalidades de energía extra, en el orden del enum
 * @param effects           efectos de ubicación registrados, ordenados por código
 * @param images            requisitos de imagen
 */
public record CreationOptions(List<Ability> abilities, ValueRange cost, ValueRange power, ValueRange cardLimit,
        ExtraEnergy.Mode defaultEnergyMode, List<ExtraEnergy.Mode> energyModes, List<LocationEffectType> effects,
        ImageOptions images) {

    public CreationOptions {
        abilities = List.copyOf(abilities);
        energyModes = List.copyOf(energyModes);
        effects = List.copyOf(effects);
    }

    /**
     * Requisitos de imagen (secciones 7.1 y 7.2, paso 7).
     *
     * @param formats      formatos permitidos, en el orden del enum
     * @param maxFileBytes peso máximo en bytes
     * @param card         requisitos de la imagen de una carta
     * @param location     requisitos de la imagen de una ubicación
     */
    public record ImageOptions(List<ImageFormat> formats, long maxFileBytes, ImageRules card, ImageRules location) {

        public ImageOptions {
            formats = List.copyOf(formats);
        }
    }
}
