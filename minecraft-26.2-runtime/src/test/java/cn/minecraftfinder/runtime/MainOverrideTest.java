package cn.minecraftfinder.runtime;

import cn.minecraftfinder.core.AreaShape;
import cn.trialfinder.config.FinderConfig;
import cn.trialfinder.config.TrialSearchMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MainOverrideTest {

    private static FinderConfig base() {
        return new FinderConfig(
                0, 0, 0, 300_000, false, AreaShape.CIRCLE,
                128, AreaShape.CIRCLE, 2, 20, 8, 262_144, TrialSearchMode.AUTO, 512);
    }

    @Test
    void noArgsReturnsBase() {
        assertEquals(base(), Main.applyOverrides(base(), new String[0]));
        assertEquals(base(), Main.applyOverrides(base(), null));
    }

    @Test
    void overridesAllSupportedKeys() {
        FinderConfig overridden = Main.applyOverrides(base(), new String[]{
                "--seed", "123",
                "--search-center-x", "10",
                "--search-center-z", "-20",
                "--search-radius-blocks", "5000",
                "--full-world", "true",
                "--search-area-shape", "square",
                "--trial-cluster-radius-blocks", "256",
                "--trial-area-shape", "square",
                "--trial-min-structures", "3",
                "--trial-min-spawners", "30",
                "--scan-threads", "4",
                "--scan-shard-size-blocks", "131072",
                "--trial-search-mode", "exact",
                "--trial-prediction-calibration-structures", "0"
        });
        assertEquals(123, overridden.seed());
        assertEquals(10, overridden.searchCenterX());
        assertEquals(-20, overridden.searchCenterZ());
        assertEquals(5000, overridden.searchRadiusBlocks());
        assertEquals(true, overridden.fullWorld());
        assertEquals(AreaShape.SQUARE, overridden.searchAreaShape());
        assertEquals(256, overridden.clusterRadiusBlocks());
        assertEquals(AreaShape.SQUARE, overridden.areaShape());
        assertEquals(3, overridden.minStructures());
        assertEquals(30, overridden.minSpawners());
        assertEquals(4, overridden.scanThreads());
        assertEquals(131072, overridden.scanShardSizeBlocks());
        assertEquals(TrialSearchMode.EXACT, overridden.searchMode());
        assertEquals(0, overridden.predictionCalibrationStructures());
    }

    @Test
    void partialOverrideKeepsOtherFields() {
        FinderConfig overridden = Main.applyOverrides(base(), new String[]{
                "--seed", "99", "--search-radius-blocks", "10000"});
        assertEquals(99, overridden.seed());
        assertEquals(10000, overridden.searchRadiusBlocks());
        assertEquals(0, overridden.searchCenterX());
        assertEquals(128, overridden.clusterRadiusBlocks());
        assertEquals(TrialSearchMode.AUTO, overridden.searchMode());
    }

    @Test
    void unknownKeyThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> Main.applyOverrides(base(), new String[]{"--bogus", "1"}));
    }

    @Test
    void missingValueThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> Main.applyOverrides(base(), new String[]{"--seed"}));
    }

    @Test
    void noProgressFlagIsIgnoredByOverrides() {
        FinderConfig overridden = Main.applyOverrides(base(), new String[]{
                "--seed", "7", "--no-progress", "--search-radius-blocks", "9999"});
        assertEquals(7, overridden.seed());
        assertEquals(9999, overridden.searchRadiusBlocks());
    }

    @Test
    void withoutFlagRemovesNoProgress() {
        String[] clean = Main.withoutFlag(
                new String[]{"--no-progress", "query", "--coords", "1,2"}, "--no-progress");
        assertEquals(3, clean.length);
        assertEquals("query", clean[0]);
    }

    @Test
    void wantsHelpRecognizesHelpFlags() {
        assertTrue(Main.wantsHelp(new String[]{"--help"}));
        assertTrue(Main.wantsHelp(new String[]{"-h"}));
        assertTrue(Main.wantsHelp(new String[]{"help"}));
        assertTrue(Main.wantsHelp(new String[]{"--seed", "1", "--help"}));
        assertFalse(Main.wantsHelp(new String[]{"--seed", "1"}));
        assertFalse(Main.wantsHelp(null));
        assertFalse(Main.wantsHelp(new String[]{"query", "--coords", "1,2"}));
    }
}
