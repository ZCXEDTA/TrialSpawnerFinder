package cn.trialfinder.sim.biome.noise;

import cn.trialfinder.sim.random.PositionalRandomFactory;
import cn.trialfinder.sim.random.RandomSource;

/**
 * Port of the Overworld climate router composition (1.21.11) — the 6 climate dimensions.
 *
 * <p><b>Status:</b>
 * <ul>
 *   <li><b>temperature</b> and <b>vegetation (humidity)</b> are composed exactly like the game:
 *       {@code shiftedNoise2d(SHIFT_X, SHIFT_Z, 0.25, TEMPERATURE|VEGETATION)} over the standard
 *       overworld climate noise.</li>
 *   <li><b>continentalness / erosion / weirdness / depth</b> in the game are
 *       {@code DensityFunctions.spline(...)} built from the large/regular noise pair via the
 *       {@code TerrainProvider} spline tables (overworldOffset / overworldFactor /
 *       overworldJaggedness). Those tables are a large data set not yet extracted, so these four
 *       dimensions currently use the raw (shifted) regular noise as a deterministic stand-in.</li>
 * </ul>
 *
 * <p>Consequently {@link #isComplete()} is {@code false} until the spline tables are wired; the
 * {@code BiomeChecker} keeps its guard and does not silently filter on approximate values.
 */
public final class OverworldNoiseRouter {

    /** The "shift" noise key used by SHIFT_X / SHIFT_Z. */
    public static final String SHIFT_KEY = "minecraft:offset";

    private final boolean complete;
    private final NoiseRouter router;

    private OverworldNoiseRouter(boolean complete, NoiseRouter router) {
        this.complete = complete;
        this.router = router;
    }

    /**
     * Builds the router. When {@code includeApproxSplines} is true, the four spline dimensions use
     * the raw shifted noise as a deterministic stand-in (for framework testing); when false, they
     * are constant 0 and {@link #isComplete()} is false.
     */
    public static OverworldNoiseRouter create(long worldSeed, boolean includeApproxSplines) {
        PositionalRandomFactory positional = RandomSource.create(worldSeed).forkPositional();

        NormalNoise temperature = createNoise(positional, "minecraft:temperature");
        NormalNoise vegetation = createNoise(positional, "minecraft:vegetation");
        NormalNoise continentalness = createNoise(positional, "minecraft:continentalness");
        NormalNoise erosion = createNoise(positional, "minecraft:erosion");
        NormalNoise weirdness = createNoise(positional, "minecraft:ridge");
        NormalNoise depth = createNoise(positional, "minecraft:depth");

        // SHIFT_X / SHIFT_Z: shiftedNoise2d(ZERO, Y, 0.0, SHIFT) / shiftedNoise2d(ZERO, Y, 0.0, SHIFT_ALT)
        NormalNoise shift = createNoise(positional, SHIFT_KEY);
        DensityFunction shiftX = DensityFunction.shiftedNoise2d(
                DensityFunction.constant(0.0), DensityFunction.constant(0.0), 0.25, shift);
        DensityFunction shiftZ = DensityFunction.shiftedNoise2d(
                DensityFunction.constant(0.0), DensityFunction.constant(0.0), 0.25, shift);

        DensityFunction tempFn = DensityFunction.shiftedNoise2d(shiftX, shiftZ, 0.25, temperature);
        DensityFunction vegFn = DensityFunction.shiftedNoise2d(shiftX, shiftZ, 0.25, vegetation);

        if (!includeApproxSplines) {
            return new OverworldNoiseRouter(false, new NoiseRouter(
                    tempFn, vegFn,
                    DensityFunction.constant(0.0),
                    DensityFunction.constant(0.0),
                    DensityFunction.constant(0.0),
                    DensityFunction.constant(0.0)));
        }

        DensityFunction contFn = DensityFunction.shiftedNoise2d(shiftX, shiftZ, 0.25, continentalness);
        DensityFunction eroFn = DensityFunction.shiftedNoise2d(shiftX, shiftZ, 0.25, erosion);
        DensityFunction weiFn = DensityFunction.shiftedNoise2d(shiftX, shiftZ, 0.25, weirdness);
        DensityFunction depFn = DensityFunction.shiftedNoise2d(shiftX, shiftZ, 0.25, depth);
        return new OverworldNoiseRouter(false, new NoiseRouter(tempFn, vegFn, contFn, eroFn, depFn, weiFn));
    }

    public NoiseRouter router() {
        return this.router;
    }

    /**
     * True only when every climate dimension is the exact game composition. Currently always false:
     * the TerrainProvider spline tables for continentalness/erosion/weirdness/depth are not ported.
     */
    public boolean isComplete() {
        return this.complete;
    }

    private static NormalNoise createNoise(PositionalRandomFactory positional, String key) {
        RandomSource random = positional.fromHashOf(key);
        return new NormalNoise(random, ClimateNoiseSeeder.CLIMATE_NOISE);
    }
}
