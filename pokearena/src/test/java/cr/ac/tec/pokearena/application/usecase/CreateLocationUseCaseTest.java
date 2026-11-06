package cr.ac.tec.pokearena.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import cr.ac.tec.pokearena.application.dto.CreateLocationCommand;
import cr.ac.tec.pokearena.application.dto.IdempotentRequest;
import cr.ac.tec.pokearena.application.dto.IdempotentResult;
import cr.ac.tec.pokearena.application.dto.LocationDetails;
import cr.ac.tec.pokearena.application.port.IdempotencyKey;
import cr.ac.tec.pokearena.application.port.IdempotencyStore;
import cr.ac.tec.pokearena.application.port.ImageFile;
import cr.ac.tec.pokearena.application.port.ImageProcessor;
import cr.ac.tec.pokearena.application.port.ImageStorage;
import cr.ac.tec.pokearena.application.port.ResponseSerializer;
import cr.ac.tec.pokearena.application.port.StoredImages;
import cr.ac.tec.pokearena.application.port.StoredResponse;
import cr.ac.tec.pokearena.application.port.UnitOfWork;
import cr.ac.tec.pokearena.application.port.ValidatedImage;
import cr.ac.tec.pokearena.domain.effect.CostModifierEffect;
import cr.ac.tec.pokearena.domain.effect.LocationEffectRegistry;
import cr.ac.tec.pokearena.domain.effect.LocationEffectType;
import cr.ac.tec.pokearena.domain.effect.TurnRestrictionEffect;
import cr.ac.tec.pokearena.domain.effect.TypeBonusEffect;
import cr.ac.tec.pokearena.domain.error.DomainException;
import cr.ac.tec.pokearena.domain.error.ErrorType;
import cr.ac.tec.pokearena.domain.error.IdempotencyConflictException;
import cr.ac.tec.pokearena.domain.error.ThumbnailGenerationException;
import cr.ac.tec.pokearena.domain.error.ValidationException;
import cr.ac.tec.pokearena.domain.model.Location;
import cr.ac.tec.pokearena.domain.repository.LocationRepository;
import cr.ac.tec.pokearena.domain.rule.ImageRules;
import cr.ac.tec.pokearena.infrastructure.persistence.JacksonEffectReader;
import cr.ac.tec.pokearena.infrastructure.storage.ImageIoProcessor;
import cr.ac.tec.pokearena.support.TestImages;
import tools.jackson.databind.json.JsonMapper;

/**
 * Propósito: pruebas unitarias de {@link CreateLocationUseCase} con los puertos en memoria (historia 2.2.1).
 * Responsabilidades: comprobar la misma plantilla que Crear carta (idempotencia, validaciones antes de
 * guardar nada, efectos externos, transacción), el efecto opcional validado por su plug-in, la
 * miniatura fallida, la compensación de archivos y que un efecto nuevo no modifica el caso de uso.
 * Sin Spring ni base de datos.
 * Referencia: sección 7.2 y Anexo A.2.
 */
@DisplayName("Caso de uso Crear ubicación (puertos en memoria)")
class CreateLocationUseCaseTest {

    private static final int CREATED = 201;
    private static final Instant NOW = Instant.parse("2026-11-06T12:00:00Z");
    private static final byte[] VALID_IMAGE = TestImages.png(800, 600);
    private static final List<String> TYPES = List.of("AGU", "FUE");
    private static final List<LocationEffectType> STANDARD_EFFECTS = List.of(new TypeBonusEffect(() -> TYPES),
            new CostModifierEffect(() -> TYPES), new TurnRestrictionEffect());

    private final InMemoryLocations locations = new InMemoryLocations();
    private final InMemoryIdempotencyStore keys = new InMemoryIdempotencyStore();
    private final InMemoryImageStorage storage = new InMemoryImageStorage();
    private final CountingImageProcessor processor = new CountingImageProcessor();
    private final IdempotencyGuard guard = new IdempotencyGuard(keys, new ObjectSerializer());
    private final UUID admin = UUID.randomUUID();

