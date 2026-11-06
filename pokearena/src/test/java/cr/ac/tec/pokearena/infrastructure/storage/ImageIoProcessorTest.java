package cr.ac.tec.pokearena.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import cr.ac.tec.pokearena.application.port.ValidatedImage;
import cr.ac.tec.pokearena.domain.error.ErrorType;
import cr.ac.tec.pokearena.domain.error.ImageValidationException;
import cr.ac.tec.pokearena.domain.error.ThumbnailGenerationException;
import cr.ac.tec.pokearena.domain.model.ImageFormat;
import cr.ac.tec.pokearena.domain.rule.ImageRules;
import cr.ac.tec.pokearena.support.TestImages;

/**
 * Propósito: pruebas unitarias del procesador de imágenes (corrección 6; pasos 7 y 8 de A.1 y A.2).
 * Responsabilidades: comprobar cada requisito con su error tipado, el orden de la validación, la
 * detección del formato real por el contenido y el tamaño exacto de las miniaturas.
 * Referencia: secciones 7.1 y 7.2.
 */
@DisplayName("Procesador de imágenes")
class ImageIoProcessorTest {

    // Límite de píxeles holgado para las imágenes de prueba (la configuración real está en properties).
    private static final long MAX_PIXELS = 16_000_000;

    private final ImageIoProcessor processor = new ImageIoProcessor(MAX_PIXELS);

    @Nested
    @DisplayName("Imágenes válidas")
    class ValidImages {

        @Test
        @DisplayName("Una carta PNG de 600 × 840 es válida y se detecta como PNG")
        void validPngCard() {
            ValidatedImage image = processor.validate(TestImages.png(600, 840), ImageRules.CARD);

            assertThat(image.format()).isEqualTo(ImageFormat.PNG);
            assertThat(image.width()).isEqualTo(600);
            assertThat(image.height()).isEqualTo(840);
        }

        @Test
        @DisplayName("Una carta JPG de 1000 × 1400 es válida y se detecta como JPEG")
        void validJpegCard() {
            ValidatedImage image = processor.validate(TestImages.jpeg(1000, 1400), ImageRules.CARD);

            assertThat(image.format()).isEqualTo(ImageFormat.JPEG);
            assertThat(image.width()).isEqualTo(1000);
        }

        @Test
        @DisplayName("Una carta WebP de 600 × 840 es válida y se detecta como WebP")
        void validWebpCard() {
            ValidatedImage image = processor.validate(TestImages.webpCard(), ImageRules.CARD);

            assertThat(image.format()).isEqualTo(ImageFormat.WEBP);
            assertThat(image.width()).isEqualTo(TestImages.WEBP_CARD_WIDTH);
            assertThat(image.height()).isEqualTo(TestImages.WEBP_CARD_HEIGHT);
        }

        @Test
        @DisplayName("Una ubicación PNG de 800 × 600 es válida")
        void validPngLocation() {
            ValidatedImage image = processor.validate(TestImages.png(800, 600), ImageRules.LOCATION);

            assertThat(image.width()).isEqualTo(800);
            assertThat(image.height()).isEqualTo(600);
        }

        @Test
        @DisplayName("La validación conserva los bytes originales, que se guardan como versión grande")
        void keepsOriginalContent() {
            byte[] content = TestImages.png(600, 840);

            assertThat(processor.validate(content, ImageRules.CARD).content()).isEqualTo(content);
        }
    }

    @Nested
    @DisplayName("Imágenes rechazadas, cada una con su requisito incumplido")
    class RejectedImages {

        @Test
        @DisplayName("Un archivo de texto con extensión .png es INVALID_IMAGE_FORMAT: se mira el contenido, no el nombre")
        void textFileIsInvalidFormat() {
            byte[] textNamedPng = "esto es un archivo de texto llamado imagen.png".getBytes(StandardCharsets.UTF_8);

            assertRejected(textNamedPng, ImageRules.CARD, ErrorType.INVALID_IMAGE_FORMAT);
        }

