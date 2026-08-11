package cn.trialfinder.sim;

import cn.trialfinder.sim.random.LegacyRandomSource;
import cn.trialfinder.sim.random.RandomSource;
import cn.trialfinder.sim.random.WorldgenRandom;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies that the ported Legacy LCG is bit-exact with java.util.Random (same LCG constants,
 * same rejection sampling), and that WorldgenRandom's structure-seeding helpers consume the
 * RNG exactly like the vanilla implementation.
 */
class RngExactnessTest {

    @Test
    void legacyMatchesJavaUtilRandom() {
        for (long seed : new long[]{0L, 1L, 12345L, -1L, Long.MAX_VALUE, 9206294873968313284L}) {
            Random jdk = new Random(seed);
            RandomSource sim = new LegacyRandomSource(seed);
            for (int bound : new int[]{1, 2, 3, 22, 34, 100, 4096}) {
                for (int i = 0; i < 50; i++) {
                    assertEquals(jdk.nextInt(bound), sim.nextInt(bound), "nextInt(" + bound + ") seed " + seed);
                }
            }
            for (int i = 0; i < 50; i++) {
                assertEquals(jdk.nextLong(), sim.nextLong(), "nextLong seed " + seed);
            }
            for (int i = 0; i < 50; i++) {
                assertEquals(jdk.nextInt(), sim.nextInt(), "nextInt() seed " + seed);
            }
        }
    }

    @Test
    void setLargeFeatureWithSaltIsStable() {
        // Cross-check the placement seed derivation against the reference algorithm
        // (regionX*341873128712 + regionZ*132897987541 + seed + salt, then 2x nextInt(22)).
        long seed = 12345L;
        WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(0L));
        for (int regionX : new int[]{-5, 0, 1, 34, -34}) {
            for (int regionZ : new int[]{-5, 0, 7, 42}) {
                random.setLargeFeatureWithSalt(seed, regionX, regionZ, 94_251_327);
                int ox = random.nextInt(22);
                int oz = random.nextInt(22);

                long m = (long) regionX * 341_873_128_712L + (long) regionZ * 132_897_987_541L + seed + 94_251_327L;
                Random jdk = new Random(m); // java.util.Random applies (seed ^ MULT) & MASK like LegacyRandomSource
                assertEquals(jdk.nextInt(22), ox, "offset x for region " + regionX + "," + regionZ);
                assertEquals(jdk.nextInt(22), oz, "offset z for region " + regionX + "," + regionZ);
            }
        }
    }
}
