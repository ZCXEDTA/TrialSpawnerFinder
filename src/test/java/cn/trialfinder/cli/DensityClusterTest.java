package cn.trialfinder.cli;

import cn.trialfinder.model.BlockPoint;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Validates the density-aware coarse clustering and the KD-tree spatial index:
 * <ol>
 *   <li>KD-tree {@code nearestBetter} matches a brute-force scan;</li>
 *   <li>density-peak clustering separates high-density cores separated by a gap;</li>
 *   <li>oversized clusters are recursively split to at most maxClusterSize;</li>
 *   <li>the clustering is deterministic (no randomness).</li>
 * </ol>
 */
class DensityClusterTest {

    private static SearchEngine.ScoredCandidate cand(int x, int z, int score) {
        return new SearchEngine.ScoredCandidate(new BlockPoint(x, z), score);
    }

    @Test
    void spatialIndexNearestBetterMatchesBruteForce() {
        Random rng = new Random(42);
        List<SearchEngine.ScoredCandidate> candidates = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            int x = rng.nextInt(2000) - 1000;
            int z = rng.nextInt(2000) - 1000;
            int score = rng.nextInt(10);
            candidates.add(cand(x, z, score));
        }
        SearchEngine.SpatialIndex index = new SearchEngine.SpatialIndex(candidates);
        int radius = 300;
        for (int target = 0; target < candidates.size(); target += 7) {
            int kdBest = index.nearestBetter(target, radius);
            int bruteBest = bruteForceNearestBetter(candidates, target, radius);
            assertEquals(bruteBest, kdBest, "nearestBetter mismatch for target " + target);
        }
    }

    @Test
    void densityPeakSeparatesTwoHighDensityCores() {
        // Two dense cores (score 8) separated by a sparse gap (score 1..2).
        List<SearchEngine.ScoredCandidate> candidates = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            candidates.add(cand(-800 + i * 10, 0, 8));   // left core
            candidates.add(cand(800 + i * 10, 0, 8));    // right core
        }
        for (int i = 0; i < 10; i++) {
            candidates.add(cand(-50 + i * 10, 0, 2));    // sparse bridge
        }
        // Small radius keeps the two cores separate.
        List<SearchEngine.CoarseCluster> clusters =
                SearchEngine.densityPeakCluster(candidates, 200, Integer.MAX_VALUE);
        // At least 2 clusters; the two cores must be in different clusters.
        assertTrue(clusters.size() >= 2, "expected at least 2 clusters, got " + clusters.size());
        boolean hasLeft = false;
        boolean hasRight = false;
        for (SearchEngine.CoarseCluster c : clusters) {
            boolean left = c.members().stream().anyMatch(m -> m.point().x() < -500);
            boolean right = c.members().stream().anyMatch(m -> m.point().x() > 500);
            assertTrue(!(left && right),
                    "a cluster must not span both cores (radius too large): " + c.size());
            if (left) {
                hasLeft = true;
            }
            if (right) {
                hasRight = true;
            }
        }
        assertTrue(hasLeft && hasRight, "both cores must be represented");
    }

    @Test
    void oversizedClusterIsSplitToMaxSize() {
        // A dense uniform blob that exceeds maxClusterSize must be recursively split.
        List<SearchEngine.ScoredCandidate> candidates = new ArrayList<>();
        for (int x = 0; x < 100; x += 5) {
            for (int z = 0; z < 100; z += 5) {
                candidates.add(cand(x, z, 10));
            }
        }
        int maxSize = 25;
        List<SearchEngine.CoarseCluster> clusters =
                SearchEngine.densityPeakCluster(candidates, 200, maxSize);
        for (SearchEngine.CoarseCluster c : clusters) {
            assertTrue(c.size() <= maxSize,
                    "cluster size " + c.size() + " exceeds max " + maxSize);
        }
        assertTrue(clusters.size() >= 2, "uniform blob should be split into multiple clusters");
    }

    @Test
    void densityPeakIsDeterministic() {
        Random rng = new Random(7);
        List<SearchEngine.ScoredCandidate> candidates = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            candidates.add(cand(rng.nextInt(1000), rng.nextInt(1000), rng.nextInt(8)));
        }
        List<SearchEngine.CoarseCluster> first =
                SearchEngine.densityPeakCluster(candidates, 150, 50);
        List<SearchEngine.CoarseCluster> second =
                SearchEngine.densityPeakCluster(candidates, 150, 50);
        assertEquals(first, second, "density-peak clustering must be deterministic");
    }

    private static int bruteForceNearestBetter(
            List<SearchEngine.ScoredCandidate> candidates, int target, int radius) {
        BlockPoint p = candidates.get(target).point();
        long radiusSq = (long) radius * radius;
        int best = -1;
        long bestDist = Long.MAX_VALUE;
        for (int j = 0; j < candidates.size(); j++) {
            if (j == target) {
                continue;
            }
            BlockPoint q = candidates.get(j).point();
            long ox = (long) p.x() - q.x();
            long oz = (long) p.z() - q.z();
            long d2 = ox * ox + oz * oz;
            if (d2 > radiusSq) {
                continue;
            }
            int sa = candidates.get(j).score();
            int sb = candidates.get(target).score();
            boolean betterScore = sa > sb
                    || (sa == sb && (q.x() < p.x() || (q.x() == p.x() && q.z() < p.z())));
            if (betterScore && (best == -1 || d2 < bestDist || (d2 == bestDist && j < best))) {
                best = j;
                bestDist = d2;
            }
        }
        return best;
    }
}
