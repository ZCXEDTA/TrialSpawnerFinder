package cn.minecraftfinder.runtime;

import cn.minecraftfinder.core.ProgressUpdate;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgressEventReporterTest {
    @Test
    void showsInitialCoarseScanBeforeOnePercentCompletes() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ProgressEventReporter reporter = new ProgressEventReporter(
                new PrintStream(bytes, true, StandardCharsets.UTF_8));

        reporter.report(ProgressUpdate.estimated(
                "粗筛", 0, 10_000, "个", 0, 2_000_000));

        ProgressEvent event = events(bytes).getFirst();
        assertEquals("粗筛", event.phase());
        assertFalse(event.complete());
        assertTrue(event.line().contains("0% 0/2000000"));
        assertTrue(event.line().contains("ETA 00:00:00"));
    }

    @Test
    void emitsIndependentEventsWithoutTerminalControlCodes() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ProgressEventReporter reporter = new ProgressEventReporter(
                new PrintStream(bytes, true, StandardCharsets.UTF_8));

        reporter.report(ProgressUpdate.estimated(
                "总进度", 0, 10, "个", 0, 1_000));
        reporter.report(ProgressUpdate.estimated(
                "粗筛", 0, 10, "个", 0, 1_000));

        List<ProgressEvent> events = events(bytes);
        assertEquals(List.of("总进度", "粗筛"),
                events.stream().map(ProgressEvent::phase).toList());
        assertFalse(bytes.toString(StandardCharsets.UTF_8).contains("\u001B"));
    }

    @Test
    void marksTheFinalEventComplete() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ProgressEventReporter reporter = new ProgressEventReporter(
                new PrintStream(bytes, true, StandardCharsets.UTF_8));

        reporter.report(ProgressUpdate.phase("总进度", 1, 2, "片"), "候选 10；聚类 2");
        reporter.report(ProgressUpdate.phase("总进度", 2, 2, "片"), "候选 20；聚类 3");
        List<ProgressEvent> events = events(bytes);
        assertFalse(events.getFirst().complete());
        assertTrue(events.getLast().complete());
        assertTrue(events.getLast().line().contains("候选 20；聚类 3"));
    }

    private static List<ProgressEvent> events(ByteArrayOutputStream bytes) {
        return bytes.toString(StandardCharsets.UTF_8).lines()
                .map(ProgressEventReporterTest::decode)
                .toList();
    }

    private static ProgressEvent decode(String encoded) {
        String[] parts = encoded.split("\\|", 4);
        assertEquals("@@MFP1", parts[0]);
        return new ProgressEvent(
                decodeBase64(parts[1]), "1".equals(parts[2]), decodeBase64(parts[3]));
    }

    private static String decodeBase64(String encoded) {
        return new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
    }

    private record ProgressEvent(String phase, boolean complete, String line) {
    }
}
