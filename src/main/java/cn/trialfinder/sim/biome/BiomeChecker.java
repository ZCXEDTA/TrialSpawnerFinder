package cn.trialfinder.sim.biome;

import java.util.Optional;

/**
 * Checks whether a candidate trial-chamber chunk is in a biome that allows the structure to
 * generate, replicating the server's
 * {@code Structure.checkStructureBiome → ChunkGenerator.getNoiseBiome → MultiNoiseBiomeSource} path.
 *
 * <p>Pipeline:
 * <ol>
 *   <li>derive the 6 climate noise dimensions at the chunk's start quart position via a
 *       {@link ClimateSampler};</li>
 *   <li>map the climate sample to a biome via {@link MultiNoiseBiomeSource};</li>
 *   <li>check the biome against {@link TrialChambersBiomes}.</li>
 * </ol>
 *
 * <p><b>Current status:</b> the {@link ClimateSampler} (which in-game is the
 * {@code NoiseRouter}/{@code NoiseChunk} data-driven tree of {@code DensityFunction}s over the
 * {@linkplain cn.trialfinder.sim.biome.noise.ImprovedNoise noise primitives}) is <b>not yet ported</b>.
 * {@link #isTrialChambersValid} therefore throws {@link UnsupportedOperationException} with a clear
 * message until a sampler is supplied. The CLI {@code --biome-check} catches this and skips the
 * check with a warning rather than silently returning wrong results.
 */
public final class BiomeChecker {

    private final ClimateSampler sampler;
    private final MultiNoiseBiomeSource biomeSource;

    public BiomeChecker(ClimateSampler sampler, MultiNoiseBiomeSource biomeSource) {
        this.sampler = sampler;
        this.biomeSource = biomeSource;
    }

    /** True when the full pipeline is wired (sampler available and parameter list populated). */
    public boolean isAvailable() {
        return this.sampler != null && this.sampler.isAvailable() && this.biomeSource.isAvailable();
    }

    /**
     * Returns whether the trial-chamber structure may generate at the given chunk.
     * The server samples the biome at the chunk start position (quart coords = block/4).
     */
    public boolean isTrialChambersValid(long seed, int chunkX, int chunkZ) {
        if (!this.isAvailable()) {
            throw new UnsupportedOperationException(
                    "BiomeChecker is not functional: the climate sampler (NoiseRouter/DensityFunctions) "
                            + "and/or the overworld biome parameter list is not wired yet. See the biome port report.");
        }
        int quartX = (chunkX * 16) >> 2;
        int quartY = 0;
        int quartZ = (chunkZ * 16) >> 2;
        Optional<String> biome = this.biomeSource.getNoiseBiome(quartX, quartY, quartZ, this.sampler);
        return biome.isPresent() && TrialChambersBiomes.contains(biome.get());
    }
}
