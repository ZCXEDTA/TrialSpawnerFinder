package cn.trialfinder.search;

import cn.trialfinder.config.AreaShape;
import cn.trialfinder.model.BlockPoint;
import cn.trialfinder.model.SpawnerPoint;
import org.junit.jupiter.api.Test;

import java.util.List;

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
}
