package cn.trialfinder.search;

import cn.minecraftfinder.core.AreaShape;
import cn.minecraftfinder.core.ProgressUpdate;
import cn.trialfinder.config.FinderConfig;
import cn.trialfinder.config.TrialSearchMode;
import cn.minecraftfinder.core.BlockPoint;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShardedClusterScannerTest {
    @Test
    void reportsEstimatedCandidateProgress() {
        List<ProgressUpdate> progress = new java.util.ArrayList<>();
        FinderConfig config = new FinderConfig(
                0, 0, 0, 1_000, false, AreaShape.CIRCLE,
                128, AreaShape.CIRCLE, 1, 0, 1, 2_000, TrialSearchMode.AUTO);

        ShardedClusterScanner.scan(config, progress::add);

        assertEquals(0, progress.getFirst().completed());
        assertTrue(progress.getFirst().hasEstimatedWork());
        assertEquals("粗筛", progress.getLast().phase());
    }

    @Test
    void candidateEstimateUsesTrialChamberRegionDensity() {
        FinderConfig circle = new FinderConfig(
                0, 0, 0, 1_000_000, false, AreaShape.CIRCLE,
                128, AreaShape.CIRCLE, 3, 20, 8, 262_144, TrialSearchMode.AUTO);
        FinderConfig world = new FinderConfig(
                0, 0, 0, 1, true, AreaShape.CIRCLE,
                128, AreaShape.CIRCLE, 3, 20, 8, 262_144, TrialSearchMode.AUTO);

        assertEquals(10_615_784, ShardedClusterScanner.estimatedCandidateCount(circle));
        assertEquals(12_164_792_388L, ShardedClusterScanner.estimatedCandidateCount(world));
    }

    @Test
    void circleShardsMatchWholeAreaAcrossBoundaries() {
        assertMatchesWholeArea(AreaShape.CIRCLE);
    }

    @Test
    void squareShardsMatchWholeAreaAcrossBoundaries() {
        assertMatchesWholeArea(AreaShape.SQUARE);
    }

    @Test
    void batchApiMatchesCompatibilityResult() {
        FinderConfig config = new FinderConfig(
                9_206_294_873_968_313_284L, 137, -219, 48_000,
                false, AreaShape.CIRCLE, 256, AreaShape.CIRCLE, 2, 0, 3, 262_144,
                TrialSearchMode.AUTO);
        ShardedClusterScanner.ScanResult expected = ShardedClusterScanner.scan(config);
        List<CircleClusters.StructureCluster> streamed = new java.util.ArrayList<>();
        List<Integer> shardOrder = new java.util.ArrayList<>();

        ShardedClusterScanner.ScanSummary summary = ShardedClusterScanner.scanBatches(
                config, ignored -> { }, batch -> {
                    shardOrder.add(batch.shardIndex());
                    streamed.addAll(batch.clusters());
                });

        assertEquals(expected.candidateCount(), summary.candidateCount());
        assertEquals(expected.clusters().stream().map(ShardedClusterScannerTest::key).sorted().toList(),
                streamed.stream().map(ShardedClusterScannerTest::key).sorted().toList());
        assertTrue(summary.shardCount() > 1);
        assertEquals(java.util.stream.IntStream.range(0, summary.shardCount()).boxed().toList(),
                shardOrder);
    }

    @Test
    void automaticallySizesShardsFromRangeAndThreads() {
        FinderConfig oneThread = config(10_000, 1, 2_000);
        FinderConfig eightThreads = config(10_000, 8, 262_144);
        FinderConfig largeRange = config(1_000_000, 8, 2_000);

        assertTrue(ShardedClusterScanner.processingShardSizeBlocks(eightThreads)
                < ShardedClusterScanner.processingShardSizeBlocks(oneThread));
        assertEquals(ShardedClusterScanner.processingShardSizeBlocks(eightThreads),
                ShardedClusterScanner.processingShardSizeBlocks(config(10_000, 8, 2_000)));
        assertEquals(32_768, ShardedClusterScanner.processingShardSizeBlocks(largeRange));
    }

    @Test
    void automaticShardSizesProduceTheSameClusters() {
        FinderConfig oneThread = config(12_000, 1, 262_144);
        FinderConfig eightThreads = config(12_000, 8, 262_144);

        ShardedClusterScanner.ScanResult first = ShardedClusterScanner.scan(oneThread);
        ShardedClusterScanner.ScanResult second = ShardedClusterScanner.scan(eightThreads);

        assertEquals(first.candidateCount(), second.candidateCount());
        assertEquals(first.clusters().stream().map(ShardedClusterScannerTest::key).sorted().toList(),
                second.clusters().stream().map(ShardedClusterScannerTest::key).sorted().toList());
    }

    @Test
    void scansTenMillionCandidatesWithoutAccumulatingAllBatches() {
        FinderConfig config = new FinderConfig(
                0, 0, 0, 1_000_000, false, AreaShape.CIRCLE,
                128, AreaShape.CIRCLE, 1, 0, 8, 262_144, TrialSearchMode.AUTO);
        AtomicInteger maximumBatchClusters = new AtomicInteger();

        ShardedClusterScanner.ScanSummary summary = ShardedClusterScanner.scanBatches(
                config, ignored -> { }, batch -> maximumBatchClusters.accumulateAndGet(
                        batch.clusters().size(), Math::max));

        assertTrue(summary.candidateCount() > 10_000_000);
        assertTrue(summary.candidateCount() < 11_000_000);
        assertEquals(3_844, summary.shardCount());
        assertTrue(maximumBatchClusters.get() < 10_000);
    }

    private static FinderConfig config(int radius, int threads, int legacyShardSize) {
        return new FinderConfig(
                9_206_294_873_968_313_284L, 0, 0, radius,
                false, AreaShape.CIRCLE, 128, AreaShape.CIRCLE,
                1, 0, threads, legacyShardSize, TrialSearchMode.AUTO);
    }

    private static void assertMatchesWholeArea(AreaShape shape) {
        FinderConfig config = new FinderConfig(
                9_206_294_873_968_313_284L, 137, -219, 12_000,
                false, AreaShape.CIRCLE, 256, shape, 2, 0, 3, 2_000,
                TrialSearchMode.AUTO);
        List<BlockPoint> points = TrialChamberCandidates.enumerate(config);
        List<CircleClusters.StructureCluster> expected = switch (shape) {
            case CIRCLE -> CircleClusters.find(points, 256, 2);
            case SQUARE -> SquareClusters.find(points, 256, 2);
        };

        ShardedClusterScanner.ScanResult actual = ShardedClusterScanner.scan(config);

        assertEquals(points.size(), actual.candidateCount());
        TreeSet<String> expectedMembers = new TreeSet<>(expected.stream()
                .map(cluster -> cluster.structures().toString()).toList());
        TreeSet<String> actualMembers = new TreeSet<>(actual.clusters().stream()
                .map(cluster -> cluster.structures().toString()).toList());
        TreeSet<String> missing = new TreeSet<>(expectedMembers);
        missing.removeAll(actualMembers);
        TreeSet<String> extra = new TreeSet<>(actualMembers);
        extra.removeAll(expectedMembers);
        assertEquals("missing=none, extra=none",
                "missing=" + missing.stream().findFirst().orElse("none")
                        + ", extra=" + extra.stream().findFirst().orElse("none"));
        assertEquals(expected.stream().map(ShardedClusterScannerTest::key).sorted().toList(),
                actual.clusters().stream().map(ShardedClusterScannerTest::key).sorted().toList());
    }

    private static String key(CircleClusters.StructureCluster cluster) {
        return cluster.center().roundedX() + "," + cluster.center().roundedZ() + ":"
                + cluster.structures();
    }
}