    private CreateLocationUseCase useCase(List<LocationEffectType> effects) {
        return new CreateLocationUseCase(guard, new ImageCreationSteps(guard, processor, storage, new SnapshotUnitOfWork()),
                new JacksonEffectReader(JsonMapper.builder().build()), new LocationEffectRegistry(effects), locations,
                processor, storage, () -> NOW);
    }

    private IdempotentResult<LocationDetails> create(String key, CreateLocationCommand command) {
        return useCase(STANDARD_EFFECTS).execute(request(key, command), command);
    }

    private IdempotentRequest request(String key, CreateLocationCommand command) {
        return new IdempotentRequest(new IdempotencyKey(key, admin, "POST /api/v1/ubicaciones"),
                "hash-" + command.name() + command.cardLimit() + command.effect(), CREATED);
    }

    private static CreateLocationCommand command(String effect) {
        return new CreateLocationCommand("Volcán", "Un volcán activo", "3", "FIXED", "2", effect, VALID_IMAGE);
    }

    @Nested
    @DisplayName("Éxito")
    class Success {

        @Test
        @DisplayName("Una ubicación válida con efecto queda guardada, READY y disponible, con su ficha completa")
        void createsAvailableLocationWithEffect() {
            LocationDetails location = create("k1",
                    command("{\"code\":\"TYPE_BONUS\",\"parameters\":{\"type\":\"FUE\",\"amount\":3}}")).body();

            assertThat(location.imageStatus()).isEqualTo("READY");
            assertThat(location.available()).isTrue();
            assertThat(location.effect().code()).isEqualTo("TYPE_BONUS");
            assertThat(location.effect().parameters()).containsEntry("amount", 3);
            assertThat(location.extraEnergy().mode()).isEqualTo("FIXED");
            assertThat(locations.saved).hasSize(1);
            assertThat(locations.saved.getFirst().createdAt()).isEqualTo(NOW);
        }

        @Test
        @DisplayName("Sin efecto también es válida: el efecto es opcional")
        void createsLocationWithoutEffect() {
            assertThat(create("k1", command(null)).body().effect()).isNull();
        }

        @Test
        @DisplayName("Un efecto nuevo se agrega con una clase y se registra, SIN modificar el caso de uso")
        void newEffectWithoutChangingUseCase() {
            List<LocationEffectType> extended = new ArrayList<>(STANDARD_EFFECTS);
            extended.add(new WeatherEffect());
            CreateLocationCommand command = command("{\"code\":\"WEATHER\",\"parameters\":{\"kind\":\"lluvia\"}}");

            LocationDetails location = useCase(extended).execute(request("k1", command), command).body();

            assertThat(location.effect().code()).isEqualTo("WEATHER");
        }
    }

    @Nested
    @DisplayName("Validaciones antes de guardar nada")
    class ValidationsFirst {

        @Test
        @DisplayName("Un campo inválido se rechaza sin procesar la imagen, sin archivos y sin filas")
        void invalidFieldLeavesNoTrace() {
            assertDomainError(() -> create("k1", new CreateLocationCommand("Volcán", "Lava", "6", "NONE", null, null,
                    VALID_IMAGE)), ErrorType.INVALID_FIELD, "cardLimit");

            assertNothingStored();
            assertThat(processor.validations).isZero();
        }

        @Test
        @DisplayName("Un tipo de efecto inexistente es INVALID_EFFECT y no se guarda nada")
        void unknownEffect() {
            assertDomainError(() -> create("k1", command("{\"code\":\"MAGIC_FIELD\"}")), ErrorType.INVALID_EFFECT,
                    "effect");
            assertNothingStored();
            assertThat(processor.validations).isZero();
        }

        @Test
        @DisplayName("Un efecto que no es un JSON válido es INVALID_EFFECT")
        void malformedEffect() {
            assertDomainError(() -> create("k1", command("{no es json")), ErrorType.INVALID_EFFECT, "effect");
            assertNothingStored();
        }

        @Test
        @DisplayName("Los campos se validan antes que el efecto: sin nombre y con efecto inválido, se informa el nombre")
        void fieldsBeforeEffect() {
            assertDomainError(() -> create("k1", new CreateLocationCommand(null, "Lava", "3", "NONE", null,
                    "{no es json", VALID_IMAGE)), ErrorType.MISSING_FIELD, "name");
        }

