package cr.ac.tec.pokearena.api.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.nimbusds.jose.jwk.RSAKey;

import cr.ac.tec.pokearena.api.web.CorrelationIdFilter;
import cr.ac.tec.pokearena.application.port.AuthenticatedUser;
import cr.ac.tec.pokearena.application.port.IdentityProvider;
import cr.ac.tec.pokearena.application.port.InitialCollectionDelivery;
import cr.ac.tec.pokearena.application.port.TokenVerifier;
import cr.ac.tec.pokearena.application.port.VerifiedIdentity;
import cr.ac.tec.pokearena.application.usecase.DeliverInitialCollectionUseCase;
import cr.ac.tec.pokearena.domain.error.AuthenticationException;
import cr.ac.tec.pokearena.domain.model.CubeMovement;
import cr.ac.tec.pokearena.domain.model.Role;
import cr.ac.tec.pokearena.domain.repository.CubeRepository;
import cr.ac.tec.pokearena.infrastructure.persistence.JdbcCubeRepository;
import cr.ac.tec.pokearena.support.FakeGoogleIdentityServer;
import cr.ac.tec.pokearena.support.PostgresIntegrationTestBase;
import cr.ac.tec.pokearena.support.TestCards;
import cr.ac.tec.pokearena.support.TestPlayers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Propósito: pruebas de punta a punta de la historia 1.1.1 "Inicio de sesión con proveedor" (criterios C.4).
 * Responsabilidades: verificar {@code POST /api/v1/sesiones} con la aplicación completa, PostgreSQL real y
 * un Google simulado (llaves RSA generadas en la prueba y un JWKS servido por un servidor local): éxito,
 * cada error con su status y su type, que un token rechazado o un proveedor caído no dejan ninguna cuenta,
 * la concurrencia de dos primeros ingresos y que un segundo proveedor no cambia la autorización. También
 * la entrega de la colección inicial durante el ingreso (paso 6): entregada, ya entregada, sin cartas
 * suficientes, con falla simulada y con ingresos simultáneos.
 * Referencia: sección 8.1, sección 8.2, Anexos A.4 y A.5, y Anexos C.4 y C.5.
 *
 * <p>Cada prueba parte de un catálogo vacío ({@link TestCards}); las de la colección siembran sus Comunes.
 * La falla de la entrega se simula con un {@link CubeRepository} real envuelto ({@code @Primary}) que lanza
 * una excepción cuando la prueba lo pide: ocurre después de guardar las cartas del jugador.
 *
 * <p>El segundo proveedor ({@code otro}) es un bean de prueba que implementa {@link IdentityProvider}: es
 * todo lo que hace falta para agregarlo. La prueba lo usa para iniciar sesión y luego entrar a una
 * operación de administrador con las mismas credenciales propias que emite Google.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.admin.identities=google:" + SessionControllerIT.GOOGLE_ADMIN_SUBJECT
                + ",otro:" + SessionControllerIT.OTHER_ADMIN_SUBJECT)
// Explícito: las clases @Nested no detectan solas la configuración anidada.
@Import({ SessionControllerIT.SecondProviderConfiguration.class, SessionControllerIT.FaultyCubesConfiguration.class })
@ExtendWith(OutputCaptureExtension.class)
@DisplayName("Historia 1.1.1: inicio de sesión con proveedor (API completa)")
class SessionControllerIT extends PostgresIntegrationTestBase {

    static final String CLIENT_ID = "pokearena-pruebas.apps.googleusercontent.com";
    static final String GOOGLE_ADMIN_SUBJECT = "admin-de-prueba-it";
    static final String OTHER_ADMIN_SUBJECT = "admin-otro-it";

    private static final String PATH = "/api/v1/sesiones";
    private static final String ADMIN_ROUTE = "/api/v1/cartas?limit=1";
    private static final long ACCESS_TOKEN_SECONDS = 15 * 60;
    private static final int SIMULTANEOUS_LOGINS = 10;