        @Test
        @DisplayName("Una imagen GIF real es INVALID_IMAGE_FORMAT: solo se aceptan PNG, JPG y WebP")
        void gifIsInvalidFormat() {
            assertRejected(TestImages.gif(600, 840), ImageRules.CARD, ErrorType.INVALID_IMAGE_FORMAT);
        }

        @Test
        @DisplayName("Un archivo con la firma de PNG pero dañado es INVALID_IMAGE_FORMAT")
        void corruptPngIsInvalidFormat() {
            byte[] png = TestImages.png(600, 840);

            assertRejected(Arrays.copyOf(png, png.length / 3), ImageRules.CARD, ErrorType.INVALID_IMAGE_FORMAT);
        }

        @Test
        @DisplayName("Un archivo vacío es INVALID_IMAGE_FORMAT")
        void emptyFileIsInvalidFormat() {
            assertRejected(new byte[0], ImageRules.CARD, ErrorType.INVALID_IMAGE_FORMAT);
        }

        @Test
        @DisplayName("Más de 5 MB es IMAGE_TOO_LARGE, y se rechaza antes de mirar el formato")
        void overFiveMegabytesIsTooLarge() {
            // Un solo byte por encima del límite, y además sin ser una imagen: el peso se revisa primero.
            byte[] tooLarge = new byte[(int) ImageRules.MAX_FILE_BYTES + 1];

            assertRejected(tooLarge, ImageRules.CARD, ErrorType.IMAGE_TOO_LARGE);
        }

        @Test
        @DisplayName("Una imagen con más píxeles que el límite configurado es IMAGE_TOO_LARGE, sin decodificarla")
        void tooManyPixelsIsTooLarge() {
            ImageIoProcessor strict = new ImageIoProcessor(1000);

            assertThatThrownBy(() -> strict.validate(TestImages.png(600, 840), ImageRules.CARD))
                    .isInstanceOfSatisfying(ImageValidationException.class,
                            error -> assertThat(error.type()).isEqualTo(ErrorType.IMAGE_TOO_LARGE));
        }

        @Test
        @DisplayName("El exceso de peso y el exceso de dimensiones son IMAGE_TOO_LARGE con mensajes distintos")
        void weightAndDimensionsHaveDistinctMessages() {
            ImageValidationException byWeight = rejection(processor,
                    new byte[(int) ImageRules.MAX_FILE_BYTES + 1], ErrorType.IMAGE_TOO_LARGE);
            ImageValidationException byDimensions = rejection(new ImageIoProcessor(1000),
                    TestImages.png(600, 840), ErrorType.IMAGE_TOO_LARGE);

            assertThat(byWeight.getMessage()).contains("pesa").contains("5 MB");
            assertThat(byDimensions.getMessage()).contains("dimensiones").contains("600 × 840").contains("1000");
            assertThat(byDimensions.getMessage()).isNotEqualTo(byWeight.getMessage());
        }

        @Test
        @DisplayName("Una carta de 600 × 600 es INVALID_IMAGE_RATIO e indica la proporción esperada")
        void wrongCardRatio() {
            assertThatThrownBy(() -> processor.validate(TestImages.png(600, 600), ImageRules.CARD))
                    .isInstanceOfSatisfying(ImageValidationException.class, error -> {
                        assertThat(error.type()).isEqualTo(ErrorType.INVALID_IMAGE_RATIO);
                        assertThat(error.getMessage()).contains("5:7");
                        assertThat(error.field()).contains(ImageValidationException.IMAGE_FIELD);
                    });
        }

        @Test
        @DisplayName("Una imagen de carta usada como ubicación es INVALID_IMAGE_RATIO (4:3)")
        void cardImageAsLocationHasWrongRatio() {
            assertRejected(TestImages.png(600, 840), ImageRules.LOCATION, ErrorType.INVALID_IMAGE_RATIO);
        }

        @Test
        @DisplayName("Una carta de 300 × 420 tiene la proporción correcta pero es IMAGE_RESOLUTION_TOO_LOW")
        void lowCardResolution() {
            assertThatThrownBy(() -> processor.validate(TestImages.png(300, 420), ImageRules.CARD))
                    .isInstanceOfSatisfying(ImageValidationException.class, error -> {
                        assertThat(error.type()).isEqualTo(ErrorType.IMAGE_RESOLUTION_TOO_LOW);
                        assertThat(error.getMessage()).contains("600 × 840");
                    });
        }