        @Test
        @DisplayName("Una imagen sin la proporción 4:3 se rechaza sin guardar archivos ni filas")
        void invalidImage() {
            assertDomainError(() -> create("k1", new CreateLocationCommand("Volcán", "Lava", "3", "NONE", null, null,
                    TestImages.png(600, 840))), ErrorType.INVALID_IMAGE_RATIO, "image");
            assertNothingStored();
        }

        @Test
        @DisplayName("Sin imagen es MISSING_FIELD sobre image")
        void missingImage() {
            assertDomainError(() -> create("k1", new CreateLocationCommand("Volcán", "Lava", "3", "NONE", null, null,
                    null)), ErrorType.MISSING_FIELD, "image");
            assertNothingStored();
        }
    }

    @Nested
    @DisplayName("Miniatura, transacción y compensación")
    class EffectsAndTransaction {

        @Test
        @DisplayName("Si la miniatura falla, la ubicación se guarda PENDING_RETRY, no disponible y sin URL pequeña")
        void thumbnailFailure() {
            processor.failThumbnail = true;

            LocationDetails location = create("k1", command(null)).body();

            assertThat(location.imageStatus()).isEqualTo("PENDING_RETRY");
            assertThat(location.available()).isFalse();
            assertThat(location.imageSmallUrl()).isNull();
        }

        @Test
        @DisplayName("Si falla el guardado en base, se borran los archivos y no queda la clave")
        void databaseFailureCompensatesFiles() {
            locations.failOnSave = true;

            assertThatThrownBy(() -> create("k1", command(null))).isInstanceOf(IllegalStateException.class);

            assertThat(storage.saved).hasSize(1);
            assertThat(storage.deleted).containsExactlyElementsOf(storage.saved);
            assertThat(keys.responses).isEmpty();
        }
    }

    @Nested
    @DisplayName("Idempotencia")
    class Idempotency {

        @Test
        @DisplayName("Reintentar con la misma clave devuelve la misma ubicación sin crear otra")
        void retryReplays() {
            IdempotentResult<LocationDetails> first = create("k1", command(null));
            IdempotentResult<LocationDetails> retry = create("k1", command(null));

            assertThat(retry.body()).isEqualTo(first.body());
            assertThat(retry.replayed()).isTrue();
            assertThat(locations.saved).hasSize(1);
            assertThat(storage.saved).hasSize(1);
        }

        @Test
        @DisplayName("Si otra solicitud igual confirma primero, se borran los archivos propios y se repite su ubicación")
        void concurrentWinnerIsReplayed() {
            LocationDetails winner = create("k0", command(null)).body();
            keys.simulateWinnerOnNextSave = winner;

            IdempotentResult<LocationDetails> result = create("k1", command(null));

            assertThat(result.body()).isEqualTo(winner);
            assertThat(storage.deleted).hasSize(1);
            assertThat(locations.saved).hasSize(1);
        }
    }

    // ---------------------------------------------------------------------------------------
    // Ayudantes y puertos en memoria.
    // ---------------------------------------------------------------------------------------

    private static void assertDomainError(Runnable action, ErrorType type, String field) {
        try {
            action.run();
        } catch (DomainException error) {
            assertThat(error.type()).isEqualTo(type);
            assertThat(error.field()).contains(field);
            return;
        }
        throw new AssertionError("Se esperaba " + type);
    }

    private void assertNothingStored() {
        assertThat(storage.saved).isEmpty();
        assertThat(locations.saved).isEmpty();
        assertThat(keys.responses).isEmpty();
    }

    /** Plug-in solo de prueba: un efecto que el caso de uso no conoce. */
    private static final class WeatherEffect implements LocationEffectType {
        @Override
        public String code() {
            return "WEATHER";
        }

        @Override
        public void validate(Map<String, Object> parameters) {
            if (!(parameters.get("kind") instanceof String)) {
                throw ValidationException.invalidEffect("effect", "El clima necesita 'kind'.", List.of());
            }
        }
    }

