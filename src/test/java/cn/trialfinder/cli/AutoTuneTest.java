package cn.trialfinder.cli;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the {@code --auto-tune} parameter tuning in {@link TrialFinderCLI}. The formula:
 * <pre>
 *   cluster-radius = max(64, min(256, searchRadius / 200))
 *   grid-size      = 2 * cluster-radius
 *   top-k          = stays 0 (disabled) for maximum precision — never auto-tuned
 * </pre>
 * Explicit CLI values, {@code --no-auto-tune} and {@code --full-world} must be respected.
 */
class AutoTuneTest {

    /** Invokes the package-private {@code applyAutoTune} after parsing args. */
    private static TrialFinderCLI parseAndTune(String... args) throws Exception {
        TrialFinderCLI cli = new TrialFinderCLI();
        new CommandLine(cli).parseArgs(args);
        Method tune = TrialFinderCLI.class.getDeclaredMethod("applyAutoTune");
        tune.setAccessible(true);
        tune.invoke(cli);
        return cli;
    }

    @Test
    void tunesClusterRadiusGridFromRadius() throws Exception {
        TrialFinderCLI cli = parseAndTune("--seed", "188188", "--search-radius", "100000");
        assertEquals(256, cli.clusterRadius, "100000/200 = 500 -> clamped to 256");
        assertEquals(512, cli.gridSize, "grid = 2 * cluster-radius");
        assertEquals(0, cli.topK, "top-k must stay disabled (no auto-tune)");
    }

    @Test
    void tunesSmallRadius() throws Exception {
        TrialFinderCLI cli = parseAndTune("--seed", "188188", "--search-radius", "10000");
        assertEquals(64, cli.clusterRadius, "10000/200 = 50 -> clamped to 64");
        assertEquals(128, cli.gridSize);
        assertEquals(0, cli.topK, "top-k must stay disabled");
    }

    @Test
    void topKNeverAutoTuned() throws Exception {
        // Even a tiny radius must not have top-k auto-tuned; it stays 0 (full precision).
        TrialFinderCLI cli = parseAndTune("--seed", "188188", "--search-radius", "3000");
        assertEquals(0, cli.topK, "top-k must stay 0 (disabled) regardless of radius");
    }

    @Test
    void tunesLargeRadiusToMaximums() throws Exception {
        TrialFinderCLI cli = parseAndTune("--seed", "188188", "--search-radius", "1000000");
        assertEquals(256, cli.clusterRadius, "1e6/200 = 5000 -> clamped to 256");
        assertEquals(0, cli.topK, "top-k must stay disabled for large radii too");
    }

    @Test
    void largeRadiusKeepsClusterPrefilterByDefault() throws Exception {
        // Grid prefilter must never be auto-selected: a large radius keeps the default cluster
        // prefilter unless the user explicitly opts in via --prefilter-mode grid.
        TrialFinderCLI cli = parseAndTune("--seed", "188188", "--search-radius", "1000000");
        assertEquals("cluster", cli.prefilterMode,
                "large radius must keep the default cluster prefilter");
    }

    @Test
    void respectsExplicitPrefilterMode() throws Exception {
        TrialFinderCLI cli = parseAndTune(
                "--seed", "188188", "--search-radius", "1000000", "--prefilter-mode", "grid");
        assertEquals("grid", cli.prefilterMode,
                "explicit --prefilter-mode grid must be kept");
    }

    @Test
    void respectsExplicitClusterRadius() throws Exception {
        TrialFinderCLI cli = parseAndTune(
                "--seed", "188188", "--search-radius", "100000", "--cluster-radius", "300");
        assertEquals(300, cli.clusterRadius, "explicit cluster-radius must not be overridden");
        // grid still auto-tuned from the explicit cluster-radius; top-k stays disabled.
        assertEquals(600, cli.gridSize);
        assertEquals(0, cli.topK);
    }

    @Test
    void respectsExplicitTopKAndGridSize() throws Exception {
        TrialFinderCLI cli = parseAndTune(
                "--seed", "188188", "--search-radius", "100000", "--top-k", "5", "--grid-size", "100");
        assertEquals(256, cli.clusterRadius);
        assertEquals(100, cli.gridSize, "explicit grid-size must not be overridden");
        assertEquals(5, cli.topK, "explicit top-k must not be overridden");
    }

    @Test
    void noAutoTuneKeepsDefaults() throws Exception {
        TrialFinderCLI cli = parseAndTune(
                "--seed", "188188", "--search-radius", "100000", "--no-auto-tune");
        assertEquals(1000, cli.clusterRadius, "default cluster-radius kept without auto-tune");
        assertEquals(0, cli.topK, "default top-k (disabled) kept");
        assertEquals(0, cli.gridSize, "default grid-size (auto) kept");
    }

    @Test
    void fullWorldSkipsAutoTune() throws Exception {
        TrialFinderCLI cli = parseAndTune("--seed", "188188", "--full-world");
        assertEquals(1000, cli.clusterRadius, "full-world must not auto-tune cluster-radius");
        assertEquals(0, cli.topK, "full-world must not auto-tune top-k");
    }

    @Test
    void autoTuneFlagDefaultsToEnabled() throws Exception {
        TrialFinderCLI cli = new TrialFinderCLI();
        assertTrue(cli.autoTune, "--auto-tune defaults to enabled");
    }
}