    private static final FakeGoogleIdentityServer GOOGLE = FakeGoogleIdentityServer.start();
    private static final RSAKey GOOGLE_KEY = FakeGoogleIdentityServer.newKey();

    static {
        GOOGLE.publish(GOOGLE_KEY);
    }

    @DynamicPropertySource
    static void identityProperties(DynamicPropertyRegistry registry) {
        registry.add("app.identity.google.client-id", () -> CLIENT_ID);
        registry.add("app.identity.google.jwks-uri", () -> GOOGLE.jwksUri().toString());
        // La prueba del proveedor caído fuerza una recarga con un kid nuevo justo después de otros ingresos.
        // El límite de recargas se prueba con un reloj controlado en GoogleIdentityProviderTest.
        registry.add("app.identity.google.jwks-min-reload-interval", () -> "PT0S");
    }

    @AfterAll
    static void stopGoogle() {
        GOOGLE.close();
    }

    /** Segundo proveedor de prueba: basta con declararlo como bean para que el registro lo ofrezca. */
    @TestConfiguration
    static class SecondProviderConfiguration {

        @Bean
        IdentityProvider otherProvider() {
            return new IdentityProvider() {
                @Override
                public String name() {
                    return "otro";
                }

                @Override
                public VerifiedIdentity verify(String idToken) {
                    if (!idToken.startsWith("otro-valido:")) {
                        throw AuthenticationException.invalidIdentityToken("Token inválido.");
                    }
                    return new VerifiedIdentity("otro", idToken.substring("otro-valido:".length()), null, null);
                }
            };
        }
    }

    private final HttpClient http = HttpClient.newHttpClient();
    private final JsonMapper json = JsonMapper.builder().build();
    private final List<String[]> identities = new ArrayList<>();

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TokenVerifier tokenVerifier;

    @Autowired
    private FaultyCubeRepository faultyCubes;

    @Autowired
    private ApplicationContext context;

    private TestCards catalog;

    /** Cada prueba parte de un catálogo vacío: así el estado de la colección inicial es conocido. */
    @BeforeEach
    void emptyCatalog() {
        catalog = new TestCards(jdbc);
        catalog.reset();
    }

    @AfterEach
    void deleteAccounts() {
        for (String[] identity : identities) {
            List<UUID> ids = jdbc.queryForList(
                    "SELECT player_id FROM player_identity WHERE provider = ? AND provider_subject = ?", UUID.class,
                    identity[0], identity[1]);
            TestPlayers.delete(jdbc, ids);
        }
        GOOGLE.setDown(false);
        faultyCubes.failing.set(false);
        catalog.reset();
    }

    /**
     * Repositorio real de cubos que puede simular una falla a mitad de la entrega: después de guardar las
     * cartas del jugador y antes de los cubos y la marca. {@code @Primary}: lo recibe el caso de uso real.
     */
    static class FaultyCubeRepository implements CubeRepository {

        final AtomicBoolean failing = new AtomicBoolean();
        private final CubeRepository real;

        FaultyCubeRepository(CubeRepository real) {
            this.real = real;
        }

        @Override
        public void apply(CubeMovement movement) {
            if (failing.get()) {
                throw new IllegalStateException("falla simulada al mover los cubos");
            }
            real.apply(movement);
        }
    }

    @TestConfiguration
    static class FaultyCubesConfiguration {

        @Bean
        @Primary
        FaultyCubeRepository faultyCubeRepository(JdbcCubeRepository real) {
            return new FaultyCubeRepository(real);
        }
    }

    /** Un sub nuevo de Google, que se borra al terminar la prueba. */
    private String newSubject() {
        return track("google", "it-" + UUID.randomUUID());
    }

    private String track(String provider, String subject) {
        identities.add(new String[] { provider, subject });
        return subject;
    }

    private FakeGoogleIdentityServer.TokenBuilder googleToken(String subject) {
        return FakeGoogleIdentityServer.token(GOOGLE_KEY, CLIENT_ID, Instant.now()).subject(subject);
    }