    /** Unidad de trabajo que restaura el estado si hay una excepción, como haría la base. */
    private final class SnapshotUnitOfWork implements UnitOfWork {
        @Override
        public <T> T execute(Supplier<T> work) {
            List<Location> savedBefore = new ArrayList<>(locations.saved);
            Map<IdempotencyKey, StoredResponse> keysBefore = new HashMap<>(keys.responses);
            try {
                return work.get();
            } catch (RuntimeException failure) {
                locations.saved.clear();
                locations.saved.addAll(savedBefore);
                keys.responses.clear();
                keys.responses.putAll(keysBefore);
                keys.responses.putAll(keys.committedByOthers);
                throw failure;
            }
        }
    }

    private static final class InMemoryLocations implements LocationRepository {
        final List<Location> saved = new ArrayList<>();
        boolean failOnSave;

        @Override
        public long save(Location location) {
            if (failOnSave) {
                throw new IllegalStateException("Falla simulada de la base");
            }
            saved.add(location);
            return saved.size();
        }
    }

    private static final class InMemoryIdempotencyStore implements IdempotencyStore {
        final Map<IdempotencyKey, StoredResponse> responses = new HashMap<>();
        final Map<IdempotencyKey, StoredResponse> committedByOthers = new HashMap<>();
        Object simulateWinnerOnNextSave;

        @Override
        public Optional<StoredResponse> find(IdempotencyKey key) {
            return Optional.ofNullable(responses.get(key));
        }

        @Override
        public void save(IdempotencyKey key, StoredResponse response) {
            if (simulateWinnerOnNextSave != null) {
                StoredResponse winner = new StoredResponse(response.requestHash(), response.status(),
                        ObjectSerializer.register(simulateWinnerOnNextSave));
                simulateWinnerOnNextSave = null;
                committedByOthers.put(key, winner);
                throw new IdempotencyConflictException("Clave ya guardada", null);
            }
            if (responses.putIfAbsent(key, response) != null) {
                throw new IdempotencyConflictException("Clave ya guardada", null);
            }
        }
    }

    /** Serializador que guarda los objetos en memoria y devuelve una referencia a ellos. */
    private static final class ObjectSerializer implements ResponseSerializer {
        private static final Map<String, Object> OBJECTS = new HashMap<>();

        static String register(Object value) {
            String id = "objeto-" + UUID.randomUUID();
            OBJECTS.put(id, value);
            return id;
        }

        @Override
        public String serialize(Object value) {
            return register(value);
        }

        @Override
        public <T> T deserialize(String text, Class<T> type) {
            return type.cast(OBJECTS.get(text));
        }
    }

    private static final class InMemoryImageStorage implements ImageStorage {
        final List<String> saved = new ArrayList<>();
        final List<String> deleted = new ArrayList<>();

        @Override
        public StoredImages save(ImageRules rules, ValidatedImage large, byte[] thumbnail) {
            String key = "locations/" + UUID.randomUUID();
            saved.add(key);
            return new StoredImages(key, "/images/" + key + "/large.png",
                    thumbnail == null ? null : "/images/" + key + "/small.png");
        }

        @Override
        public void delete(String storageKey) {
            deleted.add(storageKey);
        }

        @Override
        public Optional<ImageFile> find(String relativePath) {
            return Optional.empty();
        }
    }

    /** Procesador real (ImageIO) que cuenta validaciones y puede simular una miniatura fallida. */
    private static final class CountingImageProcessor implements ImageProcessor {
        private static final long MAX_PIXELS = 16_000_000;
        private final ImageIoProcessor real = new ImageIoProcessor(MAX_PIXELS);
        int validations;
        boolean failThumbnail;

        @Override
        public ValidatedImage validate(byte[] content, ImageRules rules) {
            validations++;
            return real.validate(content, rules);
        }

        @Override
        public byte[] generateThumbnail(ValidatedImage image, ImageRules rules) {
            if (failThumbnail) {
                throw new ThumbnailGenerationException("Falla simulada de la miniatura", null);
            }
            return real.generateThumbnail(image, rules);
        }
    }
}
