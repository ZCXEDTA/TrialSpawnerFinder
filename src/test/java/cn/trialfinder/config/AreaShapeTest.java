package cn.trialfinder.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AreaShapeTest {
    @Test
    void squareIncludesTheFiveOrgCornerResultsExcludedByCircle() {
        long centerX = 81_579;
        long centerZ = 48_882;
        long radius = 128;
        List<long[]> squareOnly = List.of(
                new long[]{81_615, 48_755},
                new long[]{81_657, 49_009},
                new long[]{81_667, 48_999},
                new long[]{81_671, 48_973},
                new long[]{81_677, 48_968});

        assertEquals(5, squareOnly.stream()
                .filter(point -> AreaShape.SQUARE.contains(centerX, centerZ, point[0], point[1], radius))
                .count());
        assertEquals(0, squareOnly.stream()
                .filter(point -> AreaShape.CIRCLE.contains(centerX, centerZ, point[0], point[1], radius))
                .count());
    }
}
