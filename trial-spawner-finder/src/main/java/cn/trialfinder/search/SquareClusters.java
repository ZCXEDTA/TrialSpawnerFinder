package cn.trialfinder.search;

import cn.minecraftfinder.core.BlockPoint;
import cn.minecraftfinder.core.CircleCenter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class SquareClusters {
    private SquareClusters() {
    }

    public static List<CircleClusters.StructureCluster> find(
            List<BlockPoint> points, int radius, int minimum) {
        int cellSize = Math.max(1, radius * 2);
        PointGrid grid = new PointGrid(points.size());

        for (int i = 0; i < points.size(); i++) {
            BlockPoint point = points.get(i);
            int cellX = Math.floorDiv(point.x(), cellSize);
            int cellZ = Math.floorDiv(point.z(), cellSize);
            grid.add(cellX, cellZ, i);
        }

        boolean[] eligible = new boolean[points.size()];
        for (int index = 0; index < points.size(); index++) {
            BlockPoint point = points.get(index);
            int cellX = Math.floorDiv(point.x(), cellSize);
            int cellZ = Math.floorDiv(point.z(), cellSize);
            eligible[index] = hasNearbyAtLeast(
                    points, grid, point, cellX, cellZ, radius, minimum);
        }

        Set<Long> boundaryCenters = new LinkedHashSet<>();
        for (int anchorIndex = 0; anchorIndex < points.size(); anchorIndex++) {
            if (!eligible[anchorIndex]) continue;
            BlockPoint anchor = points.get(anchorIndex);
            int cellX = Math.floorDiv(anchor.x(), cellSize);
            int cellZ = Math.floorDiv(anchor.z(), cellSize);
            Set<Integer> xs = new LinkedHashSet<>();
            Set<Integer> zs = new LinkedHashSet<>();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    for (int index = grid.first(cellX + dx, cellZ + dz);
                         index >= 0; index = grid.next(index)) {
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
                    boundaryCenters.add(cellKey(x, z));
                }
            }
        }

        List<Long> centers = new ArrayList<>(boundaryCenters.size());
        for (int index = 0; index < points.size(); index++) {
            BlockPoint point = points.get(index);
            long key = cellKey(point.x(), point.z());
            if (eligible[index] || boundaryCenters.remove(key)) {
                centers.add(key);
            }
        }
        centers.addAll(boundaryCenters);

        Map<List<BlockPoint>, CircleClusters.StructureCluster> unique = new LinkedHashMap<>();
        for (long center : centers) {
            int centerX = (int) (center >> 32);
            int centerZ = (int) center;
            int cellX = Math.floorDiv(centerX, cellSize);
            int cellZ = Math.floorDiv(centerZ, cellSize);
            List<BlockPoint> members = new ArrayList<>();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    for (int index = grid.first(cellX + dx, cellZ + dz);
                         index >= 0; index = grid.next(index)) {
                        BlockPoint point = points.get(index);
                        if (Math.abs((long) point.x() - centerX) <= radius
                                && Math.abs((long) point.z() - centerZ) <= radius) {
                            members.add(point);
                        }
                    }
                }
            }
            if (members.size() < minimum) {
                continue;
            }
            members.sort(BlockPoint::compareTo);
            List<BlockPoint> key = List.copyOf(members);
            CircleClusters.StructureCluster candidate = new CircleClusters.StructureCluster(
                    canonicalCenter(key, radius), key);
            unique.merge(key, candidate, SquareClusters::minimumCenter);
        }

        return unique.values().stream()
                .sorted(Comparator.comparingInt(
                                (CircleClusters.StructureCluster cluster) -> cluster.structures().size())
                        .thenComparingLong(cluster -> cluster.center().roundedX())
                        .thenComparingLong(cluster -> cluster.center().roundedZ()))
                .toList();
    }

    private static CircleClusters.StructureCluster minimumCenter(
            CircleClusters.StructureCluster a, CircleClusters.StructureCluster b) {
        int byX = Long.compare(a.center().roundedX(), b.center().roundedX());
        if (byX != 0) return byX < 0 ? a : b;
        return a.center().roundedZ() <= b.center().roundedZ() ? a : b;
    }

    private static CircleCenter canonicalCenter(List<BlockPoint> members, int radius) {
        int minX = members.stream().mapToInt(BlockPoint::x).max().orElseThrow() - radius;
        int minZ = members.stream().mapToInt(BlockPoint::z).max().orElseThrow() - radius;
        return new CircleCenter(minX, minZ);
    }

    private static long cellKey(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffff_ffffL);
    }

    private static boolean hasNearbyAtLeast(
            List<BlockPoint> points,
            PointGrid grid,
            BlockPoint point,
            int cellX,
            int cellZ,
            int radius,
            int minimum) {
        long diameter = radius * 2L;
        int nearbyCount = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int other = grid.first(cellX + dx, cellZ + dz);
                     other >= 0; other = grid.next(other)) {
                    BlockPoint nearby = points.get(other);
                    if (Math.abs((long) point.x() - nearby.x()) <= diameter
                            && Math.abs((long) point.z() - nearby.z()) <= diameter
                            && ++nearbyCount >= minimum) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
