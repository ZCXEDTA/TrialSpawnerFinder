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
 *   top-k          = max(50, min(5000, searchRadius / 100))
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
    void tunesClusterRadiusGridAndTopKFromRadius() throws Exception {
        TrialFinderCLI cli = parseAndTune("--seed", "188188", "--search-radius", "100000");
        assertEquals(256, cli.clusterRadius, "100000/200 = 500 -> clamped to 256");
        assertEquals(512, cli.gridSize, "grid = 2 * cluster-radius");
        assertEquals(1000, cli.topK, "100000/100 = 1000");
    }

    @Test
    void tunesSmallRadius() throws Exception {
        TrialFinderCLI cli = parseAndTune("--seed", "188188", "--search-radius", "10000");
        assertEquals(64, cli.clusterRadius, "10000/200 = 50 -> clamped to 64");
        assertEquals(128, cli.gridSize);
        assertEquals(100, cli.topK, "10000/100 = 100");
    }

    @Test
    void tunesTinyRadiusToMinimumTopK() throws Exception {
        // 3000/100 = 30 -> clamped to the 50 floor.
        TrialFinderCLI cli = parseAndTune("--seed", "188188", "--search-radius", "3000");
        assertEquals(50, cli.topK, "3000/100 = 30 -> clamped to 50");
    }

    @Test
    void tunesLargeRadiusToMaximums() throws Exception {
        TrialFinderCLI cli = parseAndTune("--seed", "188188", "--search-radius", "1000000");
        assertEquals(256, cli.clusterRadius, "1e6/200 = 5000 -> clamped to 256");
        assertEquals(5000, cli.topK, "1e6/100 = 10000 -> clamped to 5000");
    }

    @Test
    void respectsExplicitClusterRadius() throws Exception {
        TrialFinderCLI cli = parseAndTune(
                "--seed", "188188", "--search-radius", "100000", "--cluster-radius", "300");
        assertEquals(300, cli.clusterRadius, "explicit cluster-radius must not be overridden");
        // grid/top-k still auto-tuned.
        assertEquals(600, cli.gridSize);
        assertEquals(1000, cli.topK);
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