    private int accountsOf(String provider, String subject) {
        return jdbc.queryForObject("SELECT count(*) FROM player_identity WHERE provider = ? AND provider_subject = ?",
                Integer.class, provider, subject);
    }

    @Test
    @DisplayName("11: la configuración pública entrega a la interfaz el mismo client id que verifica la audiencia")
    void publicConfigurationExposesClientId() throws Exception {
        HttpResponse<String> response = http.send(HttpRequest.newBuilder(uri("/api/v1/configuracion")).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(json.readTree(response.body()).get("googleClientId").asString()).isEqualTo(CLIENT_ID);
    }

    @Nested
    @DisplayName("Éxito")
    class Success {

        @Test
        @DisplayName("C.4 éxito: con un token válido de Google para PokéArena entrega credenciales propias con expiración corta")
        void validTokenIssuesOwnCredentials() throws Exception {
            String subject = newSubject();

            Result result = login("google", googleToken(subject).email("ana@example.com").name("Ana").sign());

            assertThat(result.status()).isEqualTo(200);
            JsonNode body = result.body();
            AuthenticatedUser user = tokenVerifier.verify(body.get("accessToken").asString());
            assertThat(user.playerId().toString()).isEqualTo(body.get("player").get("id").asString());
            assertThat(body.get("tokenType").asString()).isEqualTo("Bearer");
            assertThat(body.get("expiresIn").asLong()).isEqualTo(ACCESS_TOKEN_SECONDS);
            assertThat(body.get("player").get("displayName").asString()).isEqualTo("Ana");
            assertThat(body.get("player").get("email").asString()).isEqualTo("ana@example.com");
        }

        @Test
        @DisplayName("C.4 éxito: el primer ingreso crea la cuenta con rol de jugador y Elo inicial 0; los cubos llegan con la colección")
        void firstLoginCreatesPlayerAccount() throws Exception {
            String subject = newSubject();

            JsonNode body = login("google", googleToken(subject).sign()).body();

            assertThat(body.get("firstLogin").asBoolean()).isTrue();
            assertThat(body.get("player").get("role").asString()).isEqualTo("PLAYER");
            Map<String, Object> row = jdbc.queryForMap("""
                    SELECT p.role, p.elo, p.cube_balance, p.initial_collection_status
                    FROM player p JOIN player_identity i ON i.player_id = p.id
                    WHERE i.provider = 'google' AND i.provider_subject = ?
                    """, subject);
            assertThat(row.get("role")).isEqualTo("PLAYER");
            assertThat(((Number) row.get("elo")).intValue()).isZero();
            // Los cubos llegan con la colección inicial (3.1.1). Aquí el catálogo está vacío: todavía no hay.
            assertThat(((Number) row.get("cube_balance")).intValue()).isZero();
            assertThat(row.get("initial_collection_status")).isEqualTo("PENDING");
            assertThat(body.get("initialCollection").get("status").asString()).isEqualTo("PENDING_NO_CARDS");
        }

        @Test
        @DisplayName("C.4 éxito: en los ingresos posteriores se reutiliza la cuenta; nunca se crea una segunda")
        void laterLoginsReuseAccount() throws Exception {
            String subject = newSubject();

            JsonNode first = login("google", googleToken(subject).sign()).body();
            JsonNode second = login("google", googleToken(subject).sign()).body();

            assertThat(second.get("player").get("id").asString()).isEqualTo(first.get("player").get("id").asString());
            assertThat(second.get("firstLogin").asBoolean()).isFalse();
            assertThat(accountsOf("google", subject)).isEqualTo(1);
        }

        @Test
        @DisplayName("C.4 éxito: las solicitudes posteriores se autorizan con las credenciales de PokéArena, no con el token del proveedor")
        void laterRequestsUseOwnCredentials() throws Exception {
            String subject = newSubject();
            String googleToken = googleToken(subject).sign();
            String ownToken = login("google", googleToken).body().get("accessToken").asString();

            // Con las credenciales propias el jugador está autenticado: 403 por no ser administrador, no 401.
            Result withOwn = get(ADMIN_ROUTE, ownToken);
            // El token de Google nunca autoriza la API.
            Result withGoogle = get(ADMIN_ROUTE, googleToken);

            assertThat(withOwn.status()).isEqualTo(403);
            assertThat(withOwn.body().get("type").asString()).isEqualTo("FORBIDDEN");
            assertThat(withGoogle.status()).isEqualTo(401);
            assertThat(withGoogle.body().get("type").asString()).isEqualTo("NOT_AUTHENTICATED");
        }

        @Test
        @DisplayName("5.2: una identidad de app.admin.identities recibe ADMIN y sus credenciales entran a las operaciones de administrador")
        void configuredAdmin() throws Exception {
            track("google", GOOGLE_ADMIN_SUBJECT);

            JsonNode body = login("google", googleToken(GOOGLE_ADMIN_SUBJECT).sign()).body();

            assertThat(body.get("player").get("role").asString()).isEqualTo("ADMIN");
            assertThat(get(ADMIN_ROUTE, body.get("accessToken").asString()).status()).isEqualTo(200);
        }

        @Test
        @DisplayName("Sin nombre ni correo en el token, la respuesta usa el nombre de respaldo y la base guarda nulos")
        void fallbackName() throws Exception {
            String subject = newSubject();

            JsonNode player = login("google", googleToken(subject).withoutProfile().sign()).body().get("player");

            String id = player.get("id").asString();
            assertThat(player.get("displayName").asString()).isEqualTo("Jugador " + id.substring(id.length() - 6));
            assertThat(player.get("email").isNull()).isTrue();
            assertThat(jdbc.queryForObject("SELECT display_name FROM player WHERE id = ?::uuid", String.class, id))
                    .isNull();
        }
    }

    @Nested
    @DisplayName("Errores")
    class Errors {

        @Test
        @DisplayName("C.4 error: un token que no fue emitido por el proveedor (otro emisor) es rechazado y no se crea ninguna cuenta")
        void otherIssuer() throws Exception {
            String subject = newSubject();

            assertRejectedWithoutAccount(subject, googleToken(subject).issuer("https://emisor-falso.example.com").sign());
        }

        @Test
        @DisplayName("C.4 error: un token expirado es rechazado y no se crea ninguna cuenta")
        void expired() throws Exception {
            String subject = newSubject();

            assertRejectedWithoutAccount(subject, googleToken(subject).expiresAt(Instant.now().minusSeconds(60)).sign());
        }

        @Test
        @DisplayName("C.4 error: un token con la firma inválida es rechazado y no se crea ninguna cuenta")
        void invalidSignature() throws Exception {
            String subject = newSubject();

            assertRejectedWithoutAccount(subject,
                    googleToken(subject).signedWithForeignKey(FakeGoogleIdentityServer.newKey()).sign());
        }

        @Test
        @DisplayName("C.4 error: un token emitido para otra aplicación es rechazado y no se crea ninguna cuenta")
        void otherAudience() throws Exception {
            String subject = newSubject();

            assertRejectedWithoutAccount(subject,
                    googleToken(subject).audience("otra-aplicacion.apps.googleusercontent.com").sign());
        }

        @Test
        @DisplayName("C.4 error: si el proveedor no está disponible responde 503 descriptivo y no deja ninguna cuenta")
        void providerUnavailable() throws Exception {
            String subject = newSubject();
            // Una llave nueva obliga a pedir el JWKS (las anteriores ya están guardadas en memoria).
            RSAKey rotated = FakeGoogleIdentityServer.newKey();
            GOOGLE.setDown(true);

            Result result = login("google", FakeGoogleIdentityServer.token(rotated, CLIENT_ID, Instant.now())
                    .subject(subject).sign());

            assertThat(result.status()).isEqualTo(503);
            assertThat(result.body().get("type").asString()).isEqualTo("IDENTITY_PROVIDER_UNAVAILABLE");
            assertThat(result.body().get("detail").asString()).contains("no responde");
            assertThat(accountsOf("google", subject)).isZero();
        }

        @Test
        @DisplayName("8.1 paso 2: un proveedor no registrado es 400 UNSUPPORTED_PROVIDER con los proveedores permitidos; dev no se acepta")
        void unsupportedProvider() throws Exception {
            Result facebook = login("facebook", "cualquier-token");
            Result dev = login("dev", "dev-admin");

            assertThat(facebook.status()).isEqualTo(400);
            assertThat(facebook.body().get("type").asString()).isEqualTo("UNSUPPORTED_PROVIDER");
            assertThat(facebook.body().get("field").asString()).isEqualTo("provider");
            assertThat(facebook.body().get("allowedValues").toString()).contains("google", "otro");
            assertThat(dev.body().get("type").asString()).isEqualTo("UNSUPPORTED_PROVIDER");
        }

        @Test
        @DisplayName("8.1: un cuerpo sin provider o sin idToken es 400 MISSING_FIELD con el campo")
        void missingFields() throws Exception {
            Result noProvider = post("{\"idToken\":\"x\"}");
            Result noToken = post("{\"provider\":\"google\"}");

            assertThat(noProvider.status()).isEqualTo(400);
            assertThat(noProvider.body().get("type").asString()).isEqualTo("MISSING_FIELD");
            assertThat(noProvider.body().get("field").asString()).isEqualTo("provider");
            assertThat(noToken.body().get("type").asString()).isEqualTo("MISSING_FIELD");
            assertThat(noToken.body().get("field").asString()).isEqualTo("idToken");
        }

        private void assertRejectedWithoutAccount(String subject, String token) throws Exception {
            Result result = login("google", token);

            assertThat(result.status()).isEqualTo(401);
            assertThat(result.body().get("type").asString()).isEqualTo("INVALID_IDENTITY_TOKEN");
            assertThat(result.body().get("correlationId").asString()).isNotBlank();
            assertThat(accountsOf("google", subject)).isZero();
        }
    }

    @Nested
    @DisplayName("Alternos")
    class Alternatives {

        @Test
        @DisplayName("C.4 alterno: dos inicios de sesión simultáneos de una misma identidad nueva crean exactamente una cuenta")
        void simultaneousFirstLogins() throws Exception {
            String subject = newSubject();
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Result>> results = new ArrayList<>();
            ExecutorService threads = Executors.newFixedThreadPool(SIMULTANEOUS_LOGINS);
            try {
                for (int i = 0; i < SIMULTANEOUS_LOGINS; i++) {
                    String token = googleToken(subject).sign();
                    Callable<Result> attempt = () -> {
                        start.await();
                        return login("google", token);
                    };
                    results.add(threads.submit(attempt));
                }
                start.countDown();

                List<String> playerIds = new ArrayList<>();
                int firstLogins = 0;
                for (Future<Result> future : results) {
                    Result result = future.get();
                    assertThat(result.status()).isEqualTo(200);
                    playerIds.add(result.body().get("player").get("id").asString());
                    firstLogins += result.body().get("firstLogin").asBoolean() ? 1 : 0;
                }

                assertThat(playerIds).containsOnly(playerIds.getFirst());
                assertThat(firstLogins).isEqualTo(1);
                assertThat(accountsOf("google", subject)).isEqualTo(1);
            } finally {
                threads.shutdownNow();
            }
        }

        @Test
        @DisplayName("C.4 alterno: si el jugador cambia su correo en el proveedor, conserva su cuenta (se identifica por sub)")
        void emailChangeKeepsAccount() throws Exception {
            String subject = newSubject();

            JsonNode before = login("google", googleToken(subject).email("ana@example.com").sign()).body();
            JsonNode after = login("google", googleToken(subject).email("ana.nueva@example.com").sign()).body();

            assertThat(after.get("player").get("id").asString()).isEqualTo(before.get("player").get("id").asString());
            assertThat(after.get("firstLogin").asBoolean()).isFalse();
            assertThat(accountsOf("google", subject)).isEqualTo(1);
        }

        @Test
        @DisplayName("C.4 alterno: un segundo proveedor se agrega sin cambiar cómo se autorizan las solicitudes a la API")
        void secondProviderUsesSameAuthorization() throws Exception {
            track("otro", OTHER_ADMIN_SUBJECT);
            String playerSubject = track("otro", "it-" + UUID.randomUUID());

            JsonNode admin = login("otro", "otro-valido:" + OTHER_ADMIN_SUBJECT).body();
            JsonNode player = login("otro", "otro-valido:" + playerSubject).body();

            // Las credenciales del segundo proveedor son las mismas credenciales propias: el mismo
            // interceptor las acepta, con las mismas reglas de rol (200 para ADMIN, 403 para PLAYER).
            assertThat(tokenVerifier.verify(admin.get("accessToken").asString()).role()).isEqualTo(Role.ADMIN);
            assertThat(get(ADMIN_ROUTE, admin.get("accessToken").asString()).status()).isEqualTo(200);
            assertThat(get(ADMIN_ROUTE, player.get("accessToken").asString()).status()).isEqualTo(403);
            assertThat(accountsOf("otro", playerSubject)).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Colección inicial al iniciar sesión (8.1 paso 6 y 8.2)")
    class InitialCollectionOnLogin {

        private static final int AVAILABLE_COMMONS = 20;
        private static final int INITIAL_CARDS = 15;
        private static final long INITIAL_CUBES = 1000;

        @Test
        @DisplayName("El único bean del puerto InitialCollectionDelivery es el caso de uso real de la colección inicial")
        void onlyOneDeliveryBean() {
            assertThat(context.getBeansOfType(InitialCollectionDelivery.class).values()).singleElement()
                    .isInstanceOf(DeliverInitialCollectionUseCase.class);
        }

        @Test
        @DisplayName("C.5 éxito: el primer ingreso con suficientes Comunes entrega DELIVERED, 15 cartas distintas, 1.000 cubos e INITIAL_GRANT como primer movimiento")
        void firstLoginDelivers() throws Exception {
            catalog.commons(AVAILABLE_COMMONS);
            String subject = newSubject();

            JsonNode body = login("google", googleToken(subject).sign()).body();

            JsonNode collection = body.get("initialCollection");
            assertThat(collection.get("status").asString()).isEqualTo("DELIVERED");
            assertThat(collection.get("message").isNull()).isTrue();
            UUID player = UUID.fromString(body.get("player").get("id").asString());
            assertThat(jdbc.queryForObject("""
                    SELECT count(DISTINCT pc.card_code) FROM player_card pc JOIN card c ON c.code = pc.card_code
                    WHERE pc.player_id = ? AND c.rarity_code = 'COMMON' AND pc.copies = 1
                    """, Integer.class, player)).isEqualTo(INITIAL_CARDS);
            assertThat(cardCount(player)).isEqualTo(INITIAL_CARDS);
            assertThat(balance(player)).isEqualTo(INITIAL_CUBES);
            Map<String, Object> first = jdbc.queryForMap(
                    "SELECT amount, reason FROM cube_movement WHERE player_id = ? ORDER BY id LIMIT 1", player);
            assertThat(first.get("reason")).isEqualTo("INITIAL_GRANT");
            assertThat(((Number) first.get("amount")).longValue()).isEqualTo(INITIAL_CUBES);
        }

        @Test
        @DisplayName("C.5 alterno: el segundo ingreso responde ALREADY_DELIVERED, sin mensaje, y no entrega nada nuevo")
        void secondLoginAlreadyDelivered() throws Exception {
            catalog.commons(AVAILABLE_COMMONS);
            String subject = newSubject();
            UUID player = UUID.fromString(login("google", googleToken(subject).sign()).body()
                    .get("player").get("id").asString());
            List<String> before = cardsOf(player);

            JsonNode collection = login("google", googleToken(subject).sign()).body().get("initialCollection");

            assertThat(collection.get("status").asString()).isEqualTo("ALREADY_DELIVERED");
            assertThat(collection.get("message").isNull()).isTrue();
            assertThat(cardsOf(player)).isEqualTo(before);
            assertThat(balance(player)).isEqualTo(INITIAL_CUBES);
            assertThat(movementCount(player)).isEqualTo(1);
        }

        @Test
        @DisplayName("C.5 error: con menos de 15 Comunes el jugador ingresa con PENDING_NO_CARDS, sin cartas ni cubos; con las cartas que faltan, el siguiente ingreso la entrega")
        void notEnoughCommonsThenDelivered() throws Exception {
            catalog.commons(INITIAL_CARDS - 1);
            String subject = newSubject();

            Result first = login("google", googleToken(subject).sign());

            assertThat(first.status()).isEqualTo(200);
            assertThat(first.body().get("accessToken").asString()).isNotBlank();
            JsonNode collection = first.body().get("initialCollection");
            assertThat(collection.get("status").asString()).isEqualTo("PENDING_NO_CARDS");
            assertThat(collection.get("message").asString())
                    .contains("todavía no hay cartas disponibles, tu colección se entregará en un ingreso posterior");
            UUID player = UUID.fromString(first.body().get("player").get("id").asString());
            assertThat(cardCount(player)).isZero();
            assertThat(balance(player)).isZero();
            assertThat(movementCount(player)).isZero();

            catalog.commons(1);
            JsonNode second = login("google", googleToken(subject).sign()).body();

            assertThat(second.get("initialCollection").get("status").asString()).isEqualTo("DELIVERED");
            assertThat(cardCount(player)).isEqualTo(INITIAL_CARDS);
            assertThat(balance(player)).isEqualTo(INITIAL_CUBES);
        }

        @Test
        @DisplayName("C.5 error: si la entrega falla, el ingreso funciona con PENDING_ERROR y un aviso con el correlationId, sin nada parcial; el siguiente ingreso la completa")
        void failedDeliveryDoesNotBlockLogin(CapturedOutput output) throws Exception {
            catalog.commons(AVAILABLE_COMMONS);
            String subject = newSubject();
            String correlationId = "it-coleccion-" + UUID.randomUUID();
            faultyCubes.failing.set(true);

            Result first = loginWithCorrelationId("google", googleToken(subject).sign(), correlationId);

            assertThat(first.status()).isEqualTo(200);
            assertThat(first.body().get("accessToken").asString()).isNotBlank();
            JsonNode collection = first.body().get("initialCollection");
            assertThat(collection.get("status").asString()).isEqualTo("PENDING_ERROR");
            assertThat(collection.get("message").asString())
                    .isEqualTo("No pudimos entregar tu colección, se reintentará en tu próximo ingreso.");
            UUID player = UUID.fromString(first.body().get("player").get("id").asString());
            // La cuenta existe (otra transacción), pero la entrega se revirtió completa.
            assertThat(accountsOf("google", subject)).isEqualTo(1);
            assertThat(cardCount(player)).isZero();
            assertThat(balance(player)).isZero();
            assertThat(movementCount(player)).isZero();
            assertThat(jdbc.queryForObject("SELECT initial_collection_status FROM player WHERE id = ?", String.class,
                    player)).isEqualTo("PENDING");
            // El aviso queda en el log con el correlationId de la solicitud.
            assertThat(output.getAll().lines()
                    .filter(line -> line.contains("No se pudo entregar la colección inicial")))
                    .anySatisfy(line -> assertThat(line).contains(correlationId));

            faultyCubes.failing.set(false);
            JsonNode second = login("google", googleToken(subject).sign()).body();

            assertThat(second.get("initialCollection").get("status").asString()).isEqualTo("DELIVERED");
            assertThat(cardCount(player)).isEqualTo(INITIAL_CARDS);
            assertThat(balance(player)).isEqualTo(INITIAL_CUBES);
        }

        @Test
        @DisplayName("C.4 y C.5 alternos: 10 primeros ingresos simultáneos de una identidad nueva crean una cuenta y entregan la colección una sola vez")
        void simultaneousFirstLoginsDeliverOnce() throws Exception {
            catalog.commons(AVAILABLE_COMMONS);
            String subject = newSubject();
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Result>> results = new ArrayList<>();
            ExecutorService threads = Executors.newFixedThreadPool(SIMULTANEOUS_LOGINS);
            try {
                for (int i = 0; i < SIMULTANEOUS_LOGINS; i++) {
                    String token = googleToken(subject).sign();
                    Callable<Result> attempt = () -> {
                        start.await();
                        return login("google", token);
                    };
                    results.add(threads.submit(attempt));
                }
                start.countDown();

                List<String> statuses = new ArrayList<>();
                List<String> playerIds = new ArrayList<>();
                for (Future<Result> future : results) {
                    Result result = future.get();
                    assertThat(result.status()).isEqualTo(200);
                    statuses.add(result.body().get("initialCollection").get("status").asString());
                    playerIds.add(result.body().get("player").get("id").asString());
                }

                assertThat(accountsOf("google", subject)).isEqualTo(1);
                assertThat(playerIds).containsOnly(playerIds.getFirst());
                assertThat(statuses).filteredOn("DELIVERED"::equals).hasSize(1);
                assertThat(statuses).filteredOn("ALREADY_DELIVERED"::equals).hasSize(SIMULTANEOUS_LOGINS - 1);
                UUID player = UUID.fromString(playerIds.getFirst());
                assertThat(cardCount(player)).isEqualTo(INITIAL_CARDS);
                assertThat(balance(player)).isEqualTo(INITIAL_CUBES);
                assertThat(movementCount(player)).isEqualTo(1);
            } finally {
                threads.shutdownNow();
            }
        }

        private List<String> cardsOf(UUID player) {
            return jdbc.queryForList("SELECT card_code FROM player_card WHERE player_id = ? ORDER BY card_code",
                    String.class, player);
        }

        private int cardCount(UUID player) {
            return jdbc.queryForObject("SELECT count(*) FROM player_card WHERE player_id = ?", Integer.class, player);
        }

        private int movementCount(UUID player) {
            return jdbc.queryForObject("SELECT count(*) FROM cube_movement WHERE player_id = ?", Integer.class,
                    player);
        }

        private long balance(UUID player) {
            return jdbc.queryForObject("SELECT cube_balance FROM player WHERE id = ?", Long.class, player);
        }
    }

    // =========================================================================================

    private Result login(String provider, String idToken) throws Exception {
        return post(json.writeValueAsString(Map.of("provider", provider, "idToken", idToken)));
    }

    private Result post(String body) throws Exception {
        HttpResponse<String> response = http.send(HttpRequest.newBuilder(uri(PATH))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
        return new Result(response.statusCode(), json.readTree(response.body()));
    }

    private Result loginWithCorrelationId(String provider, String idToken, String correlationId) throws Exception {
        HttpResponse<String> response = http.send(HttpRequest.newBuilder(uri(PATH))
                .header("Content-Type", "application/json")
                .header(CorrelationIdFilter.HEADER, correlationId)
                .POST(HttpRequest.BodyPublishers.ofString(
                        json.writeValueAsString(Map.of("provider", provider, "idToken", idToken))))
                .build(), HttpResponse.BodyHandlers.ofString());
        return new Result(response.statusCode(), json.readTree(response.body()));
    }

    private Result get(String path, String bearer) throws Exception {
        HttpResponse<String> response = http.send(HttpRequest.newBuilder(uri(path))
                .header("Authorization", "Bearer " + bearer).GET().build(), HttpResponse.BodyHandlers.ofString());
        return new Result(response.statusCode(), json.readTree(response.body()));
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private record Result(int status, JsonNode body) {
    }
}
