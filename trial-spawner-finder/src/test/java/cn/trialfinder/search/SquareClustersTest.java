package cn.trialfinder.search;

import cn.minecraftfinder.core.BlockPoint;
import cn.minecraftfinder.core.CircleCenter;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SquareClustersTest {
    @Test
    void includesOppositeSquareCornersOnBoundary() {
        List<BlockPoint> points = List.of(new BlockPoint(-5, -5), new BlockPoint(5, 5));

        List<CircleClusters.StructureCluster> clusters = SquareClusters.find(points, 5, 2);

        assertEquals(1, clusters.stream().filter(cluster -> cluster.structures().size() == 2).count());
    }

    @Test
    void acceptsPointsOutsideEquivalentCircle() {
        List<BlockPoint> points = List.of(new BlockPoint(0, 0), new BlockPoint(10, 10));

        assertTrue(SquareClusters.find(points, 5, 2).stream()
                .anyMatch(cluster -> cluster.structures().size() == 2));
        assertTrue(CircleClusters.find(points, 5, 2).isEmpty());
    }

    @Test
    void matchesPreviousAlgorithmForRandomNegativeAndBoundaryCoordinates() {
        Random random = new Random(0x5A17_2026L);
        for (int sample = 0; sample < 200; sample++) {
            int radius = random.nextInt(1, 7);
            int minimum = random.nextInt(1, 5);
            int pointCount = random.nextInt(2, 11);
            TreeSet<BlockPoint> uniquePoints = new TreeSet<>();
            while (uniquePoints.size() < pointCount) {
                uniquePoints.add(new BlockPoint(
                        random.nextInt(-12, 13), random.nextInt(-12, 13)));
            }
            List<BlockPoint> points = List.copyOf(uniquePoints);

            assertEquals(keys(previousFind(points, radius, minimum)),
                    keys(SquareClusters.find(points, radius, minimum)),
                    "sample=" + sample + ", radius=" + radius + ", minimum=" + minimum);
        }
    }

    private static List<CircleClusters.StructureCluster> previousFind(
            List<BlockPoint> points, int radius, int minimum) {
        int cellSize = Math.max(1, radius * 2);
        Map<Long, List<Integer>> grid = new LinkedHashMap<>();
        Set<TestCenter> centers = new LinkedHashSet<>();
        for (int index = 0; index < points.size(); index++) {
            BlockPoint point = points.get(index);
            centers.add(new TestCenter(point.x(), point.z()));
            int cellX = Math.floorDiv(point.x(), cellSize);
            int cellZ = Math.floorDiv(point.z(), cellSize);
            grid.computeIfAbsent(cellKey(cellX, cellZ), ignored -> new ArrayList<>()).add(index);
        }
        for (BlockPoint anchor : points) {
            int cellX = Math.floorDiv(anchor.x(), cellSize);
            int cellZ = Math.floorDiv(anchor.z(), cellSize);
            Set<Integer> xs = new LinkedHashSet<>();
            Set<Integer> zs = new LinkedHashSet<>();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    for (int index : grid.getOrDefault(cellKey(cellX + dx, cellZ + dz), List.of())) {
                        BlockPoint point = points.get(index);
                        if (Math.abs((long) anchor.x() - point.x()) <= radius * 2L
                                && Math.abs((long) anchor.z() - point.z()) <= radius * 2L) {
                            xs.add(point.x() - radius);
                            xs.add(point.x() + radius);
                            zs.add(point.z() - radius);
                            zs.add(point.z() + radius);
                        }
                    }
                }
            }
            for (int x : xs) {
                for (int z : zs) {
                    centers.add(new TestCenter(x, z));
                }
            }
        }

        Map<List<BlockPoint>, CircleClusters.StructureCluster> unique = new LinkedHashMap<>();
        for (TestCenter center : centers) {
            List<BlockPoint> members = new ArrayList<>();
            int cellX = Math.floorDiv(center.x(), cellSize);
            int cellZ = Math.floorDiv(center.z(), cellSize);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    for (int index : grid.getOrDefault(cellKey(cellX + dx, cellZ + dz), List.of())) {
                        BlockPoint point = points.get(index);
                        if (Math.abs((long) point.x() - center.x()) <= radius
                                && Math.abs((long) point.z() - center.z()) <= radius) {
                            members.add(point);
                        }
                    }
                }
            }
            if (members.size() < minimum) continue;
            members.sort(BlockPoint::compareTo);
            List<BlockPoint> key = List.copyOf(members);
            CircleCenter canonical = new CircleCenter(
                    members.stream().mapToInt(BlockPoint::x).max().orElseThrow() - radius,
                    members.stream().mapToInt(BlockPoint::z).max().orElseThrow() - radius);
            unique.putIfAbsent(key, new CircleClusters.StructureCluster(canonical, key));
        }
        return unique.values().stream()
                .sorted(Comparator.comparingInt(
                                (CircleClusters.StructureCluster cluster) -> cluster.structures().size())
                        .thenComparingLong(cluster -> cluster.center().roundedX())
                        .thenComparingLong(cluster -> cluster.center().roundedZ()))
                .toList();
    }

    private static long cellKey(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffff_ffffL);
    }

    private static List<String> keys(List<CircleClusters.StructureCluster> clusters) {
        return clusters.stream()
                .map(cluster -> cluster.center().roundedX() + "," + cluster.center().roundedZ()
                        + ":" + cluster.structures())
                .sorted()
                .toList();
    }

    private record TestCenter(int x, int z) {
    }
}
