package cr.ac.tec.pokearena;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Propósito: punto de entrada de la aplicación PokéArena.
 * Responsabilidades: arrancar Spring Boot y escanear los componentes bajo
 * {@code cr.ac.tec.pokearena}. No contiene lógica ni configuración propia; la configuración
 * de beans vive en {@code infrastructure.config}.
 * Referencia: sección 4.1 de la especificación.
 */
@SpringBootApplication
public class PokearenaApplication {

    public static void main(String[] args) {
        SpringApplication.run(PokearenaApplication.class, args);
    }
}
