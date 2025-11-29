package org.example.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class DebugLog {
    private static final boolean ENABLED = Boolean.parseBoolean(
            System.getProperty("mejais.debugLog",
                    System.getenv().getOrDefault("MEJAIS_DEBUG_LOG", "false"))
    );
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Path LOG_FILE = ENABLED ? initLogFile() : null;

    private DebugLog() {
    }

    private static Path initLogFile() {
        try {
            Path logsDir = AppPaths.locateDataDir().resolve("logs");
            Files.createDirectories(logsDir);
            return logsDir.resolve("mejais.log");
        } catch (Exception e) {
            System.err.println("Failed to prepare debug log: " + e.getMessage());
            return null;
        }
    }

    public static void log(String message) {
        if (!ENABLED || LOG_FILE == null || message == null) return;
        String line = "[" + LocalDateTime.now().format(FORMATTER) + "] " + message + System.lineSeparator();
        try {
            Files.writeString(LOG_FILE, line, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {
        }
    }
}
