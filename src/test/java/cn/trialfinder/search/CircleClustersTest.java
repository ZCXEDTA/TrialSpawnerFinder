package cn.trialfinder.search;

import cn.trialfinder.model.BlockPoint;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CircleClustersTest {
    @Test
    void findsPairWhoseBestCenterIsBetweenStructures() {
        List<BlockPoint> points = List.of(new BlockPoint(0, 0), new BlockPoint(10, 0));

        List<CircleClusters.StructureCluster> clusters = CircleClusters.find(points, 5, 2);

        assertEquals(1, clusters.size());
        assertEquals(2, clusters.getFirst().structures().size());
        assertEquals(5, clusters.getFirst().center().roundedX());
        assertEquals(0, clusters.getFirst().center().roundedZ());
    }

    @Test
    void includesPointsExactlyOnCircleBoundary() {
        List<BlockPoint> points = List.of(new BlockPoint(-3, 0), new BlockPoint(3, 0));

        assertEquals(1, CircleClusters.find(points, 3, 2).size());
    }

    @Test
    void mergesCentersContainingTheSameStructures() {
        List<BlockPoint> points = List.of(
                new BlockPoint(0, 0), new BlockPoint(2, 0), new BlockPoint(1, 1));

        List<CircleClusters.StructureCluster> clusters = CircleClusters.find(points, 10, 2);

        long fullClusters = clusters.stream().filter(cluster -> cluster.structures().size() == 3).count();
        assertEquals(1, fullClusters);
    }

    @Test
    void sortsLargerClustersFirst() {
        List<BlockPoint> points = List.of(
                new BlockPoint(0, 0), new BlockPoint(1, 0), new BlockPoint(0, 1),
                new BlockPoint(100, 100), new BlockPoint(101, 100));

        List<CircleClusters.StructureCluster> clusters = CircleClusters.find(points, 2, 2);

        assertTrue(clusters.size() >= 2);
        assertEquals(3, clusters.getFirst().structures().size());
    }
}
