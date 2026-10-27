package cr.ac.tec.pokearena;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CardSearchArchitectureTests {
    @Test
    void domainAndApplicationKeepTheirDependencyBoundaries() throws Exception {
        // Verifica imports reales para detectar dependencias inversas al agregar clases futuras.
        Path root = Path.of("src/main/java/cr/ac/tec/pokearena");
        for (String layer : new String[] { "domain", "application" }) {
            try (var sources = Files.walk(root.resolve(layer))) {
                for (Path source : sources.filter(path -> path.toString().endsWith(".java")).toList()) {
                    for (String line : Files.readAllLines(source)) {
                        if (line.startsWith("import ")) {
                            assertFalse(line.contains("org.springframework") || line.contains(".infrastructure.")
                                    || line.contains("java.sql.") || line.contains("tools.jackson"), source.toString());
                            if ("domain".equals(layer)) {
                                assertFalse(line.contains(".application."), source.toString());
                            }
                        }
                    }
                }
            }
        }
    }
}
