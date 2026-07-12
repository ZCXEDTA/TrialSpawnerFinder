package cn.trialfinder.search;

import cn.trialfinder.model.BlockPoint;
import cn.trialfinder.model.CircleCenter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
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
        Map<Long, List<Integer>> grid = new HashMap<>();
        Set<Center> centers = new LinkedHashSet<>();

        for (int i = 0; i < points.size(); i++) {
            BlockPoint point = points.get(i);
            centers.add(new Center(point.x(), point.z()));
            int cellX = Math.floorDiv(point.x(), cellSize);
            int cellZ = Math.floorDiv(point.z(), cellSize);
            grid.computeIfAbsent(cellKey(cellX, cellZ), ignored -> new ArrayList<>()).add(i);
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
                    centers.add(new Center(x, z));
                }
            }
        }

        Map<List<BlockPoint>, CircleClusters.StructureCluster> unique = new LinkedHashMap<>();
        for (Center center : centers) {
            int cellX = Math.floorDiv(center.x, cellSize);
            int cellZ = Math.floorDiv(center.z, cellSize);
            List<BlockPoint> members = new ArrayList<>();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    for (int index : grid.getOrDefault(cellKey(cellX + dx, cellZ + dz), List.of())) {
                        BlockPoint point = points.get(index);
                        if (Math.abs((long) point.x() - center.x) <= radius
                                && Math.abs((long) point.z() - center.z) <= radius) {
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

    private record Center(int x, int z) {
    }
}
