package cn.trialfinder.search;

import cn.minecraftfinder.core.AreaShape;
import cn.minecraftfinder.core.BlockPoint;
import cn.trialfinder.config.FinderConfig;
import cn.trialfinder.config.TrialSearchMode;
import cn.trialfinder.model.SearchResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrialSearchCheckpointTest {
    @TempDir
    Path directory;

    @Test
    void restoresCompletedShardsOutputAndResults() throws Exception {
        FinderConfig config = config(TrialSearchMode.AUTO);
        Path output = directory.resolve("results.csv");
        TrialSearchCheckpoint checkpoint = TrialSearchCheckpoint.open(
                config, output, true, directory);
        SearchResult result = new SearchResult(
                10, 20, 1, 30, List.of(new BlockPoint(8, 24)));
        TrialSearchCheckpoint.Statistics statistics =
                new TrialSearchCheckpoint.Statistics(11, 12, 13, 14, 15);
        List<BlockPoint> source = List.of(new BlockPoint(8, 24), new BlockPoint(40, 56));

        checkpoint.commit(7, List.of(result), Map.of(result, source), statistics);
        TrialSearchCheckpoint restored = TrialSearchCheckpoint.open(
                config, directory.resolve("different.csv"), true, directory);

        assertEquals(output, restored.output());
        assertEquals(List.of(result), restored.results());
        assertTrue(restored.isCompleted(7));
        assertFalse(restored.isCompleted(6));
        assertTrue(restored.predictionEnabled());
        assertEquals(statistics, restored.statistics());
        assertEquals(Map.of(result, source), restored.resultSources());
    }

    @Test
    void preservesExactFallbackAcrossResume() throws Exception {
        FinderConfig config = config(TrialSearchMode.AUTO);
        TrialSearchCheckpoint fallback = TrialSearchCheckpoint.open(
                config, directory.resolve("results.csv"), false, directory);
        fallback.commit(2, List.of(), Map.of(),
                new TrialSearchCheckpoint.Statistics(1, 2, 3, 4, 5));

        TrialSearchCheckpoint restored = TrialSearchCheckpoint.open(
                config, directory.resolve("results.csv"), true, directory);

        assertFalse(restored.predictionEnabled());
        assertTrue(restored.isCompleted(2));
    }

    @Test
    void searchModeUsesDifferentCheckpointFingerprint() {
        assertFalse(TrialSearchCheckpoint.fingerprint(config(TrialSearchMode.AUTO))
                .equals(TrialSearchCheckpoint.fingerprint(config(TrialSearchMode.EXACT))));
    }

    private static FinderConfig config(TrialSearchMode mode) {
        return new FinderConfig(
                1, 0, 0, 10_000, false, AreaShape.CIRCLE,
                128, AreaShape.CIRCLE, 1, 20, 8, 262_144, mode);
    }
}
