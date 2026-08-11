package cn.trialfinder.sim.biome.noise;

import cn.trialfinder.sim.random.RandomSource;

/**
 * Port of net.minecraft.world.level.levelgen.synth.NormalNoise (1.21.11).
 * A {@link PerlinNoise} pair with a value factor that normalizes the output to roughly [-1, 1].
 */
public class NormalNoise {
    private static final double INPUT_FACTOR = 1.0 / 16.0;
    private static final double TARGET_DEVIATION = 0.2;
    private static final double MAX_VALUE = 1.5;

    private final double valueFactor;
    private final PerlinNoise first;
    private final PerlinNoise second;
    private final double maxValue;
    private final NoiseParameters parameters;

    public NormalNoise(RandomSource random, NoiseParameters parameters) {
        this.parameters = parameters;
        this.first = new PerlinNoise(random, parameters);
        this.second = new PerlinNoise(random, parameters);
        double factor = Math.pow(2.0, parameters.firstOctave() - 1);
        double base = 0.0;
        for (int i = 0; i < parameters.amplitudes().size(); i++) {
            double amp = parameters.amplitudes().get(i);
            base += amp / Math.pow(2.0, i);
        }
        this.valueFactor = INPUT_FACTOR / base * factor;
        this.maxValue = MAX_VALUE * Math.max(0.5, this.valueFactor) * 1.2;
    }

    public double getValue(double x, double y, double z) {
        return this.getValue(x, y, z, 0.0, 0.0, false);
    }

    public double getValue(double x, double y, double z, double yScale, double yMax, boolean useY) {
        double v = this.first.getValue(x * INPUT_FACTOR, y * INPUT_FACTOR, z * INPUT_FACTOR,
                yScale * INPUT_FACTOR, yMax * INPUT_FACTOR, useY);
        double w = this.second.getValue(x * INPUT_FACTOR, y * INPUT_FACTOR, z * INPUT_FACTOR,
                yScale * INPUT_FACTOR, yMax * INPUT_FACTOR, useY);
        return this.valueFactor * v + w;
    }

    public NoiseParameters parameters() {
        return this.parameters;
    }

    public double maxValue() {
        return this.maxValue;
    }
}
