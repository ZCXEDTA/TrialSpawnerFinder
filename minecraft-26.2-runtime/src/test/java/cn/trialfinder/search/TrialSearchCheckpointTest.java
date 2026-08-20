package cn.trialfinder.search;

import cn.minecraftfinder.core.AreaShape;
import cn.minecraftfinder.core.BlockPoint;
import cn.trialfinder.config.FinderConfig;
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
        FinderConfig config = config();
        Path output = directory.resolve("results.csv");
        TrialSearchCheckpoint checkpoint = TrialSearchCheckpoint.open(
                config, output, directory);
        SearchResult result = new SearchResult(
                10, 20, 1, 30, List.of(new BlockPoint(8, 24)));
        TrialSearchCheckpoint.Statistics statistics =
                new TrialSearchCheckpoint.Statistics(11, 12, 13, 14);
        List<BlockPoint> source = List.of(new BlockPoint(8, 24), new BlockPoint(40, 56));

        checkpoint.commit(7, List.of(result), Map.of(result, source), statistics);
        TrialSearchCheckpoint restored = TrialSearchCheckpoint.open(
                config, directory.resolve("different.csv"), directory);

        assertEquals(output, restored.output());
        assertEquals(List.of(result), restored.results());
        assertTrue(restored.isCompleted(7));
        assertFalse(restored.isCompleted(6));
        assertEquals(statistics, restored.statistics());
        assertEquals(Map.of(result, source), restored.resultSources());
    }

    @Test
    void resumePreservesCompletedShards() throws Exception {
        FinderConfig config = config();
        TrialSearchCheckpoint first = TrialSearchCheckpoint.open(
                config, directory.resolve("results.csv"), directory);
        first.commit(2, List.of(), Map.of(),
                new TrialSearchCheckpoint.Statistics(1, 2, 3, 4));

        TrialSearchCheckpoint restored = TrialSearchCheckpoint.open(
                config, directory.resolve("results.csv"), directory);

        assertTrue(restored.isCompleted(2));
    }

    private static FinderConfig config() {
        return new FinderConfig(
                1, 0, 0, 10_000, false, AreaShape.CIRCLE,
                128, AreaShape.CIRCLE, 1, 20, 8, 262_144);
    }
}
