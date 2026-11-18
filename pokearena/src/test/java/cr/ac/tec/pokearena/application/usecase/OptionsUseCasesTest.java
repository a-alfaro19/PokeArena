package cr.ac.tec.pokearena.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import cr.ac.tec.pokearena.application.dto.CatalogOptions;
import cr.ac.tec.pokearena.application.dto.CreationOptions;
import cr.ac.tec.pokearena.application.dto.ValueRange;
import cr.ac.tec.pokearena.domain.effect.CostModifierEffect;
import cr.ac.tec.pokearena.domain.effect.EffectParameterSpec;
import cr.ac.tec.pokearena.domain.effect.LocationEffectRegistry;
import cr.ac.tec.pokearena.domain.effect.LocationEffectType;
import cr.ac.tec.pokearena.domain.effect.TurnRestrictionEffect;
import cr.ac.tec.pokearena.domain.effect.TypeBonusEffect;
import cr.ac.tec.pokearena.domain.model.Ability;
import cr.ac.tec.pokearena.domain.model.CardType;
import cr.ac.tec.pokearena.domain.model.ExtraEnergy;
import cr.ac.tec.pokearena.domain.model.ImageFormat;
import cr.ac.tec.pokearena.domain.model.Rarity;
import cr.ac.tec.pokearena.domain.repository.CardCatalogRepository;
import cr.ac.tec.pokearena.domain.rule.CardFieldRules;
import cr.ac.tec.pokearena.domain.rule.ImageRules;
import cr.ac.tec.pokearena.domain.rule.LocationFieldRules;
import cr.ac.tec.pokearena.support.InMemoryAbilityRepository;

/**
 * Propósito: pruebas unitarias de {@link GetCatalogOptionsUseCase} y {@link GetCreationOptionsUseCase}.
 * Responsabilidades: comprobar que las opciones salen de los repositorios y de las mismas clases que validan
 * (sin valores copiados), su orden estable, y que un efecto nuevo aparece sin modificar el caso de uso.
 * Sin Spring ni base de datos.
 * Referencia: Anexos A.1, A.2, A.3 y A.6; secciones 5.5, 7.1, 7.2, 7.3 y 8.3.
 */
@DisplayName("Opciones de la interfaz (casos de uso)")
class OptionsUseCasesTest {

    private static final int DEFAULT_LIMIT = 7;
    private static final int MAX_LIMIT = 9;
    private static final List<CardType> TYPES = List.of(new CardType("AGU", "Agua"), new CardType("FUE", "Fuego"));
    private static final List<Rarity> RARITIES = List.of(new Rarity("COMMON", "Común"), new Rarity("RARE", "Rara"));

    private final CardCatalogRepository catalog = new CardCatalogRepository() {
        @Override
        public List<CardType> findAllTypes() {
            return TYPES;
        }

        @Override
        public List<Rarity> findAllRarities() {
            return RARITIES;
        }
    };

    @Nested
    @DisplayName("Opciones del catálogo")
    class Catalog {

        @Test
        @DisplayName("Tipos y rarezas del repositorio; costo de CardFieldRules; paginación de la configuración")
        void fromSources() {
            CatalogOptions options = new GetCatalogOptionsUseCase(catalog, new Pagination(DEFAULT_LIMIT, MAX_LIMIT))
                    .execute();

            assertThat(options.types()).isEqualTo(TYPES);
            assertThat(options.rarities()).isEqualTo(RARITIES);
            assertThat(options.cost()).isEqualTo(new ValueRange(CardFieldRules.MIN_COST, CardFieldRules.MAX_COST));
            assertThat(options.pagination())
                    .isEqualTo(new CatalogOptions.PaginationLimits(DEFAULT_LIMIT, Pagination.MIN_LIMIT, MAX_LIMIT));
        }
    }

    @Nested
    @DisplayName("Opciones de creación")
    class Creation {

        private final Map<String, Ability> abilities = new HashMap<>(Map.of(
                "HAB-002", new Ability("HAB-002", "Segunda", null, null),
                "HAB-000", new Ability("HAB-000", "Sin Habilidad", null, null)));

        private final List<LocationEffectType> standardEffects = List.of(new TypeBonusEffect(() -> List.of("AGU")),
                new TurnRestrictionEffect(), new CostModifierEffect(() -> List.of("AGU")));

        private CreationOptions options(List<LocationEffectType> effects) {
            return new GetCreationOptionsUseCase(new InMemoryAbilityRepository(abilities),
                    new LocationEffectRegistry(effects)).execute();
        }

        @Test
        @DisplayName("Habilidades activas del repositorio, ordenadas por código")
        void abilitiesByCode() {
            assertThat(options(standardEffects).abilities()).extracting(Ability::code)
                    .containsExactly("HAB-000", "HAB-002");
        }

        @Test
        @DisplayName("Rangos de CardFieldRules y LocationFieldRules; modalidades de ExtraEnergy con NONE por defecto")
        void rangesAndEnergy() {
            CreationOptions options = options(standardEffects);

            assertThat(options.cost()).isEqualTo(new ValueRange(CardFieldRules.MIN_COST, CardFieldRules.MAX_COST));
            assertThat(options.power()).isEqualTo(new ValueRange(CardFieldRules.MIN_POWER, CardFieldRules.MAX_POWER));
            assertThat(options.cardLimit())
                    .isEqualTo(new ValueRange(LocationFieldRules.MIN_CARD_LIMIT, LocationFieldRules.MAX_CARD_LIMIT));
            assertThat(options.defaultEnergyMode()).isEqualTo(ExtraEnergy.Mode.NONE);
            assertThat(options.energyModes()).containsExactly(ExtraEnergy.Mode.values());
        }

        @Test
        @DisplayName("Efectos del registro, ordenados por código, con sus parámetros")
        void effectsByCode() {
            List<LocationEffectType> effects = options(standardEffects).effects();

            assertThat(effects).extracting(LocationEffectType::code)
                    .containsExactly(CostModifierEffect.CODE, TurnRestrictionEffect.CODE, TypeBonusEffect.CODE);
            assertThat(effects.getLast().parameters()).extracting(EffectParameterSpec::name)
                    .containsExactly("type", "amount");
        }

        @Test
        @DisplayName("Un efecto nuevo aparece en las opciones registrándolo, SIN modificar el caso de uso")
        void newEffectAppears() {
            LocationEffectType weather = new LocationEffectType() {
                @Override
                public String code() {
                    return "WEATHER";
                }

                @Override
                public String name() {
                    return "Clima";
                }

                @Override
                public List<EffectParameterSpec> parameters() {
                    return List.of(EffectParameterSpec.integer("intensity", "Intensidad", true, 1, 3));
                }

                @Override
                public void validate(Map<String, Object> parameters) {
                    // No se usa en esta prueba.
                }
            };

            assertThat(options(List.of(new TurnRestrictionEffect(), weather)).effects())
                    .extracting(LocationEffectType::code).containsExactly(TurnRestrictionEffect.CODE, "WEATHER");
        }

        @Test
        @DisplayName("Requisitos de imagen de ImageRules, con los formatos en el orden del enum")
        void images() {
            CreationOptions.ImageOptions images = options(standardEffects).images();

            assertThat(images.formats()).containsExactly(ImageFormat.PNG, ImageFormat.JPEG, ImageFormat.WEBP);
            assertThat(images.maxFileBytes()).isEqualTo(ImageRules.MAX_FILE_BYTES);
            assertThat(images.card()).isEqualTo(ImageRules.CARD);
            assertThat(images.location()).isEqualTo(ImageRules.LOCATION);
        }
    }
}
