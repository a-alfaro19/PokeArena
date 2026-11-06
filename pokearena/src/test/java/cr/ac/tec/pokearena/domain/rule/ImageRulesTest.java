package cr.ac.tec.pokearena.domain.rule;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import cr.ac.tec.pokearena.domain.model.ImageFormat;

/**
 * Propósito: pruebas unitarias de los requisitos de imagen (criterios C.1 y C.2).
 * Responsabilidades: comprobar los valores de carta y ubicación, los requisitos compartidos y la
 * tolerancia del 1 % de la proporción en sus bordes.
 * Referencia: secciones 7.1 y 7.2.
 */
@DisplayName("Requisitos de imagen de cartas y ubicaciones")
class ImageRulesTest {

    @Test
    @DisplayName("Carta: proporción 5:7, mínimo 600 × 840 y miniatura de 200 × 280")
    void cardRequirements() {
        assertThat(ImageRules.CARD.ratioText()).isEqualTo("5:7");
        assertThat(ImageRules.CARD.minWidth()).isEqualTo(600);
        assertThat(ImageRules.CARD.minHeight()).isEqualTo(840);
        assertThat(ImageRules.CARD.thumbnailWidth()).isEqualTo(200);
        assertThat(ImageRules.CARD.thumbnailHeight()).isEqualTo(280);
    }

    @Test
    @DisplayName("Ubicación: proporción 4:3, mínimo 800 × 600 y miniatura de 320 × 240")
    void locationRequirements() {
        assertThat(ImageRules.LOCATION.ratioText()).isEqualTo("4:3");
        assertThat(ImageRules.LOCATION.minWidth()).isEqualTo(800);
        assertThat(ImageRules.LOCATION.minHeight()).isEqualTo(600);
        assertThat(ImageRules.LOCATION.thumbnailWidth()).isEqualTo(320);
        assertThat(ImageRules.LOCATION.thumbnailHeight()).isEqualTo(240);
    }

    @Test
    @DisplayName("Comparten formatos PNG, JPG y WebP, máximo de 5 MB y tolerancia del 1 %")
    void sharedRequirements() {
        assertThat(ImageRules.ALLOWED_FORMATS).containsExactlyInAnyOrder(ImageFormat.PNG, ImageFormat.JPEG,
                ImageFormat.WEBP);
        assertThat(ImageRules.MAX_FILE_BYTES).isEqualTo(5L * 1024 * 1024);
        assertThat(ImageRules.RATIO_TOLERANCE).isEqualTo(0.01);
    }

    @Test
    @DisplayName("La proporción exacta y una diferencia menor al 1 % se aceptan")
    void ratioWithinTolerance() {
        assertThat(ImageRules.CARD.hasExpectedRatio(600, 840)).isTrue();
        assertThat(ImageRules.CARD.hasExpectedRatio(1000, 1400)).isTrue();
        // 600/848 difiere de 5/7 en un 0,94 %.
        assertThat(ImageRules.CARD.hasExpectedRatio(600, 848)).isTrue();
        assertThat(ImageRules.LOCATION.hasExpectedRatio(800, 600)).isTrue();
    }

    @Test
    @DisplayName("Una proporción que difiere más del 1 % se rechaza, igual que una imagen de la otra orientación")
    void ratioOutsideTolerance() {
        // 600/850 difiere de 5/7 en un 1,18 %.
        assertThat(ImageRules.CARD.hasExpectedRatio(600, 850)).isFalse();
        assertThat(ImageRules.CARD.hasExpectedRatio(600, 600)).isFalse();
        assertThat(ImageRules.CARD.hasExpectedRatio(840, 600)).isFalse();
        assertThat(ImageRules.LOCATION.hasExpectedRatio(600, 800)).isFalse();
    }

    @Test
    @DisplayName("La resolución mínima se exige en ancho y en alto")
    void minimumResolution() {
        assertThat(ImageRules.CARD.meetsMinimumResolution(600, 840)).isTrue();
        assertThat(ImageRules.CARD.meetsMinimumResolution(599, 840)).isFalse();
        assertThat(ImageRules.CARD.meetsMinimumResolution(600, 839)).isFalse();
        assertThat(ImageRules.LOCATION.meetsMinimumResolution(800, 600)).isTrue();
    }
}
