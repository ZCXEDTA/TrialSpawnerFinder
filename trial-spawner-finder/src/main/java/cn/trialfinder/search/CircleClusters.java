package cn.trialfinder.search;

import cn.minecraftfinder.core.BlockPoint;
import cn.minecraftfinder.core.CircleCenter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CircleClusters {
    private static final double EPSILON = 1.0e-7;

    private CircleClusters() {
    }

    public static List<StructureCluster> find(List<BlockPoint> points, int radius, int minimum) {
        List<CircleCenter> centers = new ArrayList<>();
        for (BlockPoint point : points) {
            centers.add(new CircleCenter(point.x(), point.z()));
        }
        int cellSize = Math.max(1, radius * 2);
        PointGrid grid = new PointGrid(points.size());
        for (int i = 0; i < points.size(); i++) {
            BlockPoint point = points.get(i);
            int cellX = Math.floorDiv(point.x(), cellSize);
            int cellZ = Math.floorDiv(point.z(), cellSize);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    for (int other = grid.first(cellX + dx, cellZ + dz);
                         other >= 0; other = grid.next(other)) {
                        addIntersections(points.get(other), point, radius, centers);
                    }
                }
            }
            grid.add(cellX, cellZ, i);
        }

        Map<List<BlockPoint>, StructureCluster> unique = new LinkedHashMap<>();
        double radiusSquared = (double) radius * radius;
        for (CircleCenter center : centers) {
            int centerCellX = Math.floorDiv((int) Math.floor(center.x()), cellSize);
            int centerCellZ = Math.floorDiv((int) Math.floor(center.z()), cellSize);
            List<BlockPoint> members = new ArrayList<>();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    for (int index = grid.first(centerCellX + dx, centerCellZ + dz);
                         index >= 0; index = grid.next(index)) {
                        BlockPoint point = points.get(index);
                        if (distanceSquared(center, point) <= radiusSquared + EPSILON) {
                            members.add(point);
                        }
                    }
                }
            }
            members.sort(BlockPoint::compareTo);
            if (members.size() < minimum) {
                continue;
            }
            CircleCenter integerCenter = bestIntegerCenter(center, members, radiusSquared);
            if (integerCenter == null) {
                continue;
            }
            StructureCluster candidate = new StructureCluster(integerCenter, members);
            unique.merge(members, candidate, CircleClusters::stableMinimumCenter);
        }
        return unique.values().stream()
                .sorted(Comparator.comparingInt((StructureCluster cluster) -> cluster.structures().size()).reversed()
                        .thenComparingLong(cluster -> cluster.center().roundedX())
                        .thenComparingLong(cluster -> cluster.center().roundedZ()))
                .toList();
    }

    private static void addIntersections(BlockPoint a, BlockPoint b, double radius,
                                         Collection<CircleCenter> output) {
        double dx = b.x() - a.x();
        double dz = b.z() - a.z();
        double distanceSquared = dx * dx + dz * dz;
        double diameter = radius * 2.0;
        if (distanceSquared == 0.0 || distanceSquared > diameter * diameter + EPSILON) {
            return;
        }
        double distance = Math.sqrt(distanceSquared);
        double middleX = (a.x() + b.x()) / 2.0;
        double middleZ = (a.z() + b.z()) / 2.0;
        double height = Math.sqrt(Math.max(0.0, radius * radius - distanceSquared / 4.0));
        double perpendicularX = -dz / distance;
        double perpendicularZ = dx / distance;
        output.add(new CircleCenter(middleX + perpendicularX * height, middleZ + perpendicularZ * height));
        output.add(new CircleCenter(middleX - perpendicularX * height, middleZ - perpendicularZ * height));
    }

    private static CircleCenter bestIntegerCenter(CircleCenter source, List<BlockPoint> members,
                                                  double radiusSquared) {
        long floorX = (long) Math.floor(source.x());
        long floorZ = (long) Math.floor(source.z());
        CircleCenter best = null;
        for (long x = floorX - 1; x <= floorX + 2; x++) {
            for (long z = floorZ - 1; z <= floorZ + 2; z++) {
                CircleCenter candidate = new CircleCenter(x, z);
                if (members.stream().allMatch(point -> distanceSquared(candidate, point) <= radiusSquared + EPSILON)
                        && (best == null || compareCenters(candidate, best) < 0)) {
                    best = candidate;
                }
            }
        }
        return best;
    }

    private static StructureCluster stableMinimumCenter(StructureCluster a, StructureCluster b) {
        return compareCenters(a.center(), b.center()) <= 0 ? a : b;
    }

    private static int compareCenters(CircleCenter a, CircleCenter b) {
        int byX = Long.compare(a.roundedX(), b.roundedX());
        return byX != 0 ? byX : Long.compare(a.roundedZ(), b.roundedZ());
    }

    private static double distanceSquared(CircleCenter center, BlockPoint point) {
        double dx = center.x() - point.x();
        double dz = center.z() - point.z();
        return dx * dx + dz * dz;
    }

    public record StructureCluster(CircleCenter center, List<BlockPoint> structures) {
        public StructureCluster {
            structures = List.copyOf(structures);
        }
    }
}
