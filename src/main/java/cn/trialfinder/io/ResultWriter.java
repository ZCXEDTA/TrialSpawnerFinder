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
    private static final String[] HEADERS = {
            "排名", "中心X", "中心Z", "密室数量", "试炼刷怪笼数量", "密室位置"
    };

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
            writer.write('\uFEFF');
            writer.write(String.join(";", HEADERS));
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
        writeAlignedText(textPath(path), sorted);
    }

    public static Path textPath(Path csvPath) {
        String fileName = csvPath.getFileName().toString();
        int extension = fileName.toLowerCase().endsWith(".csv") ? fileName.length() - 4 : fileName.length();
        return csvPath.resolveSibling(fileName.substring(0, extension) + ".txt");
    }

    private static void writeAlignedText(Path path, List<SearchResult> results) throws IOException {
        List<String[]> rows = new java.util.ArrayList<>(results.size());
        int[] widths = java.util.Arrays.stream(HEADERS).mapToInt(ResultWriter::displayWidth).toArray();
        for (int index = 0; index < results.size(); index++) {
            SearchResult result = results.get(index);
            String structures = result.structures().stream()
                    .map(ResultWriter::formatPoint)
                    .collect(Collectors.joining(" | "));
            String[] row = {
                    Integer.toString(index + 1), Long.toString(result.centerX()),
                    Long.toString(result.centerZ()), Integer.toString(result.structureCount()),
                    Integer.toString(result.spawnerCount()), structures
            };
            for (int column = 0; column < row.length; column++) {
                widths[column] = Math.max(widths[column], displayWidth(row[column]));
            }
            rows.add(row);
        }

        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            writer.write('\uFEFF');
            writeTextRow(writer, HEADERS, widths, false);
            for (String[] row : rows) {
                writeTextRow(writer, row, widths, true);
            }
        }
    }

    private static void writeTextRow(
            BufferedWriter writer, String[] values, int[] widths, boolean numeric) throws IOException {
        for (int column = 0; column < values.length; column++) {
            if (column > 0) writer.write("  ");
            boolean alignRight = numeric && column < values.length - 1;
            writer.write(alignRight
                    ? padLeft(values[column], widths[column])
                    : padRight(values[column], widths[column]));
        }
        writer.newLine();
    }

    private static String padLeft(String value, int width) {
        return " ".repeat(Math.max(0, width - displayWidth(value))) + value;
    }

    private static String padRight(String value, int width) {
        return value + " ".repeat(Math.max(0, width - displayWidth(value)));
    }

    private static int displayWidth(String value) {
        return value.codePoints().map(codePoint -> codePoint <= 0x7f ? 1 : 2).sum();
    }

    private static String formatPoint(BlockPoint point) {
        return point.x() + "," + point.z();
    }
}