        @Test
        @DisplayName("Una ubicación de 400 × 300 es IMAGE_RESOLUTION_TOO_LOW (mínimo 800 × 600)")
        void lowLocationResolution() {
            assertRejected(TestImages.png(400, 300), ImageRules.LOCATION, ErrorType.IMAGE_RESOLUTION_TOO_LOW);
        }

        @Test
        @DisplayName("Con proporción y resolución incorrectas a la vez, se informa primero la proporción")
        void ratioIsCheckedBeforeResolution() {
            assertRejected(TestImages.png(300, 300), ImageRules.CARD, ErrorType.INVALID_IMAGE_RATIO);
        }
    }

    @Nested
    @DisplayName("Miniaturas")
    class Thumbnails {

        @Test
        @DisplayName("La miniatura de una carta mide EXACTAMENTE 200 × 280 y es PNG")
        void cardThumbnailIsExact() {
            ValidatedImage card = processor.validate(TestImages.jpeg(1000, 1400), ImageRules.CARD);

            byte[] thumbnail = processor.generateThumbnail(card, ImageRules.CARD);

            assertExactPng(thumbnail, 200, 280);
        }

        @Test
        @DisplayName("La miniatura de una ubicación mide EXACTAMENTE 320 × 240 y es PNG")
        void locationThumbnailIsExact() {
            ValidatedImage location = processor.validate(TestImages.png(800, 600), ImageRules.LOCATION);

            assertExactPng(processor.generateThumbnail(location, ImageRules.LOCATION), 320, 240);
        }

        @Test
        @DisplayName("La miniatura de una carta WebP también mide EXACTAMENTE 200 × 280")
        void webpThumbnailIsExact() {
            ValidatedImage card = processor.validate(TestImages.webpCard(), ImageRules.CARD);

            assertExactPng(processor.generateThumbnail(card, ImageRules.CARD), 200, 280);
        }

        @Test
        @DisplayName("Con una proporción dentro de la tolerancia, la miniatura se fuerza al tamaño exacto")
        void thumbnailIsForcedWithinTolerance() {
            ValidatedImage card = processor.validate(TestImages.png(600, 848), ImageRules.CARD);

            assertExactPng(processor.generateThumbnail(card, ImageRules.CARD), 200, 280);
        }

        @Test
        @DisplayName("Si la miniatura no se puede generar, lanza ThumbnailGenerationException para el caso de uso")
        void failedThumbnailIsReported() {
            ValidatedImage broken = new ValidatedImage(ImageFormat.PNG, 600, 840, new byte[] { 1, 2, 3 });

            assertThatThrownBy(() -> processor.generateThumbnail(broken, ImageRules.CARD))
                    .isInstanceOf(ThumbnailGenerationException.class);
        }
    }

    private static ImageValidationException rejection(ImageIoProcessor processor, byte[] content, ErrorType expected) {
        try {
            processor.validate(content, ImageRules.CARD);
        } catch (ImageValidationException error) {
            assertThat(error.type()).isEqualTo(expected);
            return error;
        }
        throw new AssertionError("Se esperaba " + expected + " y la imagen fue aceptada");
    }

    private void assertRejected(byte[] content, ImageRules rules, ErrorType expected) {
        assertThatThrownBy(() -> processor.validate(content, rules))
                .isInstanceOfSatisfying(ImageValidationException.class,
                        error -> assertThat(error.type()).isEqualTo(expected));
    }

    private static void assertExactPng(byte[] thumbnail, int width, int height) {
        assertThat(ImageSignatures.detect(thumbnail)).contains(ImageFormat.PNG);
        BufferedImage decoded = TestImages.read(thumbnail);
        assertThat(decoded.getWidth()).isEqualTo(width);
        assertThat(decoded.getHeight()).isEqualTo(height);
    }
}
