package cr.ac.tec.pokearena.api.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Propósito: declarar de forma explícita que una operación de la API es pública (sección 5.2).
 * Responsabilidades: marcar un método de controlador, o una clase completa, que no exige
 * credenciales, como el inicio de sesión o las credenciales de prueba.
 * Referencia: sección 5.2 ("rutas cerradas por defecto").
 *
 * <p>La API es cerrada por defecto: toda operación debe declarar {@link RequiresRole} o esta
 * anotación. Una operación sin ninguna de las dos se rechaza con 403, así olvidar la anotación
 * nunca deja una ruta abierta por accidente.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.METHOD, ElementType.TYPE })
public @interface PublicRoute {
}
