package cr.ac.tec.pokearena.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import cr.ac.tec.pokearena.application.dto.ThumbnailRetryResult;
import cr.ac.tec.pokearena.application.dto.ThumbnailRetryResult.Outcome;
import cr.ac.tec.pokearena.application.port.ImageProcessor;
import cr.ac.tec.pokearena.application.port.StoredImages;
import cr.ac.tec.pokearena.application.port.ValidatedImage;
import cr.ac.tec.pokearena.application.usecase.ThumbnailRetryUseCase;
import cr.ac.tec.pokearena.domain.error.ThumbnailGenerationException;
import cr.ac.tec.pokearena.domain.model.ImageFormat;
import cr.ac.tec.pokearena.domain.rule.ImageRules;
import cr.ac.tec.pokearena.support.PostgresIntegrationTestBase;
import cr.ac.tec.pokearena.support.TestImages;

/**
 * Propósito: pruebas de integración de los reintentos de miniaturas (criterio C.1: "el sistema
 * reintenta la generación"; sección 7.1).
 * Responsabilidades: con PostgreSQL real y una carpeta de imágenes temporal, comprobar el éxito tras
 * un fallo, el agotamiento, que FAILED no se reintenta, cartas y ubicaciones, que dos ejecuciones
 * simultáneas no duplican el trabajo ({@code FOR UPDATE SKIP LOCKED}) y que la falta de la versión
 * grande deja FAILED con un aviso en el log.
 * Referencia: sección 7.1 ("Si falla la miniatura" y "Operaciones de la tarea de reintentos"), 7.2 y 9.
 *
 * <p>Las entidades se insertan directo en la base en {@code PENDING_RETRY}, con su versión grande
 * guardada por el almacenamiento real, como las dejaría Crear carta o Crear ubicación cuando la
 * miniatura falla. El procesador real va envuelto en {@link ControlledImageProcessor}, que puede
 * fallar o detenerse a pedido. La tarea programada no corre sola (ver PostgresIntegrationTestBase):
 * las pruebas invocan el caso de uso o el método de la tarea.
 */
@SpringBootTest
@Import(ThumbnailRetryIT.Controls.class)
@ExtendWith(OutputCaptureExtension.class)
@DisplayName("Reintentos de miniaturas (7.1) con PostgreSQL y disco reales")
class ThumbnailRetryIT extends PostgresIntegrationTestBase {

    private static final Path IMAGES_ROOT = createRoot();
    private static final long WAIT_SECONDS = 10;

    @Autowired
    private ThumbnailRetryUseCase retries;

    @Autowired
    private ThumbnailRetryJob job;

    @Autowired
    private FileSystemImageStorage storage;

    @Autowired
    private ControlledImageProcessor processor;

    @Autowired
    private JdbcTemplate jdbc;

    @Value("${app.images.max-retries}")
    private int maxRetries;

    @DynamicPropertySource
    static void imagesRoot(DynamicPropertyRegistry registry) {
        registry.add("app.images.root-dir", IMAGES_ROOT::toString);
    }

    @BeforeEach
    void prepare() {
        clean();
    }

    @AfterEach
    void reset() throws IOException {
        processor.reset();
        clean();
        deleteContents(IMAGES_ROOT);
    }

    @AfterAll
    static void removeRoot() throws IOException {
        deleteContents(IMAGES_ROOT);
        Files.deleteIfExists(IMAGES_ROOT);
    }

    @Test
    @DisplayName("C.1 error y 7.1: éxito tras un fallo: el reintento genera la miniatura de 200 × 280 y la carta queda READY y disponible")
    void successAfterFailure() throws IOException {
        long card = pendingCard();

        List<ThumbnailRetryResult> results = retries.execute();

        assertThat(results).extracting(ThumbnailRetryResult::outcome).containsExactly(Outcome.READY);
        Map<String, Object> row = row("card", card);
        assertThat(row.get("image_status")).isEqualTo("READY");
        assertThat(row.get("available")).isEqualTo(true);
        assertThat(row.get("retry_count")).isEqualTo(1);
        String smallUrl = (String) row.get("image_small_url");
        Path small = IMAGES_ROOT.resolve(smallUrl.substring(FileSystemImageStorage.URL_PREFIX.length()));
        assertThat(TestImages.read(Files.readAllBytes(small)))
                .satisfies(image -> assertThat(image.getWidth()).isEqualTo(200))
                .satisfies(image -> assertThat(image.getHeight()).isEqualTo(280));
    }

