package cn.minecraftfinder.runtime;

import cn.minecraftfinder.core.ProgressUpdate;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsoleProgressReporterTest {
    @Test
    void rewritesProgressInPlaceAndFinishesWithNewline() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ConsoleProgressReporter reporter = new ConsoleProgressReporter(
                new PrintStream(bytes, true, StandardCharsets.UTF_8));

        reporter.report(ProgressUpdate.phase("总进度", 1, 2, "片"), "候选 10；聚类 2");
        String running = bytes.toString(StandardCharsets.UTF_8);
        assertTrue(running.startsWith("\r[总进度"));
        assertTrue(running.contains("候选 10；聚类 2"));
        assertFalse(running.endsWith(System.lineSeparator()));

        reporter.report(ProgressUpdate.phase("总进度", 2, 2, "片"), "候选 20；聚类 3");
        assertTrue(bytes.toString(StandardCharsets.UTF_8).endsWith(System.lineSeparator()));
    }
}
