package cr.ac.tec.pokearena.infrastructure.storage;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;

import org.springframework.stereotype.Service;

import net.coobird.thumbnailator.Thumbnails;

@Service
public class ImageService {
    private final String uploadDir = "./uploads/cards/";

    public String saveLargeImage(byte[] imageBytes) throws IOException {
        return saveImageToDisk(imageBytes, "large", 0, 0);
    }

    public String saveSmallImage(byte[] imageBytes) throws IOException {
        // Genera la versión pequeña exacta de 200x280 píxeles requerida por el negocio
        return saveImageToDisk(imageBytes, "small", 200, 280);
    }

    private String saveImageToDisk(byte[] imageBytes, String version, int width, int height) throws IOException {
        Path directoryPath = Paths.get(uploadDir + version);
        if (!Files.exists(directoryPath)) {
            Files.createDirectories(directoryPath);
        }

        String fileName = UUID.randomUUID().toString() + ".jpg";
        Path filePath = directoryPath.resolve(fileName);

        ByteArrayInputStream bais = new ByteArrayInputStream(imageBytes);
        BufferedImage originalImage = ImageIO.read(bais);

        if (originalImage == null) {
            throw new IllegalArgumentException("El archivo de imagen no es válido o está corrupto.");
        }

        if (width > 0 && height > 0) {
            // Usando Thumbnailator para redimensionar de forma óptima a la versión pequeña
            Thumbnails.of(originalImage)
                    .size(width, height)
                    .outputFormat("jpg")
                    .toFile(filePath.toFile());
        } else {
            // Guardar versión grande manteniendo proporciones o formato original
            Thumbnails.of(originalImage)
                    .scale(1.0)
                    .outputFormat("jpg")
                    .toFile(filePath.toFile());
        }

        return filePath.toString();
    }

}
