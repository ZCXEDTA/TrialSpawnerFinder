package cn.minecraftfinder.core;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class ResultTableWriter {
    private ResultTableWriter() {
    }

    public static void write(Path csvPath, List<String> headers, List<List<String>> rows) throws IOException {
        write(csvPath, headers, rows, rows);
    }

    public static void write(
            Path csvPath,
            List<String> headers,
            List<List<String>> csvRows,
            List<List<String>> textRows) throws IOException {
        if (csvRows.size() != textRows.size()) {
            throw new IllegalArgumentException("CSV 与对齐文本的结果行数不一致");
        }
        List<List<String>> checkedCsvRows = validateRows(headers, csvRows);
        List<List<String>> checkedTextRows = validateRows(headers, textRows);
        try (BufferedWriter writer = Files.newBufferedWriter(csvPath, StandardCharsets.UTF_8)) {
            writer.write('\uFEFF');
            writer.write(String.join(";", headers));
            writer.newLine();
            for (List<String> row : checkedCsvRows) {
                writer.write(String.join(";", row));
                writer.newLine();
            }
        }
        writeAligned(ResultFiles.textPath(csvPath), headers, checkedTextRows);
    }

    private static List<List<String>> validateRows(
            List<String> headers, List<List<String>> rows) {
        List<List<String>> copiedRows = new ArrayList<>(rows.size());
        for (List<String> source : rows) {
            List<String> row = List.copyOf(source);
            if (row.size() != headers.size()) {
                throw new IllegalArgumentException("结果行列数与表头不一致");
            }
            copiedRows.add(row);
        }
        return List.copyOf(copiedRows);
    }

    private static void writeAligned(
            Path path, List<String> headers, List<List<String>> rows) throws IOException {
        int[] widths = headers.stream().mapToInt(ResultTableWriter::displayWidth).toArray();
        for (List<String> row : rows) {
            for (int column = 0; column < row.size(); column++) {
                widths[column] = Math.max(widths[column], displayWidth(row.get(column)));
            }
        }

        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            writer.write('\uFEFF');
            writeRow(writer, headers, widths, false);
            for (List<String> row : rows) {
                writeRow(writer, row, widths, true);
            }
        }
    }

    private static void writeRow(
            BufferedWriter writer, List<String> values, int[] widths, boolean numeric) throws IOException {
        for (int column = 0; column < values.size(); column++) {
            if (column > 0) writer.write("  ");
            String value = values.get(column);
            boolean alignRight = numeric && column < values.size() - 1;
            writer.write(alignRight ? padLeft(value, widths[column]) : padRight(value, widths[column]));
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
}