    @Test
    @DisplayName("7.1: agotamiento: tras el máximo de intentos fallidos queda FAILED, no disponible y con retry_count igual al máximo")
    void exhaustion() {
        long card = pendingCard();
        processor.failThumbnail = true;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            retries.execute();
            assertThat(row("card", card).get("retry_count")).isEqualTo(attempt);
        }

        Map<String, Object> row = row("card", card);
        assertThat(row.get("image_status")).isEqualTo("FAILED");
        assertThat(row.get("available")).isEqualTo(false);
        assertThat(row.get("retry_count")).isEqualTo(maxRetries);
        assertThat(row.get("image_small_url")).isNull();
    }

    @Test
    @DisplayName("7.1: una entidad FAILED no se reintenta, aunque la miniatura ya se pudiera generar")
    void failedIsNotRetried() {
        long card = pendingCard();
        jdbc.update("UPDATE card SET image_status = 'FAILED', retry_count = ? WHERE id = ?", maxRetries, card);

        assertThat(retries.execute()).isEmpty();
        assertThat(processor.thumbnails.get()).isZero();
        assertThat(row("card", card).get("image_status")).isEqualTo("FAILED");
    }

    @Test
    @DisplayName("7.2: las ubicaciones se reintentan igual que las cartas, con su miniatura de 320 × 240")
    void cardsAndLocations() throws IOException {
        long card = pendingCard();
        long location = pendingLocation();

        List<ThumbnailRetryResult> results = retries.execute();

        assertThat(results).extracting(ThumbnailRetryResult::kind)
                .containsExactly(ImageRules.Kind.CARD, ImageRules.Kind.LOCATION);
        assertThat(row("card", card).get("image_status")).isEqualTo("READY");
        Map<String, Object> row = row("location", location);
        assertThat(row.get("image_status")).isEqualTo("READY");
        assertThat(row.get("available")).isEqualTo(true);
        String smallUrl = (String) row.get("image_small_url");
        Path small = IMAGES_ROOT.resolve(smallUrl.substring(FileSystemImageStorage.URL_PREFIX.length()));
        assertThat(TestImages.read(Files.readAllBytes(small)))
                .satisfies(image -> assertThat(image.getWidth()).isEqualTo(320))
                .satisfies(image -> assertThat(image.getHeight()).isEqualTo(240));
    }

    @Test
    @DisplayName("9: dos ejecuciones simultáneas (dos instancias) no procesan la misma entidad: un solo intento y una sola miniatura")
    void concurrentRunsDoNotDuplicate() throws Exception {
        long card = pendingCard();
        processor.pauseInThumbnail();

        // Primera ejecución: bloquea la fila y se detiene dentro de la generación de la miniatura.
        CompletableFuture<List<ThumbnailRetryResult>> first = CompletableFuture.supplyAsync(retries::execute);
        assertThat(processor.entered.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();

        // Segunda ejecución, mientras la primera tiene la fila bloqueada: la salta (SKIP LOCKED).
        List<ThumbnailRetryResult> second = retries.execute();
        processor.release.countDown();

        assertThat(second).isEmpty();
        assertThat(first.get(WAIT_SECONDS, TimeUnit.SECONDS)).extracting(ThumbnailRetryResult::outcome)
                .containsExactly(Outcome.READY);
        assertThat(processor.thumbnails.get()).isEqualTo(1);
        assertThat(row("card", card).get("retry_count")).isEqualTo(1);
    }

    @Test
    @DisplayName("7.1: si falta la versión grande en disco, la tarea la deja FAILED y no disponible, con un aviso en el log")
    void missingLargeImage(CapturedOutput output) {
        long card = pendingCard();
        deleteContents(IMAGES_ROOT);

        job.run();

        Map<String, Object> row = row("card", card);
        assertThat(row.get("image_status")).isEqualTo("FAILED");
        assertThat(row.get("available")).isEqualTo(false);
        assertThat(output).contains("Falta en disco la versión grande de CARD " + card).contains("WARN");
    }

    // =========================================================================================
    // Ayudantes.
    // =========================================================================================

    /** Carta como la deja Crear carta cuando falla la miniatura: solo la versión grande, PENDING_RETRY. */
    private long pendingCard() {
        StoredImages stored = storage.save(ImageRules.CARD, validated(TestImages.png(600, 840), 600, 840), null);
        return jdbc.queryForObject("""
                INSERT INTO card (code, name, name_normalized, cost, power, type_code, rarity_code, ability_code,
                                  image_large_url, image_status, available, created_at)
                VALUES ('PKA-FUE-001', 'Pendiente', 'pendiente', 1, 1, 'FUE', 'COMMON', 'HAB-000', ?,
                        'PENDING_RETRY', FALSE, now())
                RETURNING id
                """, Long.class, stored.largeUrl());
    }

    /** Ubicación como la deja Crear ubicación cuando falla la miniatura. */
    private long pendingLocation() {
        StoredImages stored = storage.save(ImageRules.LOCATION, validated(TestImages.png(800, 600), 800, 600), null);
        return jdbc.queryForObject("""
                INSERT INTO location (name, description, card_limit, extra_energy_mode, extra_energy_amount,
                                      image_large_url, image_status, available, created_at)
                VALUES ('Pendiente', 'Sin miniatura', 4, 'NONE', 0, ?, 'PENDING_RETRY', FALSE, now())
                RETURNING id
                """, Long.class, stored.largeUrl());
    }

    private static ValidatedImage validated(byte[] content, int width, int height) {
        return new ValidatedImage(ImageFormat.PNG, width, height, content);
    }

    private Map<String, Object> row(String table, long id) {
        return jdbc.queryForMap("SELECT image_status, available, retry_count, image_small_url FROM " + table
                + " WHERE id = ?", id);
    }

    private void clean() {
        jdbc.update("DELETE FROM card");
        jdbc.update("DELETE FROM location");
        jdbc.update("UPDATE card_type_counter SET last_number = 0");
    }

    private static void deleteContents(Path folder) {
        if (!Files.exists(folder)) {
            return;
        }
        try (Stream<Path> files = Files.walk(folder)) {
            for (Path path : files.sorted(Comparator.reverseOrder()).toList()) {
                if (!path.equals(folder)) {
                    Files.deleteIfExists(path);
                }
            }
        } catch (IOException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static Path createRoot() {
        try {
            return Files.createTempDirectory("pokearena-reintentos-");
        } catch (IOException failure) {
            throw new IllegalStateException(failure);
        }
    }

    // =========================================================================================
    // Procesador controlable: delega en el real, y puede fallar o detenerse a pedido.
    // =========================================================================================

    static class Controls {

        @Bean
        @Primary
        ControlledImageProcessor controlledImageProcessor(ImageIoProcessor real) {
            return new ControlledImageProcessor(real);
        }
    }

    static class ControlledImageProcessor implements ImageProcessor {
        private final ImageProcessor real;
        final AtomicInteger thumbnails = new AtomicInteger();
        volatile boolean failThumbnail;
        volatile CountDownLatch entered = new CountDownLatch(0);
        volatile CountDownLatch release = new CountDownLatch(0);

        ControlledImageProcessor(ImageProcessor real) {
            this.real = real;
        }

        /** La próxima miniatura avisa por {@code entered} y espera a {@code release}. */
        void pauseInThumbnail() {
            entered = new CountDownLatch(1);
            release = new CountDownLatch(1);
        }

        void reset() {
            failThumbnail = false;
            thumbnails.set(0);
            release.countDown();
            entered = new CountDownLatch(0);
            release = new CountDownLatch(0);
        }

        @Override
        public ValidatedImage validate(byte[] content, ImageRules rules) {
            return real.validate(content, rules);
        }

        @Override
        public byte[] generateThumbnail(ValidatedImage image, ImageRules rules) {
            thumbnails.incrementAndGet();
            entered.countDown();
            try {
                if (!release.await(WAIT_SECONDS, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("La prueba no liberó la miniatura a tiempo");
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(interrupted);
            }
            if (failThumbnail) {
                throw new ThumbnailGenerationException("Falla simulada de la miniatura", null);
            }
            return real.generateThumbnail(image, rules);
        }
    }
}
