package cn.trialfinder.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class QueryCommandTest {

    @TempDir
    Path tempDir;

    @Test
    void parsesWhitespaceCoordinatesFile() throws Exception {
        Path file = tempDir.resolve("coords.txt");
        Files.writeString(file, """
                # comment
                544 166
                1000 -2000
                -500,300

                """, StandardCharsets.UTF_8);

        List<int[]> points = new ArrayList<>();
        QueryCommand.parseQueryFile(file, points);

        assertEquals(3, points.size());
        assertArrayEquals(new int[]{544, 166}, points.get(0));
        assertArrayEquals(new int[]{1000, -2000}, points.get(1));
        assertArrayEquals(new int[]{-500, 300}, points.get(2));
    }

    @Test
    void parsesResultsCsvCenterColumns() throws Exception {
        Path file = tempDir.resolve("results.csv");
        Files.writeString(file,
                "﻿排名;中心X;中心Z;密室数量;试炼刷怪笼数量;密室位置\n"
                        + "1;544;166;5;80;1,2|3,4\n"
                        + "2;1000;-2000;3;50;5,6\n",
                StandardCharsets.UTF_8);

        List<int[]> points = new ArrayList<>();
        QueryCommand.parseQueryFile(file, points);

        assertEquals(2, points.size());
        assertArrayEquals(new int[]{544, 166}, points.get(0));
        assertArrayEquals(new int[]{1000, -2000}, points.get(1));
    }

    @Test
    void parsesCsvHeaderOrderIndependent() throws Exception {
        Path file = tempDir.resolve("results2.csv");
        Files.writeString(file,
                "试炼刷怪笼数量;中心Z;排名;中心X;密室数量\n"
                        + "80;-2000;1;544;5\n",
                StandardCharsets.UTF_8);

        List<int[]> points = new ArrayList<>();
        QueryCommand.parseQueryFile(file, points);

        assertEquals(1, points.size());
        assertArrayEquals(new int[]{544, -2000}, points.get(0));
    }
}
