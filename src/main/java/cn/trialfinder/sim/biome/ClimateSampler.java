package cn.trialfinder.sim.biome;

/**
 * Supplies the 6 climate noise dimensions (temperature, humidity, continentalness, erosion, depth,
 * weirdness) at a quart position, exactly as {@code net.minecraft.world.level.biome.Climate.Sampler}.
 *
 * <p>In Minecraft these come from the {@code NoiseRouter} / {@code NoiseChunk} (a large data-driven
 * tree of {@code DensityFunction}s over {@code NormalNoise} primitives). Porting that router is the
 * remaining major work for an exact biome check (see the port report); until then no implementation
 * is wired and {@link BiomeChecker} reports that the climate sampler is unavailable.
 */
public interface ClimateSampler {

    /** @param quartX,quartY,quartZ quart-block coordinates (block / 4) */
    ClimateSample sample(int quartX, int quartY, int quartZ);

    /** True when the sampler is functional (i.e. the noise router port is wired). */
    boolean isAvailable();

    record ClimateSample(float temperature, float humidity, float continentalness,
                         float erosion, float depth, float weirdness) {
    }
}
