package cn.trialfinder.io;

import cn.trialfinder.model.BlockPoint;
import cn.trialfinder.model.SearchResult;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

public final class ResultWriter {
    private ResultWriter() {
    }

    public static void write(Path path, List<SearchResult> results) throws IOException {
        List<SearchResult> sorted = results.stream()
                .collect(Collectors.groupingBy(SearchResult::structureCount))
                .values().stream()
                .flatMap(group -> group.stream().sorted().limit(100))
                .sorted()
                .toList();

        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            writer.write("rank;center-x;center-z;structure-count;trial-spawner-count;structure-positions");
            writer.newLine();
            for (int i = 0; i < sorted.size(); i++) {
                SearchResult result = sorted.get(i);
                String structures = result.structures().stream()
                        .map(ResultWriter::formatPoint)
                        .collect(Collectors.joining("|"));
                writer.write("%d;%d;%d;%d;%d;%s".formatted(
                        i + 1, result.centerX(), result.centerZ(), result.structureCount(),
                        result.spawnerCount(), structures));
                writer.newLine();
            }
        }
    }

    private static String formatPoint(BlockPoint point) {
        return point.x() + "," + point.z();
    }
}
