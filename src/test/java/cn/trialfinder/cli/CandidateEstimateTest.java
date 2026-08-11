package cn.trialfinder.cli;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the trial-chamber candidate-count estimator used to size automatic tiles and warn about
 * impractical search radii. One chamber per 34x34-chunk region (544x544 blocks).
 */
class CandidateEstimateTest {

    private static long circleRadius(long r) {
        return SearchEngine.estimateCandidates(-r, r, -r, r, true, (long) r * r);
    }

    private static long squareRadius(long r) {
        return SearchEngine.estimateCandidates(-r, r, -r, r, false, 0);
    }

    @Test
    void circleTenThousandMatchesMeasured() {
        // Measured: seed 188188 radius 10000 circle enumerates 1058 candidates.
        long est = circleRadius(10_000);
        assertTrue(Math.abs(est - 1058) <= 20, "est " + est + " should be ~1058");
    }

    @Test
    void circleOneMillionMatchesMeasured() {
        // Measured: radius 1,000,000 circle enumerates 10,615,825 candidates.
        long est = circleRadius(1_000_000);
        assertTrue(Math.abs(est - 10_615_825L) <= 200_000L, "est " + est + " should be ~10.6M");
    }

    @Test
    void circleTenMillionIsRoughlyOneBillion() {
        long est = circleRadius(10_000_000);
        assertEquals(1_060_000_000L, est, 60_000_000L, "est " + est + " should be ~1.06B");
    }

    @Test
    void squareIsLargerThanCircle() {
        long square = squareRadius(1_000_000);
        long circle = circleRadius(1_000_000);
        assertTrue(square > circle, "square area (4r^2) > circle area (pi r^2)");
        // 4 / pi ≈ 1.27
        assertTrue(square < (long) (circle * 1.3), "square ~1.27x circle");
    }
}
