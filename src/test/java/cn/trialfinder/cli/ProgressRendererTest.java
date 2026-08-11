package cn.trialfinder.cli;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgressRendererTest {

    @Test
    void lineHasStageBarPercentAndEta() {
        String line = ProgressRenderer.line(ProgressRenderer.STAGE_A_FLOW, 1058, 1058, 87.2, "00:00:00");
        assertTrue(line.startsWith("[A-Flow"), "stage name first, got: " + line);
        assertTrue(line.contains("[##########] 100% 1058/1058"), "bar + percent, got: " + line);
        assertTrue(line.contains("87.2 座/秒"), "rate, got: " + line);
        assertTrue(line.contains("ETA 00:00:00"), "eta, got: " + line);
    }

    @Test
    void lineLeftAlignsStageToDisplayWidth() {
        // All English stage names are ASCII; "A-Flow" (6 chars) is padded to STAGE_WIDTH=10.
        String line = ProgressRenderer.line(ProgressRenderer.STAGE_A_FLOW, 0, 10, 0.0, "00:00:00");
        int stageEnd = line.indexOf(']');
        String stageColumn = line.substring(1, stageEnd);
        assertEquals(10, stageColumn.length(), "stage column length (all half-width)");
        assertEquals(10, displayWidth(stageColumn), "stage column display width");
        assertTrue(stageColumn.startsWith("A-Flow"), "stage name preserved, got: " + stageColumn);
    }

    @Test
    void allStageNamesFitInWidth() {
        String[] stages = {
                ProgressRenderer.STAGE_A_FLOW,
                ProgressRenderer.STAGE_DENSITY,
                ProgressRenderer.STAGE_B_FLOW,
                ProgressRenderer.STAGE_STAT,
                ProgressRenderer.STAGE_SORT,
                ProgressRenderer.STAGE_OUTPUT,
        };
        for (String stage : stages) {
            assertTrue(stage.codePoints().allMatch(cp -> cp <= 0x7f),
                    "stage must be ASCII: " + stage);
            assertTrue(stage.length() <= ProgressRenderer.STAGE_WIDTH,
                    "stage must fit STAGE_WIDTH: " + stage);
        }
    }

    @Test
    void tileLineShowsStageTileChambersAndEta() {
        String line = ProgressRenderer.tileLine(
                ProgressRenderer.STAGE_GLOBAL_TOP_K, 165124, 368449,
                1_234_567, 18_901, 5_015_000_000_000L, "01:40:55");
        assertTrue(line.startsWith("[全局Top-K"), "stage name first, got: " + line);
        assertTrue(line.contains("Tile 165,124/368,449"), "tile counter, got: " + line);
        assertTrue(line.contains("密室: 1,234,567"), "chambers, got: " + line);
        assertTrue(line.contains("刷怪笼: 18,901"), "second metric, got: " + line);
        assertTrue(line.contains("耗时: 01:23:35"), "elapsed HH:MM:SS, got: " + line);
        assertTrue(line.contains("ETA: 01:40:55"), "eta, got: " + line);
    }

    @Test
    void tileLineUsesOneDecimalPercentForHugeTileCounts() {
        // > 100,000 tiles → percent keeps one decimal place.
        String line = ProgressRenderer.tileLine(
                ProgressRenderer.STAGE_FULL_WORLD, 147379, 368449,
                12_345_678L, 100_000L, 3_121_000_000_000L, "01:18:15");
        assertTrue(line.matches(".*\\[.*\\] \\s*\\d+\\.\\d% Tile .*"),
                "one-decimal percent expected, got: " + line);
        assertTrue(line.contains("40.0%"), "percent with one decimal, got: " + line);
    }

    @Test
    void tileLineDoneShowsFullBarAndHundredPercent() {
        String line = ProgressRenderer.tileLine(
                ProgressRenderer.STAGE_GLOBAL_TOP_K, 368449, 368449,
                9_999_999L, 100_000L, 60_000_000_000L, "00:00:00");
        assertTrue(line.contains("[##########]"), "full bar at done, got: " + line);
        assertTrue(line.contains("100.0%") || line.contains("100%"), "100% at done, got: " + line);
    }

    @Test
    void updateTilePrintsAndNewlineOnDone() {
        ProgressRenderer renderer = new ProgressRenderer();
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        PrintStream original = System.out;
        System.setOut(new PrintStream(buffer));
        try {
            renderer.setStage(ProgressRenderer.STAGE_GLOBAL_TOP_K);
            renderer.updateTile(5, 10, 100L, 5L, 1_000_000_000L, "00:00:01");
            renderer.updateTile(10, 10, 200L, 10L, 2_000_000_000L, "00:00:00");
        } finally {
            System.setOut(original);
        }
        String output = buffer.toString();
        assertTrue(output.contains("\r"), "partial tile line uses carriage return, got: " + output);
        assertTrue(output.contains("[全局Top-K"), "stage in output, got: " + output);
        assertTrue(output.contains("Tile 10/10"), "done line printed, got: " + output);
        assertTrue(output.contains("[##########]"), "done bar, got: " + output);
    }

    @Test
    void updateTileSilentWhenQuiet() {
        ProgressRenderer renderer = ProgressRenderer.disabled();
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        PrintStream original = System.out;
        System.setOut(new PrintStream(buffer));
        try {
            renderer.setStage(ProgressRenderer.STAGE_FULL_WORLD);
            renderer.updateTile(1, 10, 100L, 5L, 1_000_000_000L, "00:00:01");
        } finally {
            System.setOut(original);
        }
        assertEquals("", buffer.toString(), "quiet updateTile must not print");
    }

    @Test
    void updatePrintsProgressLineAndNewlineOnDone() {
        ProgressRenderer renderer = new ProgressRenderer();
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        PrintStream original = System.out;
        System.setOut(new PrintStream(buffer));
        try {
            renderer.setStage(ProgressRenderer.STAGE_B_FLOW);
            renderer.update(5, 10, 10.0, "00:00:01");
            renderer.update(10, 10, 10.0, "00:00:00");
        } finally {
            System.setOut(original);
        }
        String output = buffer.toString();
        assertTrue(output.contains("\r"), "partial line uses carriage return, got: " + output);
        assertTrue(output.contains("[B-Flow"), "stage in output, got: " + output);
        assertTrue(output.contains("100% 10/10"), "done line printed, got: " + output);
    }

    @Test
    void quietSuppressesAllOutput() {
        ProgressRenderer renderer = ProgressRenderer.disabled();
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        PrintStream original = System.out;
        System.setOut(new PrintStream(buffer));
        try {
            renderer.setStage(ProgressRenderer.STAGE_A_FLOW);
            renderer.update(ProgressRenderer.STAGE_A_FLOW, 100, 100, 10.0, "00:00:00");
        } finally {
            System.setOut(original);
        }
        assertEquals("", buffer.toString(), "quiet renderer must not print anything");
        assertTrue(renderer.isQuiet());
    }

    @Test
    void setStageUpdatesCurrentStage() {
        ProgressRenderer renderer = new ProgressRenderer();
        renderer.setStage(ProgressRenderer.STAGE_STAT);
        assertEquals(ProgressRenderer.STAGE_STAT, renderer.getStage());
        renderer.setStage(ProgressRenderer.STAGE_SORT);
        assertEquals(ProgressRenderer.STAGE_SORT, renderer.getStage());
    }

    @Test
    void stageDoneEmitsCompletionLineForCurrentStage() {
        ProgressRenderer renderer = new ProgressRenderer();
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        PrintStream original = System.out;
        System.setOut(new PrintStream(buffer));
        try {
            renderer.setStage(ProgressRenderer.STAGE_OUTPUT);
            renderer.stageDone(42);
        } finally {
            System.setOut(original);
        }
        String output = buffer.toString();
        assertTrue(output.contains("[Output"), "stage in done line, got: " + output);
        assertTrue(output.contains("[##########] 100% 42/42"), "done bar, got: " + output);
        assertTrue(output.endsWith("00:00:00\n") || output.endsWith("00:00:00\r\n"),
                "newline-terminated, got: " + output);
    }

    @Test
    void stageDoneIsSilentWhenQuiet() {
        ProgressRenderer renderer = ProgressRenderer.disabled();
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        PrintStream original = System.out;
        System.setOut(new PrintStream(buffer));
        try {
            renderer.setStage(ProgressRenderer.STAGE_A_FLOW);
            renderer.stageDone(100);
        } finally {
            System.setOut(original);
        }
        assertEquals("", buffer.toString(), "quiet stageDone must not print");
    }

    private static int displayWidth(String value) {
        return value.codePoints().map(cp -> cp <= 0x7f ? 1 : 2).sum();
    }
}
