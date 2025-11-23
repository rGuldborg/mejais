package org.example.tools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.Normalizer;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

public class AssetNormalizer {
    public static void main(String[] args) throws IOException {
        renameChampSquares();
        renameRoleIcons();
    }

    private static void renameChampSquares() throws IOException {
        Path dir = Paths.get("src/main/resources/org/example/images/champSquare");
        Set<String> seen = new HashSet<>();
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".png"))
                 .forEach(path -> {
                     try {
                         String base = path.getFileName().toString();
                         String normalized = normalizeChampName(base);
                         if (normalized == null) return;
                         Path target = dir.resolve(normalized + ".png");
                         if (seen.add(target.getFileName().toString())) {
                             if (!path.equals(target)) {
                                 Files.move(path, target, StandardCopyOption.REPLACE_EXISTING);
                                 System.out.println("Renamed " + base + " -> " + target.getFileName());
                             }
                         } else {
                             System.err.println("Duplicate asset name skipped: " + target);
                         }
                     } catch (IOException ex) {
                         System.err.println("Failed to rename " + path + ": " + ex.getMessage());
                     }
                 });
        }
    }

    private static void renameRoleIcons() throws IOException {
        Path dir = Paths.get("src/main/resources/org/example/images/roles");
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".png"))
                 .forEach(path -> {
                     try {
                         String base = path.getFileName().toString();
                         if (base.equals("TopIcon.png") || base.equals("JungleIcon.png")
                                 || base.equals("MiddleIcon.png") || base.equals("BottomIcon.png")
                                 || base.equals("SupportIcon.png")) {
                             return;
                         }
                         String root = base.replace(".png", "");
                         String normalized = normalizeTokens(root);
                         if (normalized == null) return;
                         Path target = dir.resolve(normalized + ".png");
                         if (!path.equals(target)) {
                             Files.move(path, target, StandardCopyOption.REPLACE_EXISTING);
                             System.out.println("Renamed role icon " + base + " -> " + target.getFileName());
                         }
                     } catch (IOException ex) {
                         System.err.println("Failed to rename role " + path + ": " + ex.getMessage());
                     }
                 });
        }
    }

    private static String normalizeChampName(String fileName) {
        String root = fileName.replace(".png", "");
        while (root.toLowerCase(Locale.ROOT).endsWith("square")) {
            root = root.substring(0, root.length() - 6);
        }
        String cleaned = normalizeTokens(root);
        return cleaned == null ? null : cleaned + "Square";
    }

    private static String normalizeTokens(String raw) {
        if (raw == null) return null;
        String decoded = raw.replace("%27", " ");
        decoded = Normalizer.normalize(decoded, Normalizer.Form.NFD).replaceAll("[^\\p{Alnum} ]+", " ");
        String[] tokens = decoded.split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String token : tokens) {
            if (token.isBlank() || token.chars().allMatch(Character::isDigit)) continue;
            if ("square".equalsIgnoreCase(token)) continue;
            sb.append(capitalize(token));
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    private static String capitalize(String token) {
        if (token.isEmpty()) return token;
        String lower = token.toLowerCase(Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
}
