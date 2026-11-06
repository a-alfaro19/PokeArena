package cr.ac.tec.pokearena.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import cr.ac.tec.pokearena.application.dto.ThumbnailRetryResult;
import cr.ac.tec.pokearena.application.dto.ThumbnailRetryResult.Outcome;
import cr.ac.tec.pokearena.application.port.ImageFile;
import cr.ac.tec.pokearena.application.port.ImageProcessor;
import cr.ac.tec.pokearena.application.port.ImageStorage;
import cr.ac.tec.pokearena.application.port.StoredImages;
import cr.ac.tec.pokearena.application.port.UnitOfWork;
import cr.ac.tec.pokearena.application.port.ValidatedImage;
import cr.ac.tec.pokearena.domain.error.ThumbnailGenerationException;
import cr.ac.tec.pokearena.domain.model.ImageFormat;
import cr.ac.tec.pokearena.domain.model.ImageStatus;
import cr.ac.tec.pokearena.domain.model.PendingImage;
import cr.ac.tec.pokearena.domain.repository.PendingImageRepository;
import cr.ac.tec.pokearena.domain.rule.ImageRules;

/**
 * Propósito: pruebas unitarias de {@link ThumbnailRetryUseCase} con los puertos en memoria.
 * Responsabilidades: comprobar las transiciones de estado de cada intento (READY, sigue pendiente,
 * FAILED por agotamiento, FAILED por falta de la versión grande), que cada intento suma uno a
 * {@code retry_count}, que una entidad FAILED no se reintenta y que hay un solo intento por entidad en
 * cada ejecución. Sin Spring ni base de datos. La concurrencia real se prueba en {@code ThumbnailRetryIT}.
 * Referencia: sección 7.1 ("Si falla la miniatura").
 */
@DisplayName("Caso de uso de reintentos de miniaturas (puertos en memoria)")
class ThumbnailRetryUseCaseTest {

    private static final int MAX_RETRIES = 3;
    private static final String CARD_LARGE = "/images/cards/carta/large.png";
    private static final String LOCATION_LARGE = "/images/locations/ubicacion/large.png";

    private final InMemoryPending repository = new InMemoryPending();
    private final InMemoryStorage storage = new InMemoryStorage();
    private final SwitchableProcessor processor = new SwitchableProcessor();
    private final ThumbnailRetryUseCase useCase = new ThumbnailRetryUseCase(new DirectUnitOfWork(), repository,
            storage, processor, MAX_RETRIES);

    @Test
    @DisplayName("Éxito tras un fallo: la miniatura se genera, la entidad queda READY y disponible, con su URL y un intento más")
    void successAfterFailure() {
        Row card = repository.add(ImageRules.Kind.CARD, CARD_LARGE);
        storage.largeFiles.put(CARD_LARGE, new byte[] { 1 });

        List<ThumbnailRetryResult> results = useCase.execute();

        assertThat(results).extracting(ThumbnailRetryResult::outcome).containsExactly(Outcome.READY);
        assertThat(card.status).isEqualTo(ImageStatus.READY);
        assertThat(card.available).isTrue();
        assertThat(card.smallUrl).isEqualTo("/images/cards/carta/small.png");
        assertThat(card.retryCount).isEqualTo(1);
    }

    @Test
    @DisplayName("Agotamiento: cada intento fallido suma uno y en el último pasa a FAILED, no disponible, con retry_count igual al máximo")
    void exhaustion() {
        Row card = repository.add(ImageRules.Kind.CARD, CARD_LARGE);
        storage.largeFiles.put(CARD_LARGE, new byte[] { 1 });
        processor.fail = true;

        for (int attempt = 1; attempt < MAX_RETRIES; attempt++) {
            assertThat(useCase.execute()).extracting(ThumbnailRetryResult::outcome).containsExactly(Outcome.STILL_PENDING);
            assertThat(card.status).isEqualTo(ImageStatus.PENDING_RETRY);
            assertThat(card.retryCount).isEqualTo(attempt);
        }
        assertThat(useCase.execute()).extracting(ThumbnailRetryResult::outcome).containsExactly(Outcome.FAILED);

        assertThat(card.status).isEqualTo(ImageStatus.FAILED);
        assertThat(card.available).isFalse();
        assertThat(card.retryCount).isEqualTo(MAX_RETRIES);
    }

    @Test
    @DisplayName("Una entidad FAILED no se reintenta, aunque la miniatura ya se pudiera generar")
    void failedIsNotRetried() {
        Row card = repository.add(ImageRules.Kind.CARD, CARD_LARGE);
        card.status = ImageStatus.FAILED;
        card.retryCount = MAX_RETRIES;
        storage.largeFiles.put(CARD_LARGE, new byte[] { 1 });

        assertThat(useCase.execute()).isEmpty();
        assertThat(card.status).isEqualTo(ImageStatus.FAILED);
        assertThat(processor.thumbnails).isZero();
    }

    @Test
    @DisplayName("Si falta la versión grande en disco, queda FAILED de inmediato y no disponible")
    void missingLargeImage() {
        Row card = repository.add(ImageRules.Kind.CARD, CARD_LARGE);

        assertThat(useCase.execute()).extracting(ThumbnailRetryResult::outcome)
                .containsExactly(Outcome.LARGE_IMAGE_MISSING);
        assertThat(card.status).isEqualTo(ImageStatus.FAILED);
        assertThat(card.available).isFalse();
        assertThat(card.retryCount).isEqualTo(1);
    }

