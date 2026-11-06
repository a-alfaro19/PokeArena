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

import cr.ac.tec.pokearena.application.dto.CardDetails;
import cr.ac.tec.pokearena.application.dto.CreateCardCommand;
import cr.ac.tec.pokearena.application.dto.IdempotentRequest;
import cr.ac.tec.pokearena.application.dto.IdempotentResult;
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
import cr.ac.tec.pokearena.domain.error.DomainException;
import cr.ac.tec.pokearena.domain.error.ErrorType;
import cr.ac.tec.pokearena.domain.error.IdempotencyConflictException;
import cr.ac.tec.pokearena.domain.error.ThumbnailGenerationException;
import cr.ac.tec.pokearena.domain.model.Ability;
import cr.ac.tec.pokearena.domain.model.Card;
import cr.ac.tec.pokearena.domain.model.CardSearchCriteria;
import cr.ac.tec.pokearena.domain.model.CardType;
import cr.ac.tec.pokearena.domain.model.Rarity;
import cr.ac.tec.pokearena.domain.model.StoredCard;
import cr.ac.tec.pokearena.domain.repository.CardCatalogRepository;
import cr.ac.tec.pokearena.domain.repository.CardRepository;
import cr.ac.tec.pokearena.domain.rule.ImageRules;
import cr.ac.tec.pokearena.infrastructure.storage.ImageIoProcessor;
import cr.ac.tec.pokearena.support.TestImages;

/**
 * Propósito: pruebas unitarias de {@link CreateCardUseCase} con los puertos en memoria (historia 2.1.1).
 * Responsabilidades: comprobar el orden de la plantilla (idempotencia, validaciones antes de guardar
 * nada, efectos externos, transacción), el código por tipo, la miniatura fallida, la compensación de
 * archivos y que una transacción revertida no consume el consecutivo. Sin Spring ni base de datos.
 * Referencia: sección 7.1 y Anexo A.1.
 *
 * <p>La unidad de trabajo en memoria toma una foto del estado antes de la transacción y la restaura
 * si hay una excepción, como haría PostgreSQL.
 */
@DisplayName("Caso de uso Crear carta (puertos en memoria)")
class CreateCardUseCaseTest {

    private static final int CREATED = 201;
    private static final Instant NOW = Instant.parse("2026-11-06T12:00:00Z");
    private static final byte[] VALID_IMAGE = TestImages.png(600, 840);

    private final InMemoryCards cards = new InMemoryCards();
    private final InMemoryIdempotencyStore keys = new InMemoryIdempotencyStore();
    private final InMemoryImageStorage storage = new InMemoryImageStorage();
    private final CountingImageProcessor processor = new CountingImageProcessor();
    private final Map<String, Ability> abilityTable = new HashMap<>(Map.of(
            "HAB-000", new Ability("HAB-000", "Sin Habilidad", null, null),
            "HAB-LIM", new Ability("HAB-LIM", "Solo agua", "AGU", null)));

    private final IdempotencyGuard guard = new IdempotencyGuard(keys, new ObjectSerializer());

    private final CreateCardUseCase useCase = new CreateCardUseCase(guard,
            new ImageCreationSteps(guard, processor, storage, new SnapshotUnitOfWork()), new FixedCatalog(),
            code -> Optional.ofNullable(abilityTable.get(code)), cards, processor, storage, () -> NOW);

    private final UUID admin = UUID.randomUUID();

    private IdempotentResult<CardDetails> create(String key, CreateCardCommand command) {
        return useCase.execute(new IdempotentRequest(new IdempotencyKey(key, admin, "POST /api/v1/cartas"),
                "hash-" + command.name() + command.cost() + command.typeCode() + command.abilityCode(), CREATED), command);
    }

    private static CreateCardCommand command(String type, String ability) {
        return new CreateCardCommand("Charmander", "3", "4", type, "COMMON", ability, VALID_IMAGE);
    }

    @Nested
    @DisplayName("Éxito")
    class Success {

