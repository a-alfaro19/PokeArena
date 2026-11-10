package cr.ac.tec.pokearena.api.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Propósito: ejecutar las pruebas funcionales JavaScript en clean verify, sin frameworks.
 * Responsabilidades: exigir Node.js 20 o superior, propagar fallos y limitar el tiempo de ejecución.
 * Los logs de esta prueba viven fuera del repositorio en el directorio temporal de JUnit.
 */
@DisplayName("Comportamiento de la interfaz del jugador")
class PlayerInterfaceBehaviorTest {

    private static final long MAX_SECONDS = 45;
    private static final int MIN_NODE_MAJOR = 20;
    private static final String NODE_REQUIRED = "Las pruebas de interfaz requieren Node.js 20 o superior en PATH. "
            + "Instala Node.js, abre una terminal nueva y comprueba node --version. "
            + "Consulta la sección Requisitos del README. La prueba no se omite.";

    @TempDir
    Path temporaryDirectory;

    @Test
    @DisplayName("US 43, 35 y 42: filtros, paginación, sesión, privacidad y presentación de colección")
    void javascriptBehavior() throws Exception {
        String node = System.getProperty("os.name").startsWith("Windows") ? "node.exe" : "node";
        String version = runNode(node, temporaryDirectory.resolve("node-version.log"), "--version").strip();
        var major = Pattern.compile("^v(\\d+)\\.").matcher(version);
        assertThat(major.find()).as("%s Versión recibida: %s", NODE_REQUIRED, version).isTrue();
        assertThat(Integer.parseInt(major.group(1))).as(NODE_REQUIRED).isGreaterThanOrEqualTo(MIN_NODE_MAJOR);

        // Ejecuta los comportamientos reales, incluidos eventos del formulario y acciones de casos rápidos.
        runNode(node, temporaryDirectory.resolve("player-tests.log"), "--test", "src/test/javascript/player.test.mjs");
    }

    @Test
    @DisplayName("Sin Node disponible, falla con un diagnóstico útil y no omite las pruebas")
    void missingNodeHasDiagnostic() {
        String absent = temporaryDirectory.resolve("node-inexistente").toString();
        assertThatThrownBy(() -> runNode(absent, temporaryDirectory.resolve("missing-node.log"), "--version"))
                .isInstanceOf(AssertionError.class).hasMessageContaining("Node.js 20 o superior")
                .hasMessageContaining("PATH").hasMessageContaining("node --version")
                .hasCauseInstanceOf(IOException.class);
    }

    /** Distingue un ejecutable ausente de los fallos reales del conjunto de pruebas. */
    private static String runNode(String node, Path output, String... arguments) throws Exception {
        String[] command = new String[arguments.length + 1];
        command[0] = node;
        System.arraycopy(arguments, 0, command, 1, arguments.length);
        Process process;
        try {
            process = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(output.toFile()).start();
        } catch (IOException unavailable) {
            throw new AssertionError(NODE_REQUIRED, unavailable);
        }
        boolean completed = process.waitFor(MAX_SECONDS, TimeUnit.SECONDS);
        if (!completed) {
            process.destroyForcibly();
        }
        assertThat(completed).as("Node debe terminar antes de %s segundos", MAX_SECONDS).isTrue();
        String result = Files.readString(output);
        assertThat(process.exitValue()).as("Falló Node al ejecutar %s:%n%s", String.join(" ", arguments), result).isZero();
        return result;
    }
}
