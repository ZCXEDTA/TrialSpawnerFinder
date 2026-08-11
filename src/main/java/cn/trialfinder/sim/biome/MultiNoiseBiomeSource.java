package cn.trialfinder.sim.biome;

import java.util.Optional;

/**
 * Port of net.minecraft.world.level.biome.MultiNoiseBiomeSource (1.21.11) — the
 * {@code getNoiseBiome} logic that maps a climate sample to the nearest parameter point.
 *
 * <p>The biome parameter list (overworld preset) is data-driven in the game
 * ({@code MultiNoiseBiomeSourceParameterList$Preset.OVERWORLD}, built from the biome registry).
 * It is supplied to this class at construction; until it is populated the source reports
 * {@link #isAvailable()}{@code == false}.
 */
public final class MultiNoiseBiomeSource {
    private final Climate.ParameterList<String> parameters;

    public MultiNoiseBiomeSource(Climate.ParameterList<String> parameters) {
        this.parameters = parameters;
    }

    public boolean isAvailable() {
        return this.parameters != null && !this.parameters.entries().isEmpty();
    }

    /** Returns the biome id at the given quart position, or empty when unavailable/no match. */
    public Optional<String> getNoiseBiome(int quartX, int quartY, int quartZ, ClimateSampler sampler) {
        ClimateSampler.ClimateSample sample = sampler.sample(quartX, quartY, quartZ);
        Climate.TargetPoint target = Climate.TargetPoint.of(
                sample.temperature(), sample.humidity(), sample.continentalness(),
                sample.erosion(), sample.depth(), sample.weirdness());
        return this.parameters.findNearest(target);
    }
}
