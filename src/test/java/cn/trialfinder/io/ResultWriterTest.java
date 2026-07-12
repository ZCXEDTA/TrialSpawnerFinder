package cn.trialfinder.io;

import cn.minecraftfinder.core.BlockPoint;
import cn.trialfinder.model.SearchResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResultWriterTest {
    @TempDir
    Path directory;

    @Test
    void sortsAndKeepsTopOneHundred() throws IOException {
        List<SearchResult> results = new ArrayList<>();
        for (int i = 0; i < 105; i++) {
            results.add(new SearchResult(i, -i, 2, i, List.of(new BlockPoint(i, -i))));
        }
        Path output = directory.resolve("results.csv");

        ResultWriter.write(output, results);

        List<String> lines = Files.readAllLines(output);
        assertEquals(101, lines.size());
        assertEquals("\uFEFF排名;中心X;中心Z;密室数量;试炼刷怪笼数量;密室位置", lines.get(0));
        assertTrue(lines.get(1).startsWith("1;104;-104;2;104;"));
        assertTrue(Files.exists(ResultWriter.textPath(output)));
    }

    @Test
    void keepsTopOneHundredPerStructureCountThenGloballySorts() throws IOException {
        List<SearchResult> results = new ArrayList<>();
        for (int structures = 1; structures <= 3; structures++) {
            for (int score = 0; score < 105; score++) {
                List<BlockPoint> positions = new ArrayList<>();
                for (int index = 0; index < structures; index++) {
                    positions.add(new BlockPoint(score + index, structures));
                }
                results.add(new SearchResult(score, structures, structures, score, positions));
            }
        }
        Path output = directory.resolve("groups.csv");

        ResultWriter.write(output, results);

        List<String> lines = Files.readAllLines(output);
        assertEquals(301, lines.size());
        assertTrue(lines.get(1).startsWith("1;104;3;3;104;"));
        assertTrue(lines.get(2).startsWith("2;104;2;2;104;"));
        assertTrue(lines.get(3).startsWith("3;104;1;1;104;"));
        assertTrue(lines.get(300).startsWith("300;5;1;1;5;"));
    }
}
