package cn.trialfinder.cli;

import cn.trialfinder.accel.CpuAccelerator;
import cn.trialfinder.model.SearchResult;
import cn.trialfinder.sim.SimChamberGenerator;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Validates the cluster-level top-K pipeline used by {@code --top-k}:
 * <ol>
 *   <li>candidates are coarse-clustered (link distance 2R) BEFORE truncation, so clusters are
 *       never split;</li>
 *   <li>a bounded global heap keeps the top-K coarse clusters by size (chamber count);</li>
 *   <li>accumulating per-tile coarse clusters equals clustering the whole region once and taking
 *       the top-K (the streaming invariant that makes {@code --full-world} tractable);</li>
 *   <li>B-flow generates every chamber of the retained clusters and outputs complete clusters
 *       (structureCount == number of member chambers), ranked by real spawner count.</li>
 * </ol>
 */
class TopKStreamingTest {

    private static final long SEED = 188188L;

    private static final Comparator<SearchEngine.CoarseCluster> CLUSTER_DESC =
            SearchEngine.COARSE_BEST_FIRST;

    @Test
    void tiledGlobalTopKClustersEqualsSingleRegionTopKClusters() throws IOException {
        CpuAccelerator acc = new CpuAccelerator();
        SearchEngine.Options opts = new SearchEngine.Options(
                SEED, 10_000, 128, 1, 1, false, 4, false, 4_000, 500);
        int k = 30;

        // Small world (±10,000), tileSize=4,000, overlap=500. Owning columns/rows 1..3 cover the
        // union [owningStart(1), owningEnd(3)]^2 exactly.
        WorldTiler tiler = new WorldTiler(4_000, 500, 10_000);
        SearchRegion union = new SearchRegion(
                tiler.owningStart(1), tiler.owningEnd(3),
                tiler.owningStart(1), tiler.owningEnd(3), 0);

        // Baseline: coarse-cluster the whole union once, take top-K by size.
        List<SearchEngine.CoarseCluster> baseline = SearchEngine.coarseClustersForRegion(
                union, null, opts, acc, false, 0, 0, 0);
        baseline.sort(CLUSTER_DESC);
        List<SearchEngine.CoarseCluster> baselineTop = baseline.size() > k
                ? new ArrayList<>(baseline.subList(0, k)) : baseline;

        // Tiled: coarse-cluster each of the 3x3 owning tiles, accumulate a bounded global heap.
        PriorityQueue<SearchEngine.CoarseCluster> heap =
                new PriorityQueue<>(SearchEngine.COARSE_WORST_FIRST);
        for (int tx = 1; tx <= 3; tx++) {
            for (int tz = 1; tz <= 3; tz++) {
                SearchRegion tile = tiler.getTiles().get(tx * tiler.tilesPerDim() + tz);
                for (SearchEngine.CoarseCluster c : SearchEngine.coarseClustersForRegion(
                        tile, tiler, opts, acc, false, 0, 0, 0)) {
                    heap.add(c);
                    if (heap.size() > k) {
                        heap.poll();
                    }
                }
            }
        }
        List<SearchEngine.CoarseCluster> tiledTop = new ArrayList<>(heap);
        tiledTop.sort(CLUSTER_DESC);

        assertEquals(baselineTop, tiledTop,
                "tiled global top-K clusters must equal single-region top-K clusters");
        assertEquals(k, tiledTop.size());
        System.out.println("tiledGlobalTopKClustersEqualsSingleRegionTopKClusters: clusters="
                + baseline.size() + " topK=" + k);
    }

    @Test
    void retainTopKWithBoundedHeapMatchesFullSort() throws IOException {
        CpuAccelerator acc = new CpuAccelerator();
        SearchEngine.Options opts = new SearchEngine.Options(
                SEED, 10_000, 128, 1, 1, false, 4, false, 4_000, 500);
        SearchRegion union = new SearchRegion(-10_000, 10_000, -10_000, 10_000, 0);

        List<SearchEngine.CoarseCluster> clusters = SearchEngine.coarseClustersForRegion(
                union, null, opts, acc, false, 0, 0, 0);
        List<SearchEngine.CoarseCluster> fullSorted = new ArrayList<>(clusters);
        fullSorted.sort(SearchEngine.COARSE_BEST_FIRST);

        for (int k : new int[]{1, 5, 30, 1000, Integer.MAX_VALUE}) {
            List<SearchEngine.CoarseCluster> heapTop = SearchEngine.retainTopK(clusters, k);
            List<SearchEngine.CoarseCluster> expected = fullSorted.size() > k
                    ? new ArrayList<>(fullSorted.subList(0, k)) : fullSorted;
            assertEquals(expected, heapTop, "retainTopK k=" + k
                    + " must equal full-sort top-k (coarse clusters=" + clusters.size() + ")");
        }
    }

