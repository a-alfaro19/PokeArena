package cr.ac.tec.pokearena.api.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import cr.ac.tec.pokearena.domain.model.Role;

/**
 * Propósito: declarar qué rol exige una operación de la API (sección 5.2).
 * Responsabilidades: marcar un método de controlador, o una clase completa, como protegido.
 * {@link AuthorizationInterceptor} la lee antes de llamar al controlador.
 * Referencia: sección 5.1 (columna "Rol") y sección 5.2.
 *
 * <p>Si la anotación está en el método y en la clase, manda la del método. Una operación sin la
 * anotación es pública. Si varios roles pueden usarla, se listan todos:
 * {@code @RequiresRole({Role.PLAYER, Role.ADMIN})}.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.METHOD, ElementType.TYPE })
public @interface RequiresRole {

    /** Roles que pueden usar la operación; basta con tener uno. */
    Role[] value();
}