    @Test
    @DisplayName("Cartas y ubicaciones: cada una con su tamaño de miniatura")
    void cardsAndLocations() {
        repository.add(ImageRules.Kind.CARD, CARD_LARGE);
        repository.add(ImageRules.Kind.LOCATION, LOCATION_LARGE);
        storage.largeFiles.put(CARD_LARGE, new byte[] { 1 });
        storage.largeFiles.put(LOCATION_LARGE, new byte[] { 2 });

        List<ThumbnailRetryResult> results = useCase.execute();

        assertThat(results).extracting(ThumbnailRetryResult::kind)
                .containsExactly(ImageRules.Kind.CARD, ImageRules.Kind.LOCATION);
        assertThat(processor.rulesUsed).containsExactly(ImageRules.CARD, ImageRules.LOCATION);
    }

    @Test
    @DisplayName("Un solo intento por entidad en cada ejecución: una que sigue pendiente espera a la siguiente")
    void oneAttemptPerRun() {
        Row first = repository.add(ImageRules.Kind.CARD, CARD_LARGE);
        Row second = repository.add(ImageRules.Kind.CARD, CARD_LARGE);
        storage.largeFiles.put(CARD_LARGE, new byte[] { 1 });
        processor.fail = true;

        assertThat(useCase.execute()).hasSize(2);
        assertThat(first.retryCount).isEqualTo(1);
        assertThat(second.retryCount).isEqualTo(1);
    }

    @Test
    @DisplayName("Un máximo de reintentos menor que 1 es una configuración inválida")
    void invalidMaximum() {
        assertThatThrownBy(() -> new ThumbnailRetryUseCase(new DirectUnitOfWork(), repository, storage, processor, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // =========================================================================================
    // Puertos en memoria.
    // =========================================================================================

    /** Sin transacción real: el estado en memoria cambia en el momento. */
    private static final class DirectUnitOfWork implements UnitOfWork {
        @Override
        public <T> T execute(Supplier<T> work) {
            return work.get();
        }
    }

    private static final class Row {
        final ImageRules.Kind kind;
        final long id;
        final String largeUrl;
        ImageStatus status = ImageStatus.PENDING_RETRY;
        int retryCount;
        boolean available;
        String smallUrl;

        Row(ImageRules.Kind kind, long id, String largeUrl) {
            this.kind = kind;
            this.id = id;
            this.largeUrl = largeUrl;
        }
    }

    private static final class InMemoryPending implements PendingImageRepository {
        final List<Row> rows = new ArrayList<>();

        Row add(ImageRules.Kind kind, String largeUrl) {
            Row row = new Row(kind, rows.size() + 1, largeUrl);
            rows.add(row);
            return row;
        }

        @Override
        public Optional<PendingImage> lockNextPending(ImageRules.Kind kind, int maxRetries, long afterId) {
            return rows.stream()
                    .filter(row -> row.kind == kind && row.status == ImageStatus.PENDING_RETRY
                            && row.retryCount < maxRetries && row.id > afterId)
                    .findFirst()
                    .map(row -> new PendingImage(kind, row.id, row.largeUrl, row.retryCount));
        }

        @Override
        public void markReady(ImageRules.Kind kind, long id, String smallUrl) {
            Row row = find(id);
            row.status = ImageStatus.READY;
            row.available = true;
            row.smallUrl = smallUrl;
            row.retryCount++;
        }

        @Override
        public void markAttemptFailed(ImageRules.Kind kind, long id, ImageStatus status) {
            Row row = find(id);
            row.status = status;
            row.available = false;
            row.retryCount++;
        }

        private Row find(long id) {
            return rows.stream().filter(row -> row.id == id).findFirst().orElseThrow();
        }
    }

    private static final class InMemoryStorage implements ImageStorage {
        final Map<String, byte[]> largeFiles = new HashMap<>();

        @Override
        public Optional<byte[]> readLarge(String largeUrl) {
            return Optional.ofNullable(largeFiles.get(largeUrl));
        }

        @Override
        public String saveThumbnail(String largeUrl, byte[] thumbnail) {
            return largeUrl.replace("large.png", "small.png");
        }

        @Override
        public StoredImages save(ImageRules rules, ValidatedImage large, byte[] thumbnail) {
            throw new UnsupportedOperationException("Los reintentos no crean entidades");
        }

        @Override
        public void delete(String storageKey) {
            throw new UnsupportedOperationException("Los reintentos no borran entidades");
        }

        @Override
        public Optional<ImageFile> find(String relativePath) {
            throw new UnsupportedOperationException("Los reintentos no sirven imágenes");
        }
    }

    /** Procesador que acepta cualquier contenido y cuya miniatura puede fallar a pedido. */
    private static final class SwitchableProcessor implements ImageProcessor {
        boolean fail;
        int thumbnails;
        final List<ImageRules> rulesUsed = new ArrayList<>();

        @Override
        public ValidatedImage validate(byte[] content, ImageRules rules) {
            return new ValidatedImage(ImageFormat.PNG, rules.minWidth(), rules.minHeight(), content);
        }

        @Override
        public byte[] generateThumbnail(ValidatedImage image, ImageRules rules) {
            thumbnails++;
            rulesUsed.add(rules);
            if (fail) {
                throw new ThumbnailGenerationException("Falla simulada de la miniatura", null);
            }
            return new byte[] { 9 };
        }
    }
}
