package cr.ac.tec.pokearena.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import cr.ac.tec.pokearena.application.dto.LoginCommand;
import cr.ac.tec.pokearena.application.dto.LoginResult;
import cr.ac.tec.pokearena.application.port.AuthenticatedUser;
import cr.ac.tec.pokearena.application.port.IdentityProvider;
import cr.ac.tec.pokearena.application.port.IdentityProviderRegistry;
import cr.ac.tec.pokearena.application.port.InitialCollectionDelivery;
import cr.ac.tec.pokearena.application.port.InitialCollectionStatus;
import cr.ac.tec.pokearena.application.port.TokenIssuer;
import cr.ac.tec.pokearena.application.port.UnitOfWork;
import cr.ac.tec.pokearena.application.port.VerifiedIdentity;
import cr.ac.tec.pokearena.domain.error.AuthenticationException;
import cr.ac.tec.pokearena.domain.error.DomainException;
import cr.ac.tec.pokearena.domain.error.ErrorType;
import cr.ac.tec.pokearena.domain.error.IdentityProviderException;
import cr.ac.tec.pokearena.domain.model.Player;
import cr.ac.tec.pokearena.domain.model.ProviderIdentity;
import cr.ac.tec.pokearena.domain.model.Role;
import cr.ac.tec.pokearena.domain.rule.AdminIdentities;
import cr.ac.tec.pokearena.support.InMemoryPlayerRepository;

/**
 * Propósito: pruebas unitarias de {@link LoginWithProviderUseCase} con los puertos en memoria (historia 1.1.1).
 * Responsabilidades: comprobar el orden del diagrama A.4 (campos, registro, verificación ANTES de abrir la
 * transacción, cuenta, colección, credenciales), {@code firstLogin}, el nombre de respaldo, que la entrega
 * de la colección no bloquea el ingreso y que un proveedor nuevo funciona sin cambiar el caso de uso.
 * Sin Spring ni base de datos: proveedores falsos, un repositorio en memoria y una unidad de trabajo que
 * cuenta sus transacciones.
 * Referencia: sección 8.1 y Anexo A.4.
 */
