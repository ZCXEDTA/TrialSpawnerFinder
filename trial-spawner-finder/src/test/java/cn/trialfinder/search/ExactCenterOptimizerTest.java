package cn.trialfinder.search;

import cn.minecraftfinder.core.AreaShape;
import cn.minecraftfinder.core.BlockPoint;
import cn.trialfinder.model.SpawnerPoint;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExactCenterOptimizerTest {
    @Test
    void circleFindsBestCenterFarBeyondOldTwoBlockSearch() {
        ExactCenterOptimizer.CenterScore score = ExactCenterOptimizer.find(
                AreaShape.CIRCLE, 128,
                List.of(new BlockPoint(0, 0)),
                List.of(new SpawnerPoint(200, 0, 0)));

        assertEquals(new ExactCenterOptimizer.CenterScore(72, 0, 1), score);
    }

    @Test
    void circleMatchesBruteForceForMultipleStructuresAndSpawners() {
        assertMatchesBruteForce(AreaShape.CIRCLE);
    }

    @Test
    void squareMatchesBruteForceForMultipleStructuresAndSpawners() {
        assertMatchesBruteForce(AreaShape.SQUARE);
    }

    @Test
    void squareLargeRadiusUsesBoundedFallback() {
        ExactCenterOptimizer.CenterScore score = ExactCenterOptimizer.find(
                AreaShape.SQUARE, 600,
                List.of(new BlockPoint(0, 0)),
                List.of(new SpawnerPoint(600, 0, 0)));

        assertEquals(new ExactCenterOptimizer.CenterScore(0, -600, 1), score);
    }

    @Test
    void circleLookupMatchesDirectDistanceChecksAcrossRandomInputs() {
        Random random = new Random(0x5EEDC0DE);
        for (int iteration = 0; iteration < 100; iteration++) {
            int radius = 1 + random.nextInt(40);
            int baseX = random.nextInt(81) - 40;
            int baseZ = random.nextInt(81) - 40;
            int spread = radius / 3;
            List<BlockPoint> structures = new ArrayList<>();
            for (int i = 0, count = 1 + random.nextInt(5); i < count; i++) {
                structures.add(new BlockPoint(
                        baseX + (spread == 0 ? 0 : random.nextInt(spread * 2 + 1) - spread),
                        baseZ + (spread == 0 ? 0 : random.nextInt(spread * 2 + 1) - spread)));
            }
            List<SpawnerPoint> spawners = new ArrayList<>();
            for (int i = 0, count = random.nextInt(20); i < count; i++) {
                spawners.add(new SpawnerPoint(
                        baseX + random.nextInt(radius * 3 + 1) - radius * 3 / 2,
                        random.nextInt(80) - 40,
                        baseZ + random.nextInt(radius * 3 + 1) - radius * 3 / 2));
            }

            assertEquals(referenceFind(AreaShape.CIRCLE, radius, structures, spawners),
                    ExactCenterOptimizer.find(AreaShape.CIRCLE, radius, structures, spawners),
                    "iteration " + iteration);
        }
    }

    @Test
    void squareDifferenceGridMatchesDirectChecksAcrossRandomInputs() {
        Random random = new Random(0x51A7E2026L);
        for (int iteration = 0; iteration < 200; iteration++) {
            int radius = 1 + random.nextInt(40);
            int baseX = random.nextInt(81) - 40;
            int baseZ = random.nextInt(81) - 40;
            int spread = radius / 2;
            List<BlockPoint> structures = new ArrayList<>();
            for (int i = 0, count = 1 + random.nextInt(5); i < count; i++) {
                structures.add(new BlockPoint(
                        baseX + (spread == 0 ? 0 : random.nextInt(spread * 2 + 1) - spread),
                        baseZ + (spread == 0 ? 0 : random.nextInt(spread * 2 + 1) - spread)));
            }
            List<SpawnerPoint> spawners = new ArrayList<>();
            for (int i = 0, count = random.nextInt(80); i < count; i++) {
                spawners.add(new SpawnerPoint(
                        baseX + random.nextInt(radius * 4 + 1) - radius * 2,
                        random.nextInt(80) - 40,
                        baseZ + random.nextInt(radius * 4 + 1) - radius * 2));
            }

            assertEquals(referenceFind(AreaShape.SQUARE, radius, structures, spawners),
                    ExactCenterOptimizer.find(AreaShape.SQUARE, radius, structures, spawners),
                    "iteration " + iteration);
        }
    }

    private static void assertMatchesBruteForce(AreaShape shape) {
        int radius = 12;
        List<BlockPoint> structures = List.of(
                new BlockPoint(-7, -2), new BlockPoint(8, 3));
        List<SpawnerPoint> spawners = List.of(
                new SpawnerPoint(-15, 0, -4), new SpawnerPoint(2, 0, 11),
                new SpawnerPoint(17, 0, 5), new SpawnerPoint(1, 20, 11));

        ExactCenterOptimizer.CenterScore expected = null;
        for (int x = -30; x <= 30; x++) {
            for (int z = -30; z <= 30; z++) {
                int centerX = x;
                int centerZ = z;
                if (!structures.stream().allMatch(point -> shape.contains(
                        centerX, centerZ, point.x(), point.z(), radius))) continue;
                int count = (int) spawners.stream().filter(point -> shape.contains(
                        centerX, centerZ, point.x(), point.z(), radius)).count();
                ExactCenterOptimizer.CenterScore candidate =
                        new ExactCenterOptimizer.CenterScore(x, z, count);
                if (expected == null || candidate.compareTo(expected) < 0) expected = candidate;
            }
        }

        assertEquals(expected, ExactCenterOptimizer.find(shape, radius, structures, spawners));
    }

    private static ExactCenterOptimizer.CenterScore referenceFind(
            AreaShape shape, int radius, List<BlockPoint> structures,
            List<SpawnerPoint> spawners) {
        int minX = structures.stream().mapToInt(BlockPoint::x).min().orElseThrow() - radius;
        int maxX = structures.stream().mapToInt(BlockPoint::x).max().orElseThrow() + radius;
        int minZ = structures.stream().mapToInt(BlockPoint::z).min().orElseThrow() - radius;
        int maxZ = structures.stream().mapToInt(BlockPoint::z).max().orElseThrow() + radius;
        ExactCenterOptimizer.CenterScore best = null;
        for (int z = minZ; z <= maxZ; z++) {
            for (int x = minX; x <= maxX; x++) {
                int centerX = x;
                int centerZ = z;
                if (!structures.stream().allMatch(point -> shape.contains(
                        centerX, centerZ, point.x(), point.z(), radius))) continue;
                int count = (int) spawners.stream().filter(point -> shape.contains(
                        centerX, centerZ, point.x(), point.z(), radius)).count();
                ExactCenterOptimizer.CenterScore candidate =
                        new ExactCenterOptimizer.CenterScore(x, z, count);
                if (best == null || candidate.compareTo(best) < 0) best = candidate;
            }
        }
        if (best == null) throw new IllegalStateException("reference has no legal center");
        return best;
    }
}
