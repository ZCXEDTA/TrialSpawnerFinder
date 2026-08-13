package cn.trialfinder.search;

import cn.minecraftfinder.core.BlockPoint;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrialChamberCandidatesTest {
    @Test
    void candidateCalculationIsDeterministic() {
        BlockPoint first = TrialChamberCandidates.candidateInRegion(0, 0, 0);
        BlockPoint second = TrialChamberCandidates.candidateInRegion(0, 0, 0);

        assertEquals(first, second);
        assertEquals(new BlockPoint(232, 88), first);
    }

    @Test
    void negativeRegionsUseTheSameRandomSpreadRule() {
        assertEquals(new BlockPoint(-1080, -856),
                TrialChamberCandidates.candidateInRegion(-123, -2, -2));
    }

    @Test
    void allocationFreeRandomMatchesJavaRandom() {
        long seed = 9_206_294_873_968_313_284L;
        for (int regionX = -50; regionX <= 50; regionX++) {
            for (int regionZ = -50; regionZ <= 50; regionZ++) {
                long randomSeed = (long) regionX * 341_873_128_712L
                        + (long) regionZ * 132_897_987_541L + seed + 94_251_327L;
                Random random = new Random(randomSeed);
                int expectedX = (regionX * 34 + random.nextInt(22)) * 16 + 8;
                int expectedZ = (regionZ * 34 + random.nextInt(22)) * 16 + 8;
                assertEquals(new BlockPoint(expectedX, expectedZ),
                        TrialChamberCandidates.candidateInRegion(seed, regionX, regionZ));
            }
        }
    }

    @Test
    void enumerateInRectangleIsDeterministicAndBounded() {
        long seed = 0L;
        List<BlockPoint> first = TrialChamberCandidates.enumerate(seed, -10_000, 10_000, -10_000, 10_000);
        List<BlockPoint> second = TrialChamberCandidates.enumerate(seed, -10_000, 10_000, -10_000, 10_000);
        assertEquals(first, second);
        assertEquals(1_334, first.size(), "seed=0 10000 方形范围应有 1334 个候选（矩形枚举不圆过滤）");
        for (BlockPoint point : first) {
            assertTrue(point.x() >= -10_000 && point.x() <= 10_000
                            && point.z() >= -10_000 && point.z() <= 10_000,
                    "候选应在矩形范围内: " + point);
        }
    }

    @Test
    void enumerateInRectangleSortsByXY() {
        List<BlockPoint> points = TrialChamberCandidates.enumerate(0L, -100_000, 100_000, -100_000, 100_000);
        for (int i = 1; i < points.size(); i++) {
            BlockPoint prev = points.get(i - 1);
            BlockPoint curr = points.get(i);
            assertTrue(prev.compareTo(curr) <= 0, "候选应按 X 再按 Z 升序: " + prev + " > " + curr);
        }
    }
}
