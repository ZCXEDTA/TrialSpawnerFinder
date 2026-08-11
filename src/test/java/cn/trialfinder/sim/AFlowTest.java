package cn.trialfinder.sim;

import cn.trialfinder.sim.math.ChunkPos;
import cn.trialfinder.sim.structure.placement.RandomSpreadStructurePlacement;
import cn.trialfinder.sim.structure.placement.RandomSpreadType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A flow verification: the ported 34×34 grid placement must produce the same potential
 * structure chunks as the reference implementation (the finder's own TrialChamberCandidates,
 * which implements the same vanilla algorithm with its own LCG).
 */
class AFlowTest {

    private static final long[] SEEDS = {0L, 1L, 12345L, 9206294873968313284L, -1L};

    @Test
    void placementConstantsMatchStructureSetJson() {
        RandomSpreadStructurePlacement placement = new RandomSpreadStructurePlacement(34, 12, RandomSpreadType.LINEAR, 94_251_327);
        assertEquals(34, placement.spacing());
        assertEquals(12, placement.separation());
        assertEquals(94_251_327, placement.salt());
    }

    @Test
    void potentialChunkMatchesReferenceCandidate() {
        // Independent reference (report §5.1): setLargeFeatureWithSalt + 2x nextInt(22) via
        // java.util.Random, which shares the exact LCG.
        for (long seed : SEEDS) {
            for (int regionX : new int[]{-3, -1, 0, 1, 5, 34, -34, 100}) {
                for (int regionZ : new int[]{-2, 0, 3, 17, -17, 50}) {
                    ChunkPos mine = new RandomSpreadStructurePlacement(34, 12, RandomSpreadType.LINEAR, 94_251_327)
                            .getPotentialStructureChunkFromRegion(seed, regionX, regionZ);
                    long m = (long) regionX * 341_873_128_712L + (long) regionZ * 132_897_987_541L + seed + 94_251_327L;
                    java.util.Random jdk = new java.util.Random(m);
                    int expectedChunkX = regionX * 34 + jdk.nextInt(22);
                    int expectedChunkZ = regionZ * 34 + jdk.nextInt(22);
                    assertEquals(expectedChunkX, mine.x(), "chunk X for region " + regionX + "," + regionZ + " seed " + seed);
                    assertEquals(expectedChunkZ, mine.z(), "chunk Z for region " + regionX + "," + regionZ + " seed " + seed);
                }
            }
        }
    }

    @Test
    void enumerationBoundsAreDeterministic() {
        SimChamberGenerator generator = new SimChamberGenerator(java.nio.file.Path.of("src/main/resources"));
        List<ChunkPos> first = generator.enumeratePotentialChunks(12345L, -2000, 2000, -2000, 2000);
        List<ChunkPos> second = generator.enumeratePotentialChunks(12345L, -2000, 2000, -2000, 2000);
        assertEquals(first, second);
        assertEquals(56, first.size());
    }
}
