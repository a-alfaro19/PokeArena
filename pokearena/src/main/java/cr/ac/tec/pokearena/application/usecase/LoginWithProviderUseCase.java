package cr.ac.tec.pokearena.application.usecase;

import cr.ac.tec.pokearena.application.dto.LoginCommand;
import cr.ac.tec.pokearena.application.dto.LoginResult;
import cr.ac.tec.pokearena.application.dto.Session;
import cr.ac.tec.pokearena.application.port.AuthenticatedUser;
import cr.ac.tec.pokearena.application.port.IdentityProvider;
import cr.ac.tec.pokearena.application.port.IdentityProviderRegistry;
import cr.ac.tec.pokearena.application.port.InitialCollectionDelivery;
import cr.ac.tec.pokearena.application.port.InitialCollectionStatus;
import cr.ac.tec.pokearena.application.port.TokenIssuer;
import cr.ac.tec.pokearena.application.port.TokenIssuer.IssuedToken;
import cr.ac.tec.pokearena.application.port.VerifiedIdentity;
import cr.ac.tec.pokearena.domain.error.ValidationException;
import cr.ac.tec.pokearena.domain.model.Player;
import cr.ac.tec.pokearena.domain.model.PlayerAccount;

/**
 * Propósito: caso de uso de la historia 1.1.1 "Inicio de sesión con proveedor".
 * Responsabilidades: orquestar el registro de proveedores, la verificación del token, la cuenta del
 * jugador, la entrega de la colección inicial y la emisión de las credenciales propias, en el orden del
 * diagrama de secuencia.
 * Referencia: sección 8.1 (tabla de pasos) y Anexo A.4 (diagrama de secuencia).
 *
 * <h2>Plantilla de los casos de uso (sección 4.4)</h2>
 * Sigue el mismo esqueleto que {@link CreateCardUseCase}:
 * <ol>
 *   <li><b>Autorizar:</b> la operación es pública ({@code @PublicRoute}); quien llama todavía no tiene
 *       credenciales, por eso las pide.</li>
 *   <li><b>Idempotencia:</b> no hace falta clave. Iniciar sesión dos veces con el mismo token deja la
 *       misma cuenta: la unicidad de {@code (provider, sub)} en la base ya lo garantiza.</li>
 *   <li><b>Validar</b> (pasos 1 a 4): campos presentes, proveedor registrado y token verificado (firma,
 *       emisor, vencimiento y audiencia). <b>Todo antes de abrir la transacción:</b> un token inválido o
 *       un proveedor que no responde no dejan ninguna fila.</li>
 *   <li><b>Ejecutar en UnitOfWork</b> (paso 5): {@link PlayerAccountService} busca o crea la cuenta en su
 *       propia transacción y la confirma. La entrega de la colección (paso 6) usa otra, como pide la
 *       sección 8.2 ("todo o nada" de la entrega): si la entrega se revierte, la cuenta sigue existiendo.</li>
 *   <li><b>Responder</b> (pasos 7 y 8): credenciales propias y el resultado.</li>
 * </ol>
 *
 * <h2>Decisiones</h2>
 * <ul>
 *   <li>La identidad es {@code (proveedor, sub)}, nunca el correo: si el jugador cambia su correo en el
 *       proveedor, conserva su cuenta (criterio C.4).</li>
 *   <li>El nombre y el correo pueden faltar (Google no siempre los envía): se guardan como nulos y la
 *       respuesta muestra el nombre de respaldo de {@link Player#visibleName()}.</li>
 *   <li>Paso 6 "no bloquea el ingreso": la entrega ({@code DeliverInitialCollectionUseCase}, por el puerto
 *       {@link InitialCollectionDelivery}) corre después de tener la cuenta y en su propia transacción. Si
 *       lanza una excepción, {@link NonBlockingInitialCollection} registra un aviso y la sesión se abre igual
 *       con {@code PENDING_ERROR}; el siguiente ingreso vuelve a intentar.</li>
 *   <li>Este caso de uso nunca ve un token de PokéArena entrante: después del ingreso, la autorización de
 *       la API solo usa las credenciales que se emiten en el paso 7. Por eso agregar un proveedor no la
 *       cambia.</li>
 * </ul>
 */
public class LoginWithProviderUseCase {

    private final IdentityProviderRegistry providers;
    private final PlayerAccountService accounts;
    private final NonBlockingInitialCollection initialCollection;
    private final TokenIssuer tokenIssuer;

    public LoginWithProviderUseCase(IdentityProviderRegistry providers, PlayerAccountService accounts,
            InitialCollectionDelivery initialCollection, TokenIssuer tokenIssuer) {
        this.providers = providers;
        this.accounts = accounts;
        this.initialCollection = new NonBlockingInitialCollection(initialCollection);
        this.tokenIssuer = tokenIssuer;
    }

    /**
     * Inicia la sesión de un jugador con el token de su proveedor de identidad.
     *
     * @param command proveedor y token, tal como llegaron
     * @return sesión, primer ingreso y estado de la colección inicial
     */
    public LoginResult execute(LoginCommand command) {
        // Paso 1 (mensajes 1 a 5 del diagrama A.4): la interfaz obtuvo el token del proveedor y lo envió.
        // Ambos campos son obligatorios.
        String providerName = required(command.provider(), LoginCommand.PROVIDER_FIELD,
                "Debe indicar el proveedor de identidad.");
        String idToken = required(command.idToken(), LoginCommand.ID_TOKEN_FIELD,
                "Debe enviar el token de identidad del proveedor.");

        // Paso 2 (mensajes 6 y 7): obtener el proveedor del registro. UNSUPPORTED_PROVIDER si no está.
        IdentityProvider provider = providers.get(providerName);

        // Paso 3 (mensaje 8): verificar firma, emisor, vencimiento y audiencia. Ocurre ANTES de la
        // transacción: INVALID_IDENTITY_TOKEN o IDENTITY_PROVIDER_UNAVAILABLE no dejan nada creado.
        // Paso 4 (mensaje 9): identidad verificada con sub, correo y nombre.
        VerifiedIdentity verified = provider.verify(idToken);

        // Paso 5 (mensajes 10 a 13): en una transacción, buscar el jugador por (proveedor, sub) o crearlo
        // con rol PLAYER (o ADMIN si está en app.admin.identities), Elo 0 y colección PENDING.
        PlayerAccount account = accounts.findOrCreateAccount(verified.identity(),
                blankToNull(verified.displayName()), blankToNull(verified.email()));
        Player player = account.player();

        // Paso 6 (mensajes 14 y 15): entregar la colección inicial, DESPUÉS de tener la cuenta y en otra
        // transacción (la del caso de uso RecibirColecciónInicial, diagrama A.5). Nunca impide el ingreso:
        // si falla, PENDING_ERROR con un aviso en el log, y el siguiente ingreso reintenta.
        InitialCollectionStatus collection = initialCollection.deliver(player.id());

        // Paso 7 (mensajes 16 y 17): emitir las credenciales propias, con vencimiento corto.
        IssuedToken token = tokenIssuer.issue(new AuthenticatedUser(player.id(), player.role()));

        // Paso 8 (mensajes 18 a 20): la API responde 200 con token, jugador y estado de la colección.
        return new LoginResult(new Session(player, token), account.created(), collection);
    }

    private static String required(String value, String field, String message) {
        if (value == null || value.isBlank()) {
            throw ValidationException.missingField(field, message);
        }
        return value;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
