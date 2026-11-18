package cr.ac.tec.pokearena.api.dto;

import java.util.List;

import cr.ac.tec.pokearena.api.dto.CardResponse.AbilityValue;
import cr.ac.tec.pokearena.api.dto.CatalogOptionsResponse.RangeValue;
import cr.ac.tec.pokearena.application.dto.CreationOptions;
import cr.ac.tec.pokearena.application.dto.ValueRange;
import cr.ac.tec.pokearena.domain.effect.EffectParameterSpec;
import cr.ac.tec.pokearena.domain.effect.LocationEffectType;
import cr.ac.tec.pokearena.domain.model.ExtraEnergy;
import cr.ac.tec.pokearena.domain.rule.ImageRules;

/**
 * Propósito: cuerpo HTTP de {@code GET /api/v1/opciones/creacion} (esquema {@code CreationOptions}).
 * Responsabilidades: definir la forma JSON del contrato, separada de las opciones de la aplicación.
 * Referencia: secciones 7.1 y 7.2.
 *
 * @param abilities habilidades activas, ordenadas por código (misma forma que la habilidad de la ficha)
 * @param card      rangos de los campos de una carta
 * @param location  rangos, energía extra y efectos de una ubicación
 * @param images    requisitos de imagen
 */
public record CreationOptionsResponse(List<AbilityValue> abilities, CardFieldsValue card,
        LocationFieldsValue location, ImagesValue images) {

    /** Rangos de costo y poder. */
    public record CardFieldsValue(RangeValue cost, RangeValue power) {
    }

    /** Límite de cartas, energía extra y efectos. */
    public record LocationFieldsValue(RangeValue cardLimit, ExtraEnergyValue extraEnergy,
            List<EffectValue> effects) {
    }

    /** Modalidad por defecto y modalidades. */
    public record ExtraEnergyValue(String defaultMode, List<EnergyModeValue> modes) {
    }

    /** Una modalidad de energía extra con su rango de monto. */
    public record EnergyModeValue(String code, String name, int minAmount, int maxAmount) {
    }

    /** Un efecto de ubicación y sus parámetros. */
    public record EffectValue(String code, String name, List<EffectParameterValue> parameters) {
    }

    /** Un parámetro de un efecto; {@code min} y {@code max} son {@code null} con {@code CARD_TYPE}. */
    public record EffectParameterValue(String name, String label, String kind, boolean required, Integer min,
            Integer max) {
    }

    /** Formatos, peso máximo y requisitos por entidad. */
    public record ImagesValue(List<String> formats, long maxFileBytes, ImageRequirementValue card,
            ImageRequirementValue location) {
    }

    /** Proporción y resolución mínima de la versión grande. */
    public record ImageRequirementValue(int ratioWidth, int ratioHeight, int minWidth, int minHeight) {
    }

    public static CreationOptionsResponse from(CreationOptions options) {
        return new CreationOptionsResponse(
                options.abilities().stream().map(ability -> new AbilityValue(ability.code(), ability.name(),
                        ability.limitedToType(), ability.trigger(), ability.effectType(), ability.parameters()))
                        .toList(),
                new CardFieldsValue(range(options.cost()), range(options.power())),
                new LocationFieldsValue(range(options.cardLimit()),
                        new ExtraEnergyValue(options.defaultEnergyMode().name(),
                                options.energyModes().stream().map(CreationOptionsResponse::mode).toList()),
                        options.effects().stream().map(CreationOptionsResponse::effect).toList()),
                new ImagesValue(options.images().formats().stream().map(Enum::name).toList(),
                        options.images().maxFileBytes(), image(options.images().card()),
                        image(options.images().location())));
    }

    private static RangeValue range(ValueRange range) {
        return new RangeValue(range.min(), range.max());
    }

    private static EnergyModeValue mode(ExtraEnergy.Mode mode) {
        return new EnergyModeValue(mode.name(), mode.label(), ExtraEnergy.MIN_AMOUNT, mode.maxAmount());
    }

    private static EffectValue effect(LocationEffectType type) {
        return new EffectValue(type.code(), type.name(),
                type.parameters().stream().map(CreationOptionsResponse::parameter).toList());
    }

    private static EffectParameterValue parameter(EffectParameterSpec spec) {
        return new EffectParameterValue(spec.name(), spec.label(), spec.kind().name(), spec.required(), spec.min(),
                spec.max());
    }

    private static ImageRequirementValue image(ImageRules rules) {
        return new ImageRequirementValue(rules.ratioWidth(), rules.ratioHeight(), rules.minWidth(),
                rules.minHeight());
    }
}
