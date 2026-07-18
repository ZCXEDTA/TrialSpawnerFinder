package cn.minecraftfinder.core;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class ResultFiles {
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private ResultFiles() {
    }

    public static Path next(Path directory, String finderName) {
        String base = "results-" + finderName + "-" + TIMESTAMP.format(LocalDateTime.now());
        return next(directory.resolve(base + ".csv"));
    }

    public static Path next(Path requestedPath) {
        Path requested = requestedPath.normalize();
        if (!exists(requested)) return requested;
        String fileName = requested.getFileName().toString();
        int extension = fileName.toLowerCase().endsWith(".csv") ? fileName.length() - 4 : fileName.length();
        String base = fileName.substring(0, extension);
        String suffix = fileName.substring(extension);
        for (int index = 2; ; index++) {
            Path candidate = requested.resolveSibling(base + "-" + index + suffix);
            if (!exists(candidate)) return candidate;
        }
    }

    public static Path textPath(Path csvPath) {
        String fileName = csvPath.getFileName().toString();
        int extension = fileName.toLowerCase().endsWith(".csv") ? fileName.length() - 4 : fileName.length();
        return csvPath.resolveSibling(fileName.substring(0, extension) + ".txt");
    }

    public static boolean exists(Path csvPath) {
        return Files.exists(csvPath) || Files.exists(textPath(csvPath));
    }
}