        @Test
        @DisplayName("Una carta válida queda guardada, READY y disponible, con su ficha completa")
        void createsAvailableCard() {
            IdempotentResult<CardDetails> result = create("k1", command("FUE", "HAB-000"));
            CardDetails card = result.body();

            assertThat(result.status()).isEqualTo(CREATED);
            assertThat(card.code()).isEqualTo("PKA-FUE-001");
            assertThat(card.imageStatus()).isEqualTo("READY");
            assertThat(card.available()).isTrue();
            assertThat(card.imageSmallUrl()).isNotNull();
            assertThat(card.type().name()).isEqualTo("Fuego");
            assertThat(card.ability().code()).isEqualTo("HAB-000");
            assertThat(cards.saved).hasSize(1);
            assertThat(cards.saved.getFirst().createdAt()).isEqualTo(NOW);
        }

        @Test
        @DisplayName("El consecutivo es por tipo: FUE-001, FUE-002 y AGU-001")
        void sequenceIsPerType() {
            assertThat(create("k1", command("FUE", "HAB-000")).body().code()).isEqualTo("PKA-FUE-001");
            assertThat(create("k2", new CreateCardCommand("Otra", "1", "1", "FUE", "COMMON", "HAB-000", VALID_IMAGE))
                    .body().code()).isEqualTo("PKA-FUE-002");
            assertThat(create("k3", command("AGU", "HAB-000")).body().code()).isEqualTo("PKA-AGU-001");
        }

        @Test
        @DisplayName("La ficha muestra el tipo al que está limitada la habilidad")
        void showsLimitedType() {
            assertThat(create("k1", command("AGU", "HAB-LIM")).body().ability().limitedToType()).isEqualTo("AGU");
        }
    }

    @Nested
    @DisplayName("Validaciones antes de guardar nada")
    class ValidationsFirst {

        @Test
        @DisplayName("Un campo inválido se rechaza sin procesar la imagen, sin archivos y sin consumir consecutivo")
        void invalidFieldLeavesNoTrace() {
            assertDomainError(() -> create("k1", new CreateCardCommand("Charmander", "11", "4", "FUE", "COMMON",
                    "HAB-000", VALID_IMAGE)), ErrorType.INVALID_FIELD, "cost");

            assertNothingStored();
            assertThat(processor.validations).isZero();
        }

        @Test
        @DisplayName("Sin habilidad es MISSING_FIELD e indica que debe elegir una, aunque sea Sin Habilidad")
        void missingAbility() {
            DomainException error = assertDomainError(() -> create("k1", command("FUE", null)),
                    ErrorType.MISSING_FIELD, "ability");

            assertThat(error.getMessage()).contains("HAB-000");
            assertNothingStored();
        }

        @Test
        @DisplayName("Una habilidad inexistente es UNKNOWN_ABILITY")
        void unknownAbility() {
            assertDomainError(() -> create("k1", command("FUE", "HAB-999")), ErrorType.UNKNOWN_ABILITY, "ability");
            assertNothingStored();
        }

        @Test
        @DisplayName("Sin imagen es MISSING_FIELD sobre image")
        void missingImage() {
            assertDomainError(() -> create("k1", new CreateCardCommand("Charmander", "3", "4", "FUE", "COMMON",
                    "HAB-000", null)), ErrorType.MISSING_FIELD, "image");
            assertNothingStored();
        }

        @Test
        @DisplayName("Una imagen inválida se rechaza sin guardar archivos ni filas")
        void invalidImage() {
            assertDomainError(() -> create("k1", new CreateCardCommand("Charmander", "3", "4", "FUE", "COMMON",
                    "HAB-000", TestImages.png(600, 600))), ErrorType.INVALID_IMAGE_RATIO, "image");
            assertNothingStored();
        }
    }

    @Nested
    @DisplayName("Miniatura, transacción y compensación")
    class EffectsAndTransaction {

        @Test
        @DisplayName("Si la miniatura falla, la carta se guarda PENDING_RETRY, no disponible y sin URL pequeña")
        void thumbnailFailure() {
            processor.failThumbnail = true;

            CardDetails card = create("k1", command("FUE", "HAB-000")).body();

            assertThat(card.imageStatus()).isEqualTo("PENDING_RETRY");
            assertThat(card.available()).isFalse();
            assertThat(card.imageSmallUrl()).isNull();
            assertThat(cards.saved).hasSize(1);
        }

