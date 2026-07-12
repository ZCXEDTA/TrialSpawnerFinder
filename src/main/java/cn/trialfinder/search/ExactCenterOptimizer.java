package cn.trialfinder.search;

import cn.trialfinder.config.AreaShape;
import cn.trialfinder.model.BlockPoint;
import cn.trialfinder.model.SpawnerPoint;

import java.util.Collection;
import java.util.List;

final class ExactCenterOptimizer {
    private ExactCenterOptimizer() {
    }

    static CenterScore find(AreaShape shape, int radius, List<BlockPoint> structures,
                            Collection<SpawnerPoint> spawners) {
        int minZ = structures.stream().mapToInt(BlockPoint::z).max().orElseThrow() - radius;
        int maxZ = structures.stream().mapToInt(BlockPoint::z).min().orElseThrow() + radius;
        CenterScore best = null;
        long radiusSquared = (long) radius * radius;

        for (int z = minZ; z <= maxZ; z++) {
            IntRange legal = legalXRange(shape, radius, radiusSquared, structures, z);
            if (legal == null) continue;

            int[] difference = new int[legal.max() - legal.min() + 2];
            for (SpawnerPoint spawner : spawners) {
                IntRange covered = coveredXRange(
                        shape, radius, radiusSquared, spawner.x(), spawner.z(), z);
                if (covered == null) continue;
                int from = Math.max(legal.min(), covered.min());
                int to = Math.min(legal.max(), covered.max());
                if (from <= to) {
                    difference[from - legal.min()]++;
                    difference[to - legal.min() + 1]--;
                }
            }

            int count = 0;
            for (int index = 0; index < difference.length - 1; index++) {
                count += difference[index];
                CenterScore candidate = new CenterScore(legal.min() + index, z, count);
                if (best == null || candidate.compareTo(best) < 0) best = candidate;
            }
        }
        if (best == null) throw new IllegalStateException("找不到能包含所有密室起点的整数中心");
        return best;
    }

    private static IntRange legalXRange(AreaShape shape, int radius, long radiusSquared,
                                        List<BlockPoint> structures, int centerZ) {
        int min = Integer.MIN_VALUE;
        int max = Integer.MAX_VALUE;
        for (BlockPoint structure : structures) {
            IntRange range = coveredXRange(
                    shape, radius, radiusSquared, structure.x(), structure.z(), centerZ);
            if (range == null) return null;
            min = Math.max(min, range.min());
            max = Math.min(max, range.max());
            if (min > max) return null;
        }
        return new IntRange(min, max);
    }

    private static IntRange coveredXRange(AreaShape shape, int radius, long radiusSquared,
                                          int pointX, int pointZ, int centerZ) {
        long dz = (long) pointZ - centerZ;
        if (Math.abs(dz) > radius) return null;
        int horizontal = switch (shape) {
            case CIRCLE -> floorSqrt(radiusSquared - dz * dz);
            case SQUARE -> radius;
        };
        return new IntRange(pointX - horizontal, pointX + horizontal);
    }

    private static int floorSqrt(long value) {
        int root = (int) Math.sqrt(value);
        while ((long) (root + 1) * (root + 1) <= value) root++;
        while ((long) root * root > value) root--;
        return root;
    }

    record CenterScore(long x, long z, int spawners) implements Comparable<CenterScore> {
        @Override
        public int compareTo(CenterScore other) {
            int bySpawners = Integer.compare(other.spawners, spawners);
            if (bySpawners != 0) return bySpawners;
            int byX = Long.compare(x, other.x);
            return byX != 0 ? byX : Long.compare(z, other.z);
        }
    }

    private record IntRange(int min, int max) {
    }
}
