package cn.trialfinder.io;

import cn.minecraftfinder.core.BlockPoint;
import cn.minecraftfinder.core.ResultFiles;
import cn.minecraftfinder.core.ResultTableWriter;
import cn.trialfinder.model.SearchResult;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public final class ResultWriter {
    private static final List<String> HEADERS = List.of(
            "排名", "中心X", "中心Z", "密室数量", "试炼刷怪笼数量", "密室位置");

    private ResultWriter() {
    }

    public static void write(Path path, List<SearchResult> results) throws IOException {
        List<List<String>> csvRows = new ArrayList<>(results.size());
        List<List<String>> textRows = new ArrayList<>(results.size());
        for (int index = 0; index < results.size(); index++) {
            SearchResult result = results.get(index);
            List<String> prefix = List.of(
                    Integer.toString(index + 1),
                    Long.toString(result.centerX()),
                    Long.toString(result.centerZ()),
                    Integer.toString(result.structureCount()),
                    Integer.toString(result.spawnerCount()));
            csvRows.add(row(prefix, result.structures().stream().map(ResultWriter::formatPoint)
                    .collect(Collectors.joining("|"))));
            textRows.add(row(prefix, result.structures().stream().map(ResultWriter::formatPoint)
                    .collect(Collectors.joining(" | "))));
        }
        ResultTableWriter.write(path, HEADERS, csvRows, textRows);
    }

    public static Path textPath(Path csvPath) {
        return ResultFiles.textPath(csvPath);
    }

    private static String formatPoint(BlockPoint point) {
        return point.x() + "," + point.z();
    }

    private static List<String> row(List<String> prefix, String structures) {
        List<String> row = new ArrayList<>(prefix);
        row.add(structures);
        return List.copyOf(row);
    }
}
