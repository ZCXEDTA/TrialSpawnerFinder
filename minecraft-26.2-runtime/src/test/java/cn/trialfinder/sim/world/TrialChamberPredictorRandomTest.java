package cn.trialfinder.sim.world;

import cn.trialfinder.sim.math.BlockPos;
import cn.trialfinder.sim.random.LegacyRandomSource;
import cn.trialfinder.sim.random.RandomSource;
import cn.trialfinder.sim.random.WorldgenRandom;
import cn.trialfinder.sim.util.Util;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 纯 Java 随机源与连接器排序的对拍测试——替代原 HandTrialChamberPredictorTest。
 * 不再需要 Minecraft 官方 RNG 当 oracle，改用 sim.random 内部实现互相对拍。
 */
class TrialChamberPredictorRandomTest {

    @Test
    void nonAtomicRandomMatchesLegacyRandomSourceAcrossSeeds() {
        WorldgenRandom optimized = new WorldgenRandom(
                new TrialChamberPredictor.NonAtomicLegacyRandomSource(0L));
        long[] seeds = {0L, 1L, -1L, Long.MIN_VALUE, Long.MAX_VALUE, 9206294873968313284L};
        int[] chunks = {0, 1, -1, 6250, -6250, 1_875_000, -1_875_000};
        for (long seed : seeds) {
            for (int chunkX : chunks) {
                for (int chunkZ : chunks) {
                    WorldgenRandom reference = new WorldgenRandom(new LegacyRandomSource(0L));
                    reference.setLargeFeatureSeed(seed, chunkX, chunkZ);
                    optimized.setLargeFeatureSeed(seed, chunkX, chunkZ);
                    for (int draw = 1; draw <= 256; draw++) {
                        int bound = draw * 31;
                        assertEquals(reference.nextInt(bound), optimized.nextInt(bound));
                    }
                    assertEquals(reference.nextLong(), optimized.nextLong());
                    assertEquals(reference.nextDouble(), optimized.nextDouble());
                }
            }
        }
    }

    @Test
    void connectorOrderMatchesShuffleThenStableSort() {
        for (int size = 0; size <= 64; size++) {
            for (long seed = 0; seed < 100; seed++) {
                TrialChamberPredictor.Connector[] input = connectors(size, seed);
                RandomSource vanillaRandom = RandomSource.create(seed);
                RandomSource optimizedRandom = RandomSource.create(seed);

                List<TrialChamberPredictor.Connector> vanilla =
                        new ArrayList<>(Arrays.asList(input.clone()));
                Util.shuffle(vanilla, vanillaRandom);
                vanilla.sort(Comparator.comparingInt(
                        TrialChamberPredictor.Connector::selectionPriority).reversed());

                TrialChamberPredictor.Connector[] optimized = input.clone();
                TrialChamberPredictor.orderConnectors(optimized, optimizedRandom);

                assertArrayEquals(vanilla.toArray(), optimized);
                assertEquals(vanillaRandom.nextLong(), optimizedRandom.nextLong());
            }
        }
    }

    private static TrialChamberPredictor.Connector[] connectors(int size, long seed) {
        TrialChamberPredictor.Connector[] connectors =
                new TrialChamberPredictor.Connector[size];
        RandomSource priorities = RandomSource.create(seed ^ 0x5DEECE66DL);
        for (int index = 0; index < size; index++) {
            connectors[index] = new TrialChamberPredictor.Connector(
                    new BlockPos(index, 0, 0), null, null, null, null, null,
                    false, priorities.nextInt(5) - 2, 0);
        }
        return connectors;
    }
}
