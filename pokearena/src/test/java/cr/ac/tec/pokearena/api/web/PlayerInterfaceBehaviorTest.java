package cr.ac.tec.pokearena.api.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opentest4j.TestAbortedException;

/**
 * Propósito: ejecutar las pruebas funcionales JavaScript de la interfaz del jugador en clean verify, sin
 * frameworks ni paquetes npm (sección 11).
 * Responsabilidades: buscar Node.js 20 o superior, ejecutar {@code node --test}, propagar sus fallos y
 * limitar el tiempo de ejecución. Los logs viven fuera del repositorio, en el directorio temporal de JUnit.
 * Referencia: sección 11 (interfaz web) y sección 3 (Node 20 es un requisito opcional).
 *
 * <p>Decisión: Node es un requisito <b>opcional</b> del proyecto (sección 3 y README). Si no está en
 * {@code PATH}, o su versión es menor que 20, la prueba se <b>omite</b> con un aviso claro (JUnit
 * Assumptions) en lugar de fallar, para que {@code clean verify} pase en una máquina con solo Java y
 * Docker. Si Node está disponible, cualquier falla de las pruebas JavaScript sí hace fallar el build.
 */
@DisplayName("Comportamiento de la interfaz del jugador")
class PlayerInterfaceBehaviorTest {

    private static final long MAX_SECONDS = 45;
    private static final int MIN_NODE_MAJOR = 20;
    private static final Pattern NODE_VERSION = Pattern.compile("^v(\\d+)\\.");
    private static final String NODE_SKIPPED = "Pruebas de JavaScript OMITIDAS: requieren Node.js "
            + MIN_NODE_MAJOR + " o superior en PATH (requisito opcional; ver la sección Requisitos del README). "
            + "Comprueba con node --version.";

    @TempDir
    Path temporaryDirectory;

    @Test
    @DisplayName("Historias 3.1.2, 1.1.1 y 3.1.1: filtros, paginación, sesión, privacidad y presentación de la colección")
    void javascriptBehavior() throws Exception {
        String node = System.getProperty("os.name").startsWith("Windows") ? "node.exe" : "node";
        requireNode(node, temporaryDirectory.resolve("node-version.log"));

        // Ejecuta los comportamientos reales, incluidos eventos del formulario y acciones de casos rápidos.
        String output = runNode(node, temporaryDirectory.resolve("player-tests.log"),
                "--test", "src/test/javascript/player.test.mjs");
        assertThat(output).as("node --test no ejecutó ninguna prueba").contains("pass");
    }

    @Test
    @DisplayName("Sin Node disponible, la prueba se omite con un aviso claro en lugar de fallar")
    void missingNodeSkipsWithNotice() {
        String absent = temporaryDirectory.resolve("node-inexistente").toString();

        assertThatThrownBy(() -> requireNode(absent, temporaryDirectory.resolve("missing-node.log")))
                .isInstanceOf(TestAbortedException.class)
                .hasMessageContaining("OMITIDAS").hasMessageContaining("Node.js 20 o superior")
                .hasMessageContaining("PATH");
    }

    /** Omite la prueba si Node no existe o es anterior a la versión 20. */
    private static void requireNode(String node, Path output) throws Exception {
        Process process;
        try {
            process = new ProcessBuilder(node, "--version").redirectErrorStream(true)
                    .redirectOutput(output.toFile()).start();
        } catch (IOException unavailable) {
            Assumptions.abort(NODE_SKIPPED + " No se encontró el ejecutable.");
            return;
        }
        boolean completed = process.waitFor(MAX_SECONDS, TimeUnit.SECONDS);
        if (!completed) {
            process.destroyForcibly();
        }
        String version = Files.readString(output).strip();
        Matcher major = NODE_VERSION.matcher(version);
        Assumptions.assumeTrue(completed && process.exitValue() == 0 && major.find()
                && Integer.parseInt(major.group(1)) >= MIN_NODE_MAJOR,
                () -> NODE_SKIPPED + " Versión encontrada: " + (version.isEmpty() ? "desconocida" : version));
    }

    /** Ejecuta Node y exige que termine a tiempo y sin errores. */
    private static String runNode(String node, Path output, String... arguments) throws Exception {
        String[] command = new String[arguments.length + 1];
        command[0] = node;
        System.arraycopy(arguments, 0, command, 1, arguments.length);
        Process process = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(output.toFile()).start();
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