        @Test
        @DisplayName("Si falla el guardado en base, se borran los archivos y el consecutivo no se consume")
        void databaseFailureCompensatesFiles() {
            cards.failOnSave = true;

            assertThatThrownBy(() -> create("k1", command("FUE", "HAB-000"))).isInstanceOf(IllegalStateException.class);

            assertThat(storage.saved).hasSize(1);
            assertThat(storage.deleted).containsExactlyElementsOf(storage.saved);
            assertThat(cards.counters).doesNotContainKey("FUE");
            assertThat(keys.responses).isEmpty();
        }

        @Test
        @DisplayName("Una habilidad que evolucionaría a la misma carta es INVALID_EVOLUTION, sin consumir el código")
        void selfEvolution() {
            abilityTable.put("HAB-EVO", new Ability("HAB-EVO", "Evoluciona a sí misma", null, "PKA-FUE-001"));

            assertDomainError(() -> create("k1", command("FUE", "HAB-EVO")), ErrorType.INVALID_EVOLUTION, "ability");

            assertThat(cards.counters).doesNotContainKey("FUE");
            assertThat(cards.saved).isEmpty();
            assertThat(storage.deleted).containsExactlyElementsOf(storage.saved);
        }

        @Test
        @DisplayName("Una habilidad que formaría un ciclo de evoluciones es INVALID_EVOLUTION")
        void evolutionCycle() {
            // Ya existe PKA-AGU-001, cuya habilidad evoluciona a PKA-FUE-001: la carta nueva cerraría el ciclo.
            cards.evolutions.put("PKA-AGU-001", "PKA-FUE-001");
            abilityTable.put("HAB-CICLO", new Ability("HAB-CICLO", "Evoluciona a agua", null, "PKA-AGU-001"));

            assertDomainError(() -> create("k1", command("FUE", "HAB-CICLO")), ErrorType.INVALID_EVOLUTION, "ability");
            assertThat(cards.counters).doesNotContainKey("FUE");
        }

        @Test
        @DisplayName("Después de la carta 999 de un tipo, la siguiente es PKA-FUE-1000: el consecutivo no tiene máximo")
        void codeContinuesAfter999() {
            cards.counters.put("FUE", 999);

            assertThat(create("k1", command("FUE", "HAB-000")).body().code()).isEqualTo("PKA-FUE-1000");
            assertThat(cards.counters).containsEntry("FUE", 1000);
        }
    }

    @Nested
    @DisplayName("Idempotencia")
    class Idempotency {

        @Test
        @DisplayName("Reintentar con la misma clave devuelve la misma ficha sin crear otra carta ni consumir otro código")
        void retryReplays() {
            IdempotentResult<CardDetails> first = create("k1", command("FUE", "HAB-000"));
            IdempotentResult<CardDetails> retry = create("k1", command("FUE", "HAB-000"));

            assertThat(retry.body()).isEqualTo(first.body());
            assertThat(retry.replayed()).isTrue();
            assertThat(cards.saved).hasSize(1);
            assertThat(cards.counters).containsEntry("FUE", 1);
            assertThat(storage.saved).hasSize(1);
        }

        @Test
        @DisplayName("Misma clave con otro contenido es IDEMPOTENCY_KEY_REUSED")
        void reusedKey() {
            create("k1", command("FUE", "HAB-000"));

            assertDomainError(() -> create("k1", command("AGU", "HAB-000")), ErrorType.IDEMPOTENCY_KEY_REUSED, null);
        }

        @Test
        @DisplayName("Si otra solicitud igual confirma primero, se borran los archivos propios y se repite su ficha")
        void concurrentWinnerIsReplayed() {
            CardDetails winner = create("k0", command("FUE", "HAB-000")).body();
            keys.simulateWinnerOnNextSave = winner;

            IdempotentResult<CardDetails> result = create("k1", command("FUE", "HAB-000"));

            assertThat(result.body()).isEqualTo(winner);
            assertThat(storage.deleted).hasSize(1);
            assertThat(cards.saved).hasSize(1);
        }
    }

    // ---------------------------------------------------------------------------------------
    // Ayudantes y puertos en memoria.
    // ---------------------------------------------------------------------------------------

    private static DomainException assertDomainError(Runnable action, ErrorType type, String field) {
        try {
            action.run();
        } catch (DomainException error) {
            assertThat(error.type()).isEqualTo(type);
            if (field != null) {
                assertThat(error.field()).contains(field);
            }
            return error;
        }
        throw new AssertionError("Se esperaba " + type);
    }

