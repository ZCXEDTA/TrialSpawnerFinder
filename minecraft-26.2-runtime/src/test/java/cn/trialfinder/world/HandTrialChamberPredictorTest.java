package cn.trialfinder.world;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class HandTrialChamberPredictorTest {
    @Test
    void nonAtomicRandomMatchesVanillaAcrossRepeatedStructureSeeds() {
        WorldgenRandom optimized = new WorldgenRandom(
                new HandTrialChamberPredictor.NonAtomicLegacyRandomSource(0L));
        long[] seeds = {0L, 1L, -1L, Long.MIN_VALUE, Long.MAX_VALUE, 9206294873968313284L};
        int[] chunks = {0, 1, -1, 6250, -6250, 1_875_000, -1_875_000};
        for (long seed : seeds) {
            for (int chunkX : chunks) {
                for (int chunkZ : chunks) {
                    WorldgenRandom vanilla = new WorldgenRandom(new LegacyRandomSource(0L));
                    vanilla.setLargeFeatureSeed(seed, chunkX, chunkZ);
                    optimized.setLargeFeatureSeed(seed, chunkX, chunkZ);
                    for (int draw = 1; draw <= 256; draw++) {
                        int bound = draw * 31;
                        assertEquals(vanilla.nextInt(bound), optimized.nextInt(bound));
                    }
                    assertEquals(vanilla.nextLong(), optimized.nextLong());
                    assertEquals(vanilla.nextDouble(), optimized.nextDouble());
                }
            }
        }
    }

    @Test
    void optimizedConnectorOrderMatchesVanillaAndPreservesRandomState() {
        for (int size = 0; size <= 64; size++) {
            for (long seed = 0; seed < 100; seed++) {
                HandTrialChamberPredictor.Connector[] input = connectors(size, seed);
                RandomSource vanillaRandom = RandomSource.create(seed);
                RandomSource optimizedRandom = RandomSource.create(seed);

                List<HandTrialChamberPredictor.Connector> vanilla =
                        new ArrayList<>(Arrays.asList(input.clone()));
                Util.shuffle(vanilla, vanillaRandom);
                vanilla.sort(Comparator.comparingInt(
                        HandTrialChamberPredictor.Connector::selectionPriority).reversed());

                HandTrialChamberPredictor.Connector[] optimized = input.clone();
                HandTrialChamberPredictor.orderConnectors(optimized, optimizedRandom);

                assertArrayEquals(vanilla.toArray(HandTrialChamberPredictor.Connector[]::new), optimized);
                assertEquals(vanillaRandom.nextLong(), optimizedRandom.nextLong());
            }
        }
    }

    private static HandTrialChamberPredictor.Connector[] connectors(int size, long seed) {
        HandTrialChamberPredictor.Connector[] connectors =
                new HandTrialChamberPredictor.Connector[size];
        RandomSource priorities = RandomSource.create(seed ^ 0x5DEECE66DL);
        for (int index = 0; index < size; index++) {
            connectors[index] = new HandTrialChamberPredictor.Connector(
                    new BlockPos(index, 0, 0), null, null, null, null, null,
                    false, priorities.nextInt(5) - 2, 0);
        }
        return connectors;
    }
}
