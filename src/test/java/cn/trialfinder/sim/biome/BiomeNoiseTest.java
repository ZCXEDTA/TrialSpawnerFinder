package cn.trialfinder.sim.biome;

import cn.trialfinder.sim.biome.noise.ImprovedNoise;
import cn.trialfinder.sim.biome.noise.OverworldNoiseRouter;
import cn.trialfinder.sim.biome.noise.PerlinNoise;
import cn.trialfinder.sim.biome.noise.SimplexNoise;
import cn.trialfinder.sim.random.LegacyRandomSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the ported biome/noise foundation:
 * <ul>
 *   <li>{@link ImprovedNoise} and {@link SimplexNoise} are deterministic given the seed (their
 *       permutation tables are derived from {@link LegacyRandomSource}, the same LCG the game
 *       uses), and change with the seed;</li>
 *   <li>{@link Climate} nearest-parameter-point matching picks the closest point;</li>
 *   <li>{@link BiomeChecker} is correctly reported unavailable until the climate sampler is wired.</li>
 * </ul>
 */
class BiomeNoiseTest {

    @Test
    void improvedNoiseIsDeterministicAndSeedDependent() {
        ImprovedNoise a1 = new ImprovedNoise(new LegacyRandomSource(42L));
        ImprovedNoise a2 = new ImprovedNoise(new LegacyRandomSource(42L));
        ImprovedNoise b = new ImprovedNoise(new LegacyRandomSource(43L));

        double v = a1.noise(0.5, 0.25, -0.75);
        assertEquals(v, a2.noise(0.5, 0.25, -0.75), 0.0, "same seed -> same noise");
        assertNotEquals(v, b.noise(0.5, 0.25, -0.75), "different seed -> different noise");
    }

    @Test
    void simplexNoiseIsDeterministicAndSeedDependent() {
        SimplexNoise a1 = new SimplexNoise(new LegacyRandomSource(42L));
        SimplexNoise a2 = new SimplexNoise(new LegacyRandomSource(42L));
        SimplexNoise b = new SimplexNoise(new LegacyRandomSource(43L));

        double v = a1.getValue(0.5, 0.25, -0.75);
        assertEquals(v, a2.getValue(0.5, 0.25, -0.75), 0.0, "same seed -> same simplex noise");
        assertNotEquals(v, b.getValue(0.5, 0.25, -0.75), "different seed -> different noise");
        // 2D variant deterministic too.
        assertEquals(a1.getValue(1.0, 2.0), a2.getValue(1.0, 2.0), 0.0);
    }

    @Test
    void climateFindsNearestParameterPoint() {
        Climate.ParameterPoint desert = Climate.ParameterPoint.parameters(
                0.4f, 2.0f, -0.5f, 0.5f, 0.1f, 0.9f, 0.2f, 1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
        Climate.ParameterPoint snowy = Climate.ParameterPoint.parameters(
                -1.0f, 0.4f, -0.5f, 0.5f, -0.5f, 0.5f, -0.5f, 0.5f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
        Climate.ParameterList<String> list = new Climate.ParameterList<>(java.util.List.of(
                new Climate.Entry<>(desert, "minecraft:desert"),
                new Climate.Entry<>(snowy, "minecraft:snowy_plains")));

        assertEquals("minecraft:desert",
                list.findNearest(Climate.TargetPoint.of(1.5f, 0.0f, 0.5f, 0.6f, 0.0f, 0.0f)).orElseThrow());
        assertEquals("minecraft:snowy_plains",
                list.findNearest(Climate.TargetPoint.of(-0.8f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f)).orElseThrow());
    }

    @Test
    void trialChambersBiomesContainsKnownEntries() {
        assertTrue(TrialChambersBiomes.contains("minecraft:deep_dark"));
        assertTrue(TrialChambersBiomes.contains("minecraft:plains"));
        assertFalse(TrialChambersBiomes.contains("minecraft:nether_wastes"));
    }

    @Test
    void biomeCheckerIsAvailableWithApproximateSplines() {
        // The default factory now uses the deterministic approximate splines + the land-vs-ocean
        // parameter subset, so --biome-check is usable (it must not throw).
        BiomeChecker checker = cn.trialfinder.sim.biome.BiomeCheckerFactory.create();
        assertTrue(checker.isAvailable(), "approximate router + parameter list must be available");
        // A land candidate near spawn resolves to a biome; the check runs without throwing.
        checker.isTrialChambersValid(12345L, 0, 0);
    }

    @Test
    void approximateBiomeCheckRunsWithoutThrowingAcrossCoordinates() {
        BiomeChecker checker = cn.trialfinder.sim.biome.BiomeCheckerFactory.create();
        // Sweep a small grid of chunk coordinates: the checker must not throw and must return a
        // boolean (the approximate parameter list covers all land + ocean regions).
        int hits = 0;
        for (int cx = -20; cx <= 20; cx += 4) {
            for (int cz = -20; cz <= 20; cz += 4) {
                if (checker.isTrialChambersValid(188188L, cx, cz)) {
                    hits++;
                }
            }
        }
        // At least some land coordinates must pass (the broad trial-chambers land set).
        assertTrue(hits > 0, "some land candidates must be valid, got " + hits);
    }

    @Test
    void overworldNoiseRouterIsDeterministicAndSeedDependent() {
        // Router with approximate splines: deterministic for the same seed, changes with the seed.
        var a1 = OverworldNoiseRouter.create(42L, true).router();
        var a2 = OverworldNoiseRouter.create(42L, true).router();
        var b = OverworldNoiseRouter.create(43L, true).router();

        double[] s1 = a1.sample(100.0, 0.0, 200.0);
        double[] s2 = a2.sample(100.0, 0.0, 200.0);
        double[] s3 = b.sample(100.0, 0.0, 200.0);
        org.junit.jupiter.api.Assertions.assertArrayEquals(s1, s2, 0.0, "same seed -> same climate");
        assertNotEquals(s1[0], s3[0], "temperature differs with seed");
        assertNotEquals(s1[1], s3[1], "humidity differs with seed");
    }

    @Test
    void perlinNoiseIsDeterministicAndSeedDependent() {
        cn.trialfinder.sim.biome.noise.NoiseParameters params =
                new cn.trialfinder.sim.biome.noise.NoiseParameters(0, 1.5);
        PerlinNoise a1 = new PerlinNoise(new LegacyRandomSource(7L), params);
        PerlinNoise a2 = new PerlinNoise(new LegacyRandomSource(7L), params);
        PerlinNoise b = new PerlinNoise(new LegacyRandomSource(8L), params);
        double v = a1.getValue(0.5, 0.25, -0.75);
        assertEquals(v, a2.getValue(0.5, 0.25, -0.75), 0.0, "same seed -> same perlin");
        assertNotEquals(v, b.getValue(0.5, 0.25, -0.75), "different seed -> different perlin");
    }

    @Test
    void routerClimateSamplerReflectsCompleteness() {
        var router = OverworldNoiseRouter.create(1L, false);
        RouterClimateSampler sampler = new RouterClimateSampler(router);
        assertFalse(sampler.isAvailable(), "router is not complete -> sampler unavailable");
    }
}