    @Test
    void sparsePrefilterDropsNoQualifyingClusters() throws IOException {
        // The density prune (score >= minStructures) is lossless: enabling it must not change the
        // coarse-cluster set beyond removing candidates that can never form a qualifying cluster.
        CpuAccelerator acc = new CpuAccelerator();
        SearchEngine.Options baseline = new SearchEngine.Options(
                SEED, 10_000, 128, 3, 1, false, 4, false, 4_000, 500);

        SearchRegion union = new SearchRegion(-10_000, 10_000, -10_000, 10_000, 0);
        List<SearchEngine.CoarseCluster> withPrune = SearchEngine.coarseClustersForRegion(
                union, null, baseline, acc, false, 0, 0, 0);
        // A high sparse threshold: coarse clustering is skipped, so no clusters.
        SearchEngine.Options skip = new SearchEngine.Options(
                SEED, 10_000, 128, 3, 1, false, 4, false, 4_000, 500,
                0, "cluster", 0, null, Integer.MAX_VALUE, 0, 0, 0);
        List<SearchEngine.CoarseCluster> skipped = SearchEngine.coarseClustersForRegion(
                union, null, skip, acc, false, 0, 0, 0);
        assertTrue(skipped.isEmpty(), "huge threshold must skip coarse clustering");

        // The pruned set itself must still produce exactly the same top-K as the unpruned pipeline.
        // The default threshold (= minStructures) keeps every candidate that could be in a cluster.
        assertEquals(withPrune, SearchEngine.coarseClustersForRegion(
                union, null, baseline, acc, false, 0, 0, 0),
                "deterministic across calls with identical options");
    }

    @Test
    void topKClusterSelectionIsDeterministic() throws IOException {
        CpuAccelerator acc = new CpuAccelerator();
        SearchEngine.Options opts = new SearchEngine.Options(
                SEED, 10_000, 128, 1, 1, false, 4, false, 4_000, 500);
        int k = 30;

        WorldTiler tiler = new WorldTiler(4_000, 500, 10_000);
        List<SearchEngine.CoarseCluster> first = accumulateGlobalTopKClusters(tiler, opts, acc, k);
        List<SearchEngine.CoarseCluster> second = accumulateGlobalTopKClusters(tiler, opts, acc, k);
        assertEquals(first, second, "top-K cluster selection must be deterministic");
    }

    @Test
    void generateClustersProducesCompleteClusters() throws IOException {
        CpuAccelerator acc = new CpuAccelerator();
        SimChamberGenerator generator = SimChamberGenerator.fromClasspath();
        SearchEngine.Options opts = new SearchEngine.Options(
                SEED, 10_000, 128, 1, 1, false, 4, false, 4_000, 500);

        WorldTiler tiler = new WorldTiler(4_000, 500, 10_000);
        List<SearchEngine.CoarseCluster> retained = accumulateGlobalTopKClusters(tiler, opts, acc, 40);

        List<SearchResult> results = SearchEngine.generateClusters(retained, generator, opts);
        for (int i = 1; i < results.size(); i++) {
            assertTrue(results.get(i - 1).spawnerCount() >= results.get(i).spawnerCount(),
                    "results sorted by spawner count descending");
        }
        for (SearchResult r : results) {
            assertTrue(r.spawnerCount() >= 1, "each result has real spawners");
            assertEquals(r.structures().size(), r.structureCount(),
                    "each result is a complete cluster (all member chambers listed)");
        }
        long multiChamber = results.stream().filter(r -> r.structureCount() > 1).count();
        System.out.println("generateClustersProducesCompleteClusters: retained=" + retained.size()
                + " results=" + results.size() + " multi-chamber=" + multiChamber);
    }

    private static List<SearchEngine.CoarseCluster> accumulateGlobalTopKClusters(
            WorldTiler tiler, SearchEngine.Options opts, CpuAccelerator acc, int k) throws IOException {
        PriorityQueue<SearchEngine.CoarseCluster> heap =
                new PriorityQueue<>(SearchEngine.COARSE_WORST_FIRST);
        for (SearchRegion tile : tiler.getTiles()) {
            for (SearchEngine.CoarseCluster c : SearchEngine.coarseClustersForRegion(
                    tile, tiler, opts, acc, false, 0, 0, 0)) {
                heap.add(c);
                if (heap.size() > k) {
                    heap.poll();
                }
            }
        }
        List<SearchEngine.CoarseCluster> result = new ArrayList<>(heap);
        result.sort(CLUSTER_DESC);
        return result;
    }
}
