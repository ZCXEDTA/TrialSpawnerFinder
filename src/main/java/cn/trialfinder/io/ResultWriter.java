package cn.trialfinder.io;

import cn.trialfinder.cli.CheckTopChecker;
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
    /** Extra columns appended when check-top inspection is enabled. */
    private static final String[] CHECK_HEADERS = {"快速刷怪笼", "慢速刷怪笼", "宝库数量"};

    private ResultWriter() {
    }

    public static void write(Path path, List<SearchResult> results) throws IOException {
        write(path, results, 100);
    }

    /** Writes results, keeping at most {@code topN} per structure-count group. */
    public static void write(Path path, List<SearchResult> results, int topN) throws IOException {
        write(path, results, topN, null);
    }

    /** Writes results, keeping at most {@code topN} per structure-count group.
     * When {@code checks} is non-null and non-empty, its per-row tallies are appended as extra
     * columns ({@code \u5FEB\u901F\u5237\u602A\u7B3C}/{@code \u6162\u901F\u5237\u602A\u7B3C}/{@code \u5B9D\u5E93\u6570\u91CF}). The list must be aligned
     * with the sorted output rows (i-th row \u2192 i-th check). */
    public static void write(Path path, List<SearchResult> results, int topN,
                             List<CheckTopChecker.CheckResult> checks) throws IOException {
        List<SearchResult> sorted = results.stream()
                .collect(Collectors.groupingBy(SearchResult::structureCount))
                .values().stream()
                .flatMap(group -> group.stream().sorted().limit(topN))
                .sorted()
                .toList();
        boolean hasChecks = checks != null && !checks.isEmpty();

        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            writer.write('\uFEFF');
            writer.write(String.join(";", HEADERS));
            if (hasChecks) {
                writer.write(";" + String.join(";", CHECK_HEADERS));
            }
            writer.newLine();
            for (int i = 0; i < sorted.size(); i++) {
                SearchResult result = sorted.get(i);
                String structures = result.structures().stream()
                        .map(ResultWriter::formatPoint)
                        .collect(Collectors.joining("|"));
                writer.write("%d;%d;%d;%d;%d;%s".formatted(
                        i + 1, result.centerX(), result.centerZ(), result.structureCount(),
                        result.spawnerCount(), structures));
                if (hasChecks && i < checks.size()) {
                    CheckTopChecker.CheckResult c = checks.get(i);
                    writer.write(";%d;%d;%d".formatted(c.fastSpawners(), c.slowSpawners(), c.vaults()));
                }
                writer.newLine();
            }
        }
        writeAlignedText(textPath(path), sorted, hasChecks ? checks : null);
    }

    public static Path textPath(Path csvPath) {
        String fileName = csvPath.getFileName().toString();
        int extension = fileName.toLowerCase().endsWith(".csv") ? fileName.length() - 4 : fileName.length();
        return csvPath.resolveSibling(fileName.substring(0, extension) + ".txt");
    }

    private static void writeAlignedText(Path path, List<SearchResult> results,
                                         List<CheckTopChecker.CheckResult> checks) throws IOException {
        boolean hasChecks = checks != null && !checks.isEmpty();
        String[] headers = hasChecks ? concat(HEADERS, CHECK_HEADERS) : HEADERS;
        List<String[]> rows = new java.util.ArrayList<>(results.size());
        int[] widths = java.util.Arrays.stream(headers).mapToInt(ResultWriter::displayWidth).toArray();
        for (int index = 0; index < results.size(); index++) {
            SearchResult result = results.get(index);
            String structures = result.structures().stream()
                    .map(ResultWriter::formatPoint)
                    .collect(Collectors.joining(" | "));
            List<String> cells = new java.util.ArrayList<>(java.util.Arrays.asList(
                    Integer.toString(index + 1), Long.toString(result.centerX()),
                    Long.toString(result.centerZ()), Integer.toString(result.structureCount()),
                    Integer.toString(result.spawnerCount()), structures));
            if (hasChecks && index < checks.size()) {
                CheckTopChecker.CheckResult c = checks.get(index);
                cells.add(Integer.toString(c.fastSpawners()));
                cells.add(Integer.toString(c.slowSpawners()));
                cells.add(Integer.toString(c.vaults()));
            }
            String[] row = cells.toArray(new String[0]);
            for (int column = 0; column < row.length; column++) {
                widths[column] = Math.max(widths[column], displayWidth(row[column]));
            }
            rows.add(row);
        }

        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            writer.write('\uFEFF');
            writeTextRow(writer, headers, widths, false);
            for (String[] row : rows) {
                writeTextRow(writer, row, widths, true);
            }
        }
    }

    private static String[] concat(String[] a, String[] b) {
        String[] result = new String[a.length + b.length];
        System.arraycopy(a, 0, result, 0, a.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
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
