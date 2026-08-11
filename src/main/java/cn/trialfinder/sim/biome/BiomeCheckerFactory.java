package cn.trialfinder.sim.biome;

import cn.trialfinder.sim.biome.noise.OverworldNoiseRouter;

/**
 * Builds the {@link BiomeChecker}. The climate sampler comes from the ported
 * {@link OverworldNoiseRouter} (temperature/humidity exact; the four terrain spline dimensions use
 * deterministic shifted-noise stand-ins), and the parameter list from
 * {@link OverworldBiomeParameters} (a coarse land-vs-ocean subset of the overworld table).
 *
 * <p><b>Approximate, usable:</b> {@link BiomeChecker#isAvailable()} is {@code true}; the check
 * reliably excludes oceans / deep oceans / beaches (biomes that never host trial chambers) while
 * keeping the broad land set. It is NOT bit-exact with the game — a land coordinate may resolve to
 * a slightly different land biome than the server would, but since the trial-chambers tag covers
 * essentially all land, the pass/fail decision is correct for the practical case.
 */
public final class BiomeCheckerFactory {
    private BiomeCheckerFactory() {
    }

    /**
     * Builds a checker with the approximate-but-usable router and parameter list.
     *
     * @param includeApproxSplines when true the router fills the four spline dimensions with the
     *                             deterministic shifted-noise stand-ins (required for availability);
     *                             when false the router stays incomplete and the checker unavailable.
     */
    public static BiomeChecker create(long worldSeed, boolean includeApproxSplines) {
        OverworldNoiseRouter router = OverworldNoiseRouter.create(worldSeed, includeApproxSplines);
        ClimateSampler sampler = new RouterClimateSampler(router);
        MultiNoiseBiomeSource source = new MultiNoiseBiomeSource(OverworldBiomeParameters.create());
        return new BiomeChecker(sampler, source);
    }

    /** Default: approximate splines on, checker usable (the CLI {@code --biome-check} path). */
    public static BiomeChecker create() {
        return create(0L, true);
    }
}
