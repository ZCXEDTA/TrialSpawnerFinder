package cn.trialfinder.accel;

import cn.trialfinder.model.BlockPoint;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/**
 * Verifies that density scoring stays correct when the spatial-hash cell is enlarged to keep the
 * grid within the bounded-cell limit (a 1M-block span with a small cluster radius triggers this).
 */
class CpuAcceleratorAdaptiveTest {

    @Test
    void densityScoresWithAdaptiveCellMatchBruteForce() {
        Random random = new Random(0x1BADCAFE);
        // Sparse points across a ±1,000,000 block span; clusterRadius 64 → nominal cell 128 would
        // give (2M/128)^2 ≈ 244M cells, far above the 10M limit, so the cell must be enlarged.
        List<BlockPoint> points = new ArrayList<>();
        for (int i = 0; i < 300; i++) {
            int x = random.nextInt(2_000_001) - 1_000_000;
            int z = random.nextInt(2_000_001) - 1_000_000;
            points.add(new BlockPoint(x, z));
        }

        CpuAccelerator cpu = new CpuAccelerator();
        int[] scores = cpu.densityScores(points, 64);

        // Brute-force reference: count neighbours within 2R (Euclidean), including self.
        long radiusSq = 128L * 128L;
        int[] expected = new int[points.size()];
        for (int i = 0; i < points.size(); i++) {
            BlockPoint p = points.get(i);
            int count = 0;
            for (BlockPoint q : points) {
                long ox = (long) p.x() - q.x();
                long oz = (long) p.z() - q.z();
                if (ox * ox + oz * oz <= radiusSq) {
                    count++;
                }
            }
            expected[i] = count;
        }
        assertArrayEquals(expected, scores,
                "enlarged-cell density scores must equal brute-force counts");
    }

    @Test
    void densityScoresTwoPointSpanNoGridOverflow() {
        // Two points at ±10M: the span is huge but the enlarged cell must still return 1 each.
        List<BlockPoint> points = List.of(
                new BlockPoint(-10_000_000, 0),
                new BlockPoint(10_000_000, 0));
        CpuAccelerator cpu = new CpuAccelerator();
        int[] scores = cpu.densityScores(points, 32);
        assertArrayEquals(new int[]{1, 1}, scores,
                "far-apart points must not count each other regardless of cell size");
    }
}
