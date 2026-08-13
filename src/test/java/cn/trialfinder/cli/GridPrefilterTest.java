package cn.trialfinder.cli;

import cn.trialfinder.accel.CpuAccelerator;
import cn.trialfinder.model.SearchResult;
import org.junit.jupiter.api.Test;

import java.io.PrintStream;
import java.io.OutputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that {@code --prefilter-mode grid} keeps the high-density regions that the exact
 * cluster pipeline reports: the top results of the baseline (cluster) search must be recalled by
 * the grid-prefilter search within a small distance. Grid prefilter is coarser by design, so the
 * recall is asserted to be "high" (>= 60% of the top 10) rather than exact.
 */
class GridPrefilterTest {

    private static final long SEED = 188188L;
    private static final PrintStream NULL_OUT = new PrintStream(OutputStream.nullOutputStream());

    @Test
    void gridPrefilterRecallsTopClusterResults() throws Exception {
        CpuAccelerator acc = new CpuAccelerator();

        // Baseline: exact cluster pipeline (topK=0, prefilter=cluster).
        SearchEngine.Options clusterOpts = new SearchEngine.Options(
                SEED, 3000, 128, 1, 1, false, 4, false, 100_000, 1_000,
                0, "cluster", 0);
        SearchEngine.Result baseline = SearchEngine.run(clusterOpts, acc, NULL_OUT);
        List<SearchResult> top = baseline.results().stream().limit(10).toList();
        assertTrue(!top.isEmpty(), "baseline should produce results");

        // Grid prefilter: keep the top-60 cells of side 512 blocks. With 96 candidates spanning
        // 6000 blocks there are ~12x12 cells; 60 cells keeps most high-density cells while still
        // demonstrating pruning. Grid prefilter is coarser than cluster by design.
        SearchEngine.Options gridOpts = new SearchEngine.Options(
                SEED, 3000, 128, 1, 1, false, 4, false, 100_000, 1_000,
                60, "grid", 512);
        SearchEngine.Result grid = SearchEngine.runGrid(gridOpts, acc, NULL_OUT);

        int recalled = 0;
        for (SearchResult result : top) {
            for (SearchResult candidate : grid.results()) {
                long dx = candidate.centerX() - result.centerX();
                long dz = candidate.centerZ() - result.centerZ();
                if (dx * dx + dz * dz <= 512L * 512L) {
                    recalled++;
                    break;
                }
            }
        }
        double recall = (double) recalled / top.size();
        System.out.println("gridPrefilterRecallsTopClusterResults: baseline top=" + top.size()
                + " gridResults=" + grid.results().size() + " recalled=" + recalled
                + " recall=" + String.format("%.2f", recall));
        assertTrue(recall >= 0.6,
                "grid prefilter must recall >= 60% of the top baseline results, got " + recall);
    }

    @Test
    void gridPrefilterRetainsFewerCandidatesThanRaw() throws Exception {
        CpuAccelerator acc = new CpuAccelerator();
        // Small top-K forces real pruning: keep only the top-10 cells.
        SearchEngine.Options gridOpts = new SearchEngine.Options(
                SEED, 3000, 128, 1, 1, false, 4, false, 100_000, 1_000,
                10, "grid", 512);
        SearchEngine.Result grid = SearchEngine.runGrid(gridOpts, acc, NULL_OUT);
        int raw = grid.candidateCount();
        int retained = raw - grid.prunedCount();
        assertTrue(raw > 0, "grid mode should enumerate candidates");
        assertTrue(retained > 0, "retained candidates must be positive");
        assertTrue(retained < raw, "grid prefilter should prune with a small top-K, raw=" + raw
                + " retained=" + retained);
        System.out.println("gridPrefilterRetainsFewerCandidates: candidates=" + raw
                + " pruned=" + grid.prunedCount() + " retained=" + retained);
    }
}
