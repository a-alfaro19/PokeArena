package cr.ac.tec.pokearena.support;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import org.springframework.jdbc.core.JdbcTemplate;

import cr.ac.tec.pokearena.application.port.UnitOfWork;
import cr.ac.tec.pokearena.domain.repository.PlayerCardRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Propósito: cliente HTTP de prueba para las rutas de decks (historia 3.1.3).
 * Responsabilidades: enviar {@code POST /api/v1/decks} y los {@code GET} con un token y, si se indica, una
 * {@code Idempotency-Key}, y devolver el status con el cuerpo JSON. También da cartas a un jugador por la
 * misma vía que la colección inicial. Lo comparten {@code DeckControllerIT}, {@code DeckIdempotencyIT},
 * {@code DeckConcurrencyIT} y {@code DeckPerformanceIT}, para que las cuatro hablen con la API igual.
 * Referencia: sección 7.1 y sección 13 del Sprint 3.
 */
public final class DeckHttp {

    public static final String DECKS = "/api/v1/decks";

    /** Tiempo máximo de espera de las solicitudes simultáneas. */
    private static final long CONCURRENCY_TIMEOUT_SECONDS = 30;

    private final HttpClient http = HttpClient.newHttpClient();
    private final JsonMapper json = JsonMapper.builder().build();
    private final int port;

    public DeckHttp(int port) {
        this.port = port;
    }

    /** Respuesta: status y cuerpo JSON ({@code null} si vino vacío). */
    public record Result(int status, JsonNode body) {
    }

    /** {@code POST /api/v1/decks} con {@code { name, cards }}; {@code cards} puede ser {@code null}. */
    public Result create(String bearer, String idempotencyKey, String name, List<String> cards) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("cards", cards == null ? null : new ArrayList<>(cards));
        HttpRequest.Builder request = request(DECKS, bearer)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
        if (idempotencyKey != null) {
            request.header("Idempotency-Key", idempotencyKey);
        }
        return send(request);
    }

    /** {@code GET} a una ruta (por ejemplo {@code /api/v1/decks?limit=2}). */
    public Result get(String path, String bearer) {
        return send(request(path, bearer).GET());
    }

    /** POST sin cuerpo, con clave opcional para probar su validación. */
    public Result post(String path, String bearer, String key) {
        HttpRequest.Builder builder = request(path, bearer);
        if (key != null) {
            builder.header("Idempotency-Key", key);
        }
        return send(builder.POST(HttpRequest.BodyPublishers.noBody()));
    }

    /** DELETE autenticado sin cuerpo ni clave de idempotencia. */
    public Result delete(String path, String bearer) {
        return send(request(path, bearer).DELETE());
    }

    /**
     * Ejecuta las solicitudes a la vez: cada una espera en el mismo {@link CountDownLatch} y todas salen
     * juntas cuando están listas. Sin pausas fijas.
     *
     * @return los resultados, en el orden de {@code requests}
     */
    public static List<Result> concurrently(List<Supplier<Result>> requests) {
        ExecutorService pool = Executors.newFixedThreadPool(requests.size());
        CountDownLatch ready = new CountDownLatch(requests.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Result>> futures = new ArrayList<>();
            for (Supplier<Result> request : requests) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    start.await();
                    return request.get();
                }));
            }
            if (!ready.await(CONCURRENCY_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Las solicitudes simultáneas no llegaron a prepararse");
            }
            start.countDown();
            List<Result> results = new ArrayList<>();
            for (Future<Result> future : futures) {
                results.add(future.get(CONCURRENCY_TIMEOUT_SECONDS, TimeUnit.SECONDS));
            }
            return results;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Prueba interrumpida", interrupted);
        } catch (ExecutionException | TimeoutException failure) {
            throw new IllegalStateException("Una solicitud simultánea falló", failure);
        } finally {
            pool.shutdownNow();
        }
    }

    /** Da una copia de cada carta al jugador, en una transacción (la misma vía que la colección inicial). */
    public static void give(UnitOfWork unitOfWork, PlayerCardRepository playerCards, UUID player, List<String> codes) {
        unitOfWork.execute(() -> {
            playerCards.addOneCopyOfEach(player, codes);
            return null;
        });
    }

    /** Cantidad de decks del jugador en la base. */
    public static int deckCount(JdbcTemplate jdbc, UUID player) {
        return jdbc.queryForObject("SELECT count(*) FROM deck WHERE player_id = ?", Integer.class, player);
    }

    private HttpRequest.Builder request(String path, String bearer) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (bearer != null) {
            request.header("Authorization", "Bearer " + bearer);
        }
        return request;
    }

    private Result send(HttpRequest.Builder request) {
        try {
            HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
            JsonNode body = response.body() == null || response.body().isBlank() ? null : json.readTree(response.body());
            return new Result(response.statusCode(), body);
        } catch (IOException failure) {
            throw new IllegalStateException("No se pudo llamar a la API", failure);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Llamada interrumpida", interrupted);
        }
    }
}
