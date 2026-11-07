package cr.ac.tec.pokearena.application.port;

import cr.ac.tec.pokearena.domain.error.AuthenticationException;
import cr.ac.tec.pokearena.domain.error.IdentityProviderException;

/**
 * Propósito: puerto de un proveedor de identidad externo (patrón Strategy, sección 4.4; participante
 * "Verificador de Google" del diagrama A.4).
 * Responsabilidades: verificar el token de identidad que el proveedor le entregó a la interfaz y devolver
 * la identidad verificada. Cada proveedor es una implementación; el caso de uso no sabe cuál usa.
 * Referencia: sección 8.1, pasos 3 y 4, y criterio C.4 ("agregar un segundo proveedor no requiere
 * cambiar cómo se autorizan las solicitudes").
 *
 * <p>Agregar un proveedor es escribir una clase que implemente esta interfaz y declararla como bean:
 * {@link IdentityProviderRegistry} la encuentra por su {@link #name()}. Ni el caso de uso ni la
 * autorización de la API cambian, porque después del ingreso todo usa las credenciales propias.
 */
public interface IdentityProvider {

    /** Nombre con el que la interfaz pide este proveedor, por ejemplo {@code google}. */
    String name();

    /**
     * Si el proveedor está listo para usarse. Un proveedor sin su configuración (por ejemplo, Google
     * sin {@code client id}) devuelve {@code false}: el registro no lo ofrece y la aplicación arranca igual.
     */
    default boolean isEnabled() {
        return true;
    }

    /**
     * Verifica el token del proveedor.
     *
     * @param idToken token tal como lo entregó el proveedor
     * @return la identidad verificada
     * @throws AuthenticationException    INVALID_IDENTITY_TOKEN si el token no es válido por cualquier motivo
     * @throws IdentityProviderException IDENTITY_PROVIDER_UNAVAILABLE si no se puede consultar al proveedor
     */
    VerifiedIdentity verify(String idToken);
}
