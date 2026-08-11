package cn.trialfinder.sim.biome.noise;

import cn.trialfinder.sim.random.PositionalRandomFactory;
import cn.trialfinder.sim.random.RandomSource;

/**
 * Port of the noise-seeding path used by RandomState / Noises.instantiate (1.21.11):
 * {@code RandomSource.create(worldSeed).forkPositional().fromHashOf(key)} yields the RandomSource
 * from which a {@link NormalNoise} is created from its {@link NoiseParameters}.
 */
public final class ClimateNoiseSeeder {
    private ClimateNoiseSeeder() {
    }

    /** The classic overworld climate noise parameters (firstOctave=0, amplitude 1.5). */
    public static final NoiseParameters CLIMATE_NOISE = new NoiseParameters(0, 1.5);

    /** Creates a NormalNoise for the given noise key id from a world seed. */
    public static NormalNoise createNoise(long worldSeed, String noiseKey) {
        PositionalRandomFactory positional = RandomSource.create(worldSeed).forkPositional();
        RandomSource random = positional.fromHashOf(noiseKey);
        return new NormalNoise(random, CLIMATE_NOISE);
    }
}
