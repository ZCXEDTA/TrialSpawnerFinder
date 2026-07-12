package cn.trialfinder.search;

import cn.minecraftfinder.core.AreaShape;
import cn.trialfinder.config.FinderConfig;
import cn.minecraftfinder.core.BlockPoint;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShardedClusterScannerTest {
    @Test
    void progressShowsThroughputAndEstimatedTime() {
        String progress = ShardedClusterScanner.progressLine(
                25, 100, 500_000, 2_000_000, 10_000_000_000L);

        assertEquals("[##--------] 25% 25/100 | 500.0k | 50.0k/s | ETA 00:00:30", progress);
    }

    @Test
    void candidateEstimateUsesTrialChamberRegionDensity() {
        FinderConfig circle = new FinderConfig(
                0, 0, 0, 1_000_000, false, 128, AreaShape.CIRCLE, 3, 20, 8, 262_144);
        FinderConfig world = new FinderConfig(
                0, 0, 0, 1, true, 128, AreaShape.CIRCLE, 3, 20, 8, 262_144);

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

    private static void assertMatchesWholeArea(AreaShape shape) {
        FinderConfig config = new FinderConfig(
                9_206_294_873_968_313_284L, 137, -219, 12_000,
                false, 256, shape, 2, 0, 3, 2_000);
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
