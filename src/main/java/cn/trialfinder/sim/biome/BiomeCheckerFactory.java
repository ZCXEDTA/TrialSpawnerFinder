package cn.trialfinder.sim.biome;

import cn.trialfinder.sim.biome.noise.OverworldNoiseRouter;

/**
 * Builds the {@link BiomeChecker}. The climate sampler comes from the ported
 * {@link OverworldNoiseRouter}; because the four spline dimensions are not yet exact, the checker's
 * {@link BiomeChecker#isAvailable()} is currently {@code false} and the CLI warns + skips the
 * filter rather than silently applying approximate biome values.
 */
public final class BiomeCheckerFactory {
    private BiomeCheckerFactory() {
    }

    /**
     * Builds a checker from an overworld noise router.
     *
     * @param includeApproxSplines when true the router fills the four spline dimensions with raw
     *                             shifted noise (deterministic, for framework testing); the checker
     *                             still reports unavailable until {@code isComplete()} is true.
     */
    public static BiomeChecker create(long worldSeed, boolean includeApproxSplines) {
        OverworldNoiseRouter router = OverworldNoiseRouter.create(worldSeed, includeApproxSplines);
        ClimateSampler sampler = new RouterClimateSampler(router);
        // Parameter list is empty until the overworld biome parameter table is extracted.
        MultiNoiseBiomeSource source = new MultiNoiseBiomeSource(null);
        return new BiomeChecker(sampler, source);
    }

    /** Default (framework-only) factory: no approximate splines, checker unavailable. */
    public static BiomeChecker create() {
        return create(0L, false);
    }
}
