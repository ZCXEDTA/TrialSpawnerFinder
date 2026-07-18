package cn.trialfinder.search;

import cn.minecraftfinder.core.BlockPoint;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