@DisplayName("Caso de uso Inicio de sesión con proveedor (puertos en memoria)")
class LoginWithProviderUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-11-07T12:00:00Z");
    private static final Duration TTL = Duration.ofMinutes(15);
    private static final String VALID = "valido";

    private final InMemoryPlayerRepository players = new InMemoryPlayerRepository();
    private final CountingUnitOfWork unitOfWork = new CountingUnitOfWork();
    private final List<AuthenticatedUser> issued = new ArrayList<>();
    private final TokenIssuer tokenIssuer = user -> {
        issued.add(user);
        return new TokenIssuer.IssuedToken("token-" + user.playerId(), TTL, NOW.plus(TTL));
    };

    // Por defecto, una entrega que encuentra el catálogo sin Comunes suficientes.
    private InitialCollectionDelivery delivery = playerId -> InitialCollectionStatus.PENDING_NO_CARDS;

    /**
     * Proveedor falso: acepta los tokens {@code valido:<sub>:<correo>:<nombre>} (correo y nombre opcionales),
     * rechaza el resto como INVALID_IDENTITY_TOKEN y, con {@code down}, responde como un proveedor caído.
     */
    private static class FakeProvider implements IdentityProvider {

        private final String name;
        private boolean down;
        private int verifications;

        FakeProvider(String name) {
            this.name = name;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public VerifiedIdentity verify(String idToken) {
            verifications++;
            if (down) {
                throw IdentityProviderException.unavailable("El proveedor no responde.");
            }
            String[] parts = idToken.split(":", -1);
            if (!parts[0].equals(VALID) || parts.length < 2) {
                throw AuthenticationException.invalidIdentityToken("Token inválido.");
            }
            return new VerifiedIdentity(name, parts[1], part(parts, 2), part(parts, 3));
        }

        private static String part(String[] parts, int index) {
            return parts.length > index ? parts[index] : null;
        }
    }

    private final FakeProvider google = new FakeProvider("google");

    private LoginWithProviderUseCase useCase(Set<ProviderIdentity> admins, IdentityProvider... providers) {
        PlayerAccountService accounts = new PlayerAccountService(unitOfWork, players, new AdminIdentities(admins),
                () -> NOW);
        return new LoginWithProviderUseCase(new IdentityProviderRegistry(List.of(providers)), accounts,
                playerId -> delivery.deliver(playerId), tokenIssuer);
    }

    private LoginResult login(String provider, String token) {
        return useCase(Set.of(), google).execute(new LoginCommand(provider, token));
    }

    private static void assertError(Runnable action, ErrorType type, String field) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(DomainException.class, error -> {
            assertThat(error.type()).isEqualTo(type);
            if (field != null) {
                assertThat(error.field()).contains(field);
            }
        });
    }

    @Nested
    @DisplayName("Éxito")
    class Success {

        @Test
        @DisplayName("C.4: el primer ingreso crea la cuenta con rol de jugador y entrega credenciales propias")
        void firstLoginCreatesPlayer() {
            LoginResult result = login("google", "valido:sub-1:ana@example.com:Ana");

            assertThat(result.firstLogin()).isTrue();
            assertThat(result.session().player().role()).isEqualTo(Role.PLAYER);
            assertThat(result.session().player().visibleName()).isEqualTo("Ana");
            assertThat(result.session().token().validity()).isEqualTo(TTL);
            assertThat(issued).containsExactly(
                    new AuthenticatedUser(result.session().player().id(), Role.PLAYER));
        }

        @Test
        @DisplayName("C.4: un ingreso posterior reutiliza la cuenta y ya no es el primer ingreso")
        void laterLoginReusesAccount() {
            LoginResult first = login("google", "valido:sub-1");
            LoginResult second = login("google", "valido:sub-1");

            assertThat(second.session().player().id()).isEqualTo(first.session().player().id());
            assertThat(second.firstLogin()).isFalse();
            assertThat(players.created()).hasSize(1);
        }

        @Test
        @DisplayName("C.4: un cambio de correo en el proveedor conserva la cuenta: la identidad es (proveedor, sub)")
        void emailChangeKeepsAccount() {
            LoginResult before = login("google", "valido:sub-1:ana@example.com:Ana");
            LoginResult after = login("google", "valido:sub-1:ana.nueva@example.com:Ana");

            assertThat(after.session().player().id()).isEqualTo(before.session().player().id());
            assertThat(players.created()).hasSize(1);
        }

        @Test
        @DisplayName("5.2: una identidad configurada en app.admin.identities recibe el rol ADMIN")
        void configuredAdmin() {
            LoginResult result = useCase(Set.of(new ProviderIdentity("google", "sub-admin")), google)
                    .execute(new LoginCommand("google", "valido:sub-admin"));

            assertThat(result.session().player().role()).isEqualTo(Role.ADMIN);
            assertThat(issued.getFirst().role()).isEqualTo(Role.ADMIN);
        }

        @Test
        @DisplayName("Sin nombre del proveedor se guarda nulo y se muestra 'Jugador' más un sufijo del id")
        void fallbackName() {
            Player player = login("google", "valido:sub-sin-nombre").session().player();

            assertThat(player.displayName()).isNull();
            assertThat(player.email()).isNull();
            String id = player.id().toString();
            assertThat(player.visibleName()).isEqualTo("Jugador " + id.substring(id.length() - 6));
        }

        @Test
        @DisplayName("Un nombre o correo en blanco se trata como ausente")
        void blankProfileIsAbsent() {
            Player player = login("google", "valido:sub-blanco: : ").session().player();

            assertThat(player.displayName()).isNull();
            assertThat(player.email()).isNull();
        }
    }

    @Nested
    @DisplayName("Errores: nada se crea antes de verificar")
    class Errors {

        @Test
        @DisplayName("Sin proveedor o sin token: MISSING_FIELD, sin consultar al proveedor")
        void missingFields() {
            assertError(() -> login(null, "valido:sub-1"), ErrorType.MISSING_FIELD, "provider");
            assertError(() -> login(" ", "valido:sub-1"), ErrorType.MISSING_FIELD, "provider");
            assertError(() -> login("google", null), ErrorType.MISSING_FIELD, "idToken");
            assertError(() -> login("google", ""), ErrorType.MISSING_FIELD, "idToken");

            assertThat(google.verifications).isZero();
            assertThat(unitOfWork.transactions).isZero();
        }

        @Test
        @DisplayName("8.1 paso 2: un proveedor no registrado es UNSUPPORTED_PROVIDER con los proveedores permitidos")
        void unsupportedProvider() {
            assertThatThrownBy(() -> login("facebook", "valido:sub-1"))
                    .isInstanceOfSatisfying(DomainException.class, error -> {
                        assertThat(error.type()).isEqualTo(ErrorType.UNSUPPORTED_PROVIDER);
                        assertThat(error.allowedValues()).containsExactly("google");
                    });
            assertThat(unitOfWork.transactions).isZero();
        }

        @Test
        @DisplayName("C.4: un token rechazado por el proveedor no abre la transacción ni emite credenciales")
        void invalidTokenCreatesNothing() {
            assertError(() -> login("google", "falso:sub-1"), ErrorType.INVALID_IDENTITY_TOKEN, null);

            assertThat(unitOfWork.transactions).isZero();
            assertThat(players.created()).isEmpty();
            assertThat(issued).isEmpty();
        }

        @Test
        @DisplayName("C.4: con el proveedor caído responde IDENTITY_PROVIDER_UNAVAILABLE y no deja ninguna cuenta")
        void providerDownCreatesNothing() {
            google.down = true;

            assertError(() -> login("google", "valido:sub-1"), ErrorType.IDENTITY_PROVIDER_UNAVAILABLE, null);

            assertThat(unitOfWork.transactions).isZero();
            assertThat(players.created()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Colección inicial (paso 6)")
    class InitialCollection {

        @Test
        @DisplayName("Informa el estado que devuelve la entrega; PENDING_NO_CARDS lleva su mensaje para el jugador")
        void reportsDeliveryStatus() {
            LoginResult result = login("google", "valido:sub-1");

            assertThat(result.initialCollection()).isEqualTo(InitialCollectionStatus.PENDING_NO_CARDS);
            assertThat(result.initialCollection().message())
                    .contains("todavía no hay cartas disponibles, tu colección se entregará en un ingreso posterior");
        }

        @Test
        @DisplayName("8.1 paso 6: si la entrega falla, el ingreso no se bloquea y el estado es PENDING_ERROR con su mensaje")
        void deliveryFailureDoesNotBlockLogin() {
            delivery = playerId -> {
                throw new IllegalStateException("falla simulada");
            };

            LoginResult result = login("google", "valido:sub-1");

            assertThat(result.initialCollection()).isEqualTo(InitialCollectionStatus.PENDING_ERROR);
            assertThat(result.initialCollection().message())
                    .isEqualTo("No pudimos entregar tu colección, se reintentará en tu próximo ingreso.");
            assertThat(result.session().token().value()).isNotBlank();
        }

        @Test
        @DisplayName("8.1 paso 6: la entrega ocurre después de crear la cuenta, en otra transacción")
        void deliveryAfterAccountInSeparateTransaction() {
            List<Integer> transactionsSeenByDelivery = new ArrayList<>();
            delivery = playerId -> {
                // La cuenta ya existe y su transacción ya terminó cuando empieza la entrega.
                assertThat(players.created()).hasSize(1);
                transactionsSeenByDelivery.add(unitOfWork.transactions);
                return InitialCollectionStatus.DELIVERED;
            };

            login("google", "valido:sub-1");

            assertThat(transactionsSeenByDelivery).containsExactly(1);
        }

        @Test
        @DisplayName("La entrega recibe el jugador del ingreso, después de crear su cuenta")
        void deliveryReceivesPlayer() {
            List<UUID> received = new ArrayList<>();
            delivery = playerId -> {
                received.add(playerId);
                return InitialCollectionStatus.ALREADY_DELIVERED;
            };

            LoginResult result = login("google", "valido:sub-1");

            assertThat(received).containsExactly(result.session().player().id());
            assertThat(result.initialCollection()).isEqualTo(InitialCollectionStatus.ALREADY_DELIVERED);
        }
    }

    @Nested
    @DisplayName("Proveedores (Strategy y Registry)")
    class Providers {

        @Test
        @DisplayName("C.4: un segundo proveedor se agrega registrando su clase, sin modificar el caso de uso")
        void secondProviderWithoutChanges() {
            FakeProvider other = new FakeProvider("otro");
            LoginWithProviderUseCase withTwo = useCase(Set.of(), google, other);

            LoginResult viaOther = withTwo.execute(new LoginCommand("otro", "valido:sub-1"));
            LoginResult viaGoogle = withTwo.execute(new LoginCommand("google", "valido:sub-1"));

            // El mismo sub en otro proveedor es OTRA identidad: (proveedor, sub).
            assertThat(viaOther.session().player().id()).isNotEqualTo(viaGoogle.session().player().id());
            assertThat(players.created()).containsOnlyKeys(new ProviderIdentity("otro", "sub-1"),
                    new ProviderIdentity("google", "sub-1"));
            // Ambos reciben el mismo tipo de credenciales propias: la autorización no cambia.
            assertThat(issued).extracting(AuthenticatedUser::role).containsOnly(Role.PLAYER);
        }

        @Test
        @DisplayName("Un proveedor deshabilitado no se registra: UNSUPPORTED_PROVIDER")
        void disabledProviderIsNotRegistered() {
            IdentityProvider disabled = new FakeProvider("apagado") {
                @Override
                public boolean isEnabled() {
                    return false;
                }
            };

            assertThatThrownBy(() -> useCase(Set.of(), google, disabled).execute(new LoginCommand("apagado", "valido:x")))
                    .isInstanceOfSatisfying(DomainException.class, error -> {
                        assertThat(error.type()).isEqualTo(ErrorType.UNSUPPORTED_PROVIDER);
                        assertThat(error.allowedValues()).containsExactly("google");
                    });
        }

        @Test
        @DisplayName("Dos proveedores con el mismo nombre detienen el arranque")
        void duplicateNames() {
            assertThatThrownBy(() -> new IdentityProviderRegistry(List.of(google, new FakeProvider("google"))))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("google");
        }
    }

    /** Ejecuta el trabajo directamente y cuenta las transacciones abiertas. */
    private static final class CountingUnitOfWork implements UnitOfWork {

        private int transactions;

        @Override
        public <T> T execute(Supplier<T> work) {
            transactions++;
            return work.get();
        }
    }
}