    private void assertNothingStored() {
        assertThat(storage.saved).isEmpty();
        assertThat(cards.saved).isEmpty();
        assertThat(cards.counters).isEmpty();
        assertThat(keys.responses).isEmpty();
    }

    /** Participa en la transacción en memoria: puede guardar y restaurar su estado. */
    private interface Transactional {
        Runnable snapshot();
    }

    /** Unidad de trabajo que restaura el estado de los participantes si hay una excepción. */
    private final class SnapshotUnitOfWork implements UnitOfWork {
        @Override
        public <T> T execute(Supplier<T> work) {
            List<Runnable> restores = List.of(cards.snapshot(), keys.snapshot());
            try {
                return work.get();
            } catch (RuntimeException failure) {
                restores.forEach(Runnable::run);
                throw failure;
            }
        }
    }

    private static final class FixedCatalog implements CardCatalogRepository {
        @Override
        public List<CardType> findAllTypes() {
            return List.of(new CardType("AGU", "Agua"), new CardType("FUE", "Fuego"));
        }

        @Override
        public List<Rarity> findAllRarities() {
            return List.of(new Rarity("COMMON", "Común"));
        }
    }

    private static final class InMemoryCards implements CardRepository, Transactional {
        final Map<String, Integer> counters = new HashMap<>();
        final List<Card> saved = new ArrayList<>();
        final Map<String, String> evolutions = new HashMap<>();
        boolean failOnSave;

        @Override
        public int nextNumber(String typeCode) {
            return counters.merge(typeCode, 1, Integer::sum);
        }

        @Override
        public void save(Card card) {
            if (failOnSave) {
                throw new IllegalStateException("Falla simulada de la base");
            }
            saved.add(card);
        }

        @Override
        public Optional<String> evolutionTargetOf(String cardCode) {
            return Optional.ofNullable(evolutions.get(cardCode));
        }

        @Override
        public List<StoredCard> search(CardSearchCriteria criteria, long afterId, int maxRows) {
            throw new UnsupportedOperationException("Crear carta no busca cartas");
        }

        @Override
        public Runnable snapshot() {
            Map<String, Integer> countersBefore = new HashMap<>(counters);
            List<Card> savedBefore = new ArrayList<>(saved);
            return () -> {
                counters.clear();
                counters.putAll(countersBefore);
                saved.clear();
                saved.addAll(savedBefore);
            };
        }
    }

    private static final class InMemoryIdempotencyStore implements IdempotencyStore, Transactional {
        final Map<IdempotencyKey, StoredResponse> responses = new HashMap<>();
        // Lo que confirmó "otra solicitud" en la simulación de carrera: la reversión propia no lo toca.
        private final Map<IdempotencyKey, StoredResponse> committedByOthers = new HashMap<>();
        Object simulateWinnerOnNextSave;

        @Override
        public Optional<StoredResponse> find(IdempotencyKey key) {
            return Optional.ofNullable(responses.get(key));
        }

        @Override
        public void save(IdempotencyKey key, StoredResponse response) {
            if (simulateWinnerOnNextSave != null) {
                // Otra solicitud igual confirmó primero con su propia respuesta.
                StoredResponse winner = new StoredResponse(response.requestHash(), response.status(),
                        ObjectSerializer.register(simulateWinnerOnNextSave));
                simulateWinnerOnNextSave = null;
                committedByOthers.put(key, winner);
                responses.put(key, winner);
                throw new IdempotencyConflictException("Clave ya guardada", null);
            }
            if (responses.putIfAbsent(key, response) != null) {
                throw new IdempotencyConflictException("Clave ya guardada", null);
            }
        }

        @Override
        public Runnable snapshot() {
            Map<IdempotencyKey, StoredResponse> before = new HashMap<>(responses);
            return () -> {
                responses.clear();
                responses.putAll(before);
                responses.putAll(committedByOthers);
            };
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
            String key = "cards/" + UUID.randomUUID();
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

        @Override
        public Optional<byte[]> readLarge(String largeUrl) {
            throw new UnsupportedOperationException("Solo lo usa la tarea de reintentos");
        }

        @Override
        public String saveThumbnail(String largeUrl, byte[] thumbnail) {
            throw new UnsupportedOperationException("Solo lo usa la tarea de reintentos");
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
