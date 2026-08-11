package cn.trialfinder.search;

import cn.trialfinder.config.AreaShape;
import cn.trialfinder.model.BlockPoint;
import cn.trialfinder.model.SpawnerPoint;

import java.util.Collection;
import java.util.List;

public final class ExactCenterOptimizer {
    // Keep auxiliary memory bounded when callers use unusually large radii.
    private static final int MAX_CIRCLE_LOOKUP_RADIUS = 1_000_000;
    private static final int MAX_SQUARE_DIFFERENCE_CELLS = 1_000_000;
    private static volatile CircleHorizontalCache circleHorizontalCache;

    private ExactCenterOptimizer() {
    }

    public static CenterScore find(AreaShape shape, int radius, List<BlockPoint> structures,
                                   Collection<SpawnerPoint> spawners) {
        if (shape == AreaShape.SQUARE) {
            // Square scoring via one 2D difference pass over the whole candidate grid — O(cells +
            // spawners) instead of O(spawners × rows). Falls back to the row-by-row path when the
            // grid would be too large.
            CenterScore square = findSquare(radius, structures, spawners);
            if (square != null) {
                return square;
            }
        }
        int minZ = structures.stream().mapToInt(BlockPoint::z).max().orElseThrow() - radius;
        int maxZ = structures.stream().mapToInt(BlockPoint::z).min().orElseThrow() + radius;
        CenterScore best = null;
        long radiusSquared = (long) radius * radius;
        int[] circleHorizontal = shape == AreaShape.CIRCLE
                ? circleHorizontalLookup(radius) : null;

        for (int z = minZ; z <= maxZ; z++) {
            IntRange legal = legalXRange(
                    shape, radius, radiusSquared, circleHorizontal, structures, z);
            if (legal == null) continue;

            int[] difference = new int[legal.max() - legal.min() + 2];
            for (SpawnerPoint spawner : spawners) {
                IntRange covered = coveredXRange(
                        shape, radius, radiusSquared, circleHorizontal,
                        spawner.x(), spawner.z(), z);
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

    /**
     * Square scoring via a single 2D difference pass: for each spawner add +1/−1 at the corners of
     * the square it covers, then one prefix-sum sweep gives the spawner count at every integer
     * centre. Returns {@code null} (so the caller falls back to the row-by-row path) when the grid
     * would exceed {@link #MAX_SQUARE_DIFFERENCE_CELLS}.
     */
    private static CenterScore findSquare(
            int radius, List<BlockPoint> structures, Collection<SpawnerPoint> spawners) {
        int minX = structures.stream().mapToInt(BlockPoint::x).max().orElseThrow() - radius;
        int maxX = structures.stream().mapToInt(BlockPoint::x).min().orElseThrow() + radius;
        int minZ = structures.stream().mapToInt(BlockPoint::z).max().orElseThrow() - radius;
        int maxZ = structures.stream().mapToInt(BlockPoint::z).min().orElseThrow() + radius;
        if (minX > maxX || minZ > maxZ) {
            throw new IllegalStateException("找不到能包含所有密室起点的整数中心");
        }

        long widthLong = (long) maxX - minX + 1;
        long heightLong = (long) maxZ - minZ + 1;
        // Keep concurrent searches bounded for unusually large configured radii.
        if (widthLong > MAX_SQUARE_DIFFERENCE_CELLS
                || heightLong > MAX_SQUARE_DIFFERENCE_CELLS
                || widthLong * heightLong > MAX_SQUARE_DIFFERENCE_CELLS) {
            return null;
        }
        int width = (int) widthLong;
        int height = (int) heightLong;
        int stride = width + 1;
        int[] difference = new int[stride * (height + 1)];
        for (SpawnerPoint spawner : spawners) {
            int fromX = Math.max(minX, spawner.x() - radius);
            int toX = Math.min(maxX, spawner.x() + radius);
            int fromZ = Math.max(minZ, spawner.z() - radius);
            int toZ = Math.min(maxZ, spawner.z() + radius);
            if (fromX > toX || fromZ > toZ) continue;

            int x0 = fromX - minX;
            int x1 = toX - minX + 1;
            int z0 = fromZ - minZ;
            int z1 = toZ - minZ + 1;
            difference[z0 * stride + x0]++;
            difference[z0 * stride + x1]--;
            difference[z1 * stride + x0]--;
            difference[z1 * stride + x1]++;
        }

        CenterScore best = null;
        for (int z = 0; z < height; z++) {
            int rowSum = 0;
            for (int x = 0; x < width; x++) {
                int index = z * stride + x;
                rowSum += difference[index];
                int count = rowSum + (z == 0 ? 0 : difference[index - stride]);
                difference[index] = count;
                CenterScore candidate = new CenterScore(minX + x, minZ + z, count);
                if (best == null || candidate.compareTo(best) < 0) best = candidate;
            }
        }
        return best;
    }

    private static IntRange legalXRange(AreaShape shape, int radius, long radiusSquared,
                                        int[] circleHorizontal, List<BlockPoint> structures,
                                        int centerZ) {
        int min = Integer.MIN_VALUE;
        int max = Integer.MAX_VALUE;
        for (BlockPoint structure : structures) {
            IntRange range = coveredXRange(
                    shape, radius, radiusSquared, circleHorizontal,
                    structure.x(), structure.z(), centerZ);
            if (range == null) return null;
            min = Math.max(min, range.min());
            max = Math.min(max, range.max());
            if (min > max) return null;
        }
        return new IntRange(min, max);
    }

    private static IntRange coveredXRange(AreaShape shape, int radius, long radiusSquared,
                                          int[] circleHorizontal, int pointX, int pointZ,
                                          int centerZ) {
        long dz = (long) pointZ - centerZ;
        long absDz = Math.abs(dz);
        if (absDz > radius) return null;
        int horizontal = switch (shape) {
            case CIRCLE -> circleHorizontal == null
                    ? floorSqrt(radiusSquared - dz * dz)
                    : circleHorizontal[(int) absDz];
            case SQUARE -> radius;
        };
        return new IntRange(pointX - horizontal, pointX + horizontal);
    }

    private static int[] buildCircleHorizontalLookup(int radius) {
        if (radius < 0 || radius > MAX_CIRCLE_LOOKUP_RADIUS) return null;
        int[] horizontal = new int[radius + 1];
        long radiusSquared = (long) radius * radius;
        for (int dz = 0; dz <= radius; dz++) {
            horizontal[dz] = floorSqrt(radiusSquared - (long) dz * dz);
        }
        return horizontal;
    }

    private static int[] circleHorizontalLookup(int radius) {
        if (radius < 0 || radius > MAX_CIRCLE_LOOKUP_RADIUS) return null;
        CircleHorizontalCache cached = circleHorizontalCache;
        if (cached != null && cached.radius() == radius) return cached.values();
        int[] values = buildCircleHorizontalLookup(radius);
        circleHorizontalCache = new CircleHorizontalCache(radius, values);
        return values;
    }

    private static int floorSqrt(long value) {
        int root = (int) Math.sqrt(value);
        while ((long) (root + 1) * (root + 1) <= value) root++;
        while ((long) root * root > value) root--;
        return root;
    }

    public record CenterScore(long x, long z, int spawners) implements Comparable<CenterScore> {
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

    private record CircleHorizontalCache(int radius, int[] values) {
    }
}
