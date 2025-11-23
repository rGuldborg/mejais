package org.example.tools;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class WebpConverter {
    public static void main(String[] args) throws IOException {
        Path root = Paths.get("src/main/resources/org/example/images");
        List<Path> webps = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(p -> Files.isRegularFile(p) && p.toString().toLowerCase().endsWith(".webp"))
                    .forEach(webps::add);
        }
        if (webps.isEmpty()) {
            System.out.println("No .webp files discovered under " + root);
            return;
        }
        for (Path source : webps) {
            BufferedImage image = ImageIO.read(source.toFile());
            if (image == null) {
                System.err.println("Skipping unreadable file: " + source);
                continue;
            }
            Path target = replaceExtension(source, ".png");
            ImageIO.write(image, "png", target.toFile());
            Files.deleteIfExists(source);
            System.out.println("Converted " + source + " -> " + target);
        }
    }

    private static Path replaceExtension(Path path, String newExtension) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        if (dot >= 0) {
            name = name.substring(0, dot);
        }
        return path.resolveSibling(name + newExtension);
    }
}
