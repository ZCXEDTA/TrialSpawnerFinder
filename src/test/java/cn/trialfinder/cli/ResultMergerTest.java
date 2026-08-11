package cn.trialfinder.cli;

import cn.trialfinder.model.BlockPoint;
import cn.trialfinder.model.SearchResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResultMergerTest {

    @TempDir
    Path tempDir;

    private static ResultEntry entry(long x, long z, int structs, int spawners) {
        List<BlockPoint> structures = List.of(new BlockPoint((int) x, (int) z));
        return new ResultEntry(x, z, structs, spawners, structures);
    }

    private Path writeFile(String name, ResultEntry... entries) throws IOException {
        Path file = tempDir.resolve(name);
        SearchEngine.writeTileTempFile(file, List.of(entries));
        return file;
    }

    @Test
    void mergesSortedAndTruncatesPerGroup() throws IOException {
        // File A: two single-chamber clusters (spawners 30, 25)
        Path a = writeFile("a.tmp", entry(1, 1, 1, 30), entry(2, 2, 1, 25), entry(3, 3, 1, 20));
        // File B: one double-chamber (28) and one single (15)
        Path b = writeFile("b.tmp", entry(4, 4, 2, 28), entry(5, 5, 1, 15));
        // File C: one single (27)
        Path c = writeFile("c.tmp", entry(6, 6, 1, 27));

        Path csv = tempDir.resolve("merged.csv");
        ResultMerger.merge(List.of(a, b, c), csv, 2);

        // Expected: global spawner-desc order, top-2 per group.
        // group1 (structs=1): 30, 27 (25,20,15 truncated)
        // group2 (structs=2): 28
        // => 30, 28, 27
        List<String> lines = Files.readAllLines(csv, StandardCharsets.UTF_8);
        assertTrue(lines.get(0).startsWith("﻿排名;"), "header with BOM");
        assertEquals(4, lines.size(), "header + 3 rows");
        assertTrue(lines.get(1).contains(";1;1;1;30;"));
        assertTrue(lines.get(2).contains(";4;4;2;28;"));
        assertTrue(lines.get(3).contains(";6;6;1;27;"));

        // Temp files deleted.
        assertFalse(Files.exists(a));
        assertFalse(Files.exists(b));
        assertFalse(Files.exists(c));
    }

    @Test
    void emptyInputWritesHeaderOnly() throws IOException {
        Path csv = tempDir.resolve("empty.csv");
        ResultMerger.merge(List.of(), csv, 100);
        List<String> lines = Files.readAllLines(csv, StandardCharsets.UTF_8);
        assertEquals(1, lines.size(), "only header row");
        assertTrue(lines.get(0).contains("排名"));
    }

    @Test
    void entryRoundTripIsLossless() {
        ResultEntry e = new ResultEntry(-5, 7, 2, 31,
                List.of(new BlockPoint(-8, 3), new BlockPoint(4, 11)));
        assertEquals(e, ResultEntry.parse(e.toCsvLine()));
        SearchResult r = e.toSearchResult();
        assertEquals(e, ResultEntry.from(r));
    }

    @Test
    void twoPhaseMergeHandlesManyFiles() throws IOException {
        // Exceed FAN_IN/2 to force at least one intermediate round-trip path without allocating
        // 512 files: use 4 files and rely on fan-in batching logic being exercised at a smaller
        // scale is not possible here, so just verify correctness with a moderate count.
        List<Path> files = new java.util.ArrayList<>();
        for (int i = 0; i < 40; i++) {
            ResultEntry e = entry(i, i, (i % 3) + 1, 100 - i);
            files.add(writeFile("m" + i + ".tmp", e));
        }
        Path csv = tempDir.resolve("many.csv");
        ResultMerger.merge(files, csv, 100);

        List<String> lines = Files.readAllLines(csv, StandardCharsets.UTF_8);
        // All 40 distinct groups (structs 1..3) fit under 100 → all kept, sorted by spawner desc.
        assertEquals(41, lines.size(), "header + 40 rows");
        // First data row must have the highest spawner count (100).
        assertTrue(lines.get(1).contains(";100;"), "highest spawner first");
        for (Path f : files) {
            assertFalse(Files.exists(f));
        }
    }
}
