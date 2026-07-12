package cn.trialfinder.search;

import cn.minecraftfinder.core.BlockPoint;
import org.junit.jupiter.api.Test;

import java.util.List;

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
}
