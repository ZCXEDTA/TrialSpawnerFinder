package cn.trialfinder.sim.biome.noise;

import cn.trialfinder.sim.random.RandomSource;

import java.util.List;

/**
 * Port of net.minecraft.world.level.levelgen.synth.PerlinNoise (1.21.11).
 *
 * <p>A stack of {@link ImprovedNoise} octaves. Each octave's input is scaled by a per-octave
 * frequency and its contribution weighted by the amplitude; octaves whose index (firstOctave+i)
 * is negative consume RNG but contribute no noise (vanilla {@code skipOctave}). The frequency and
 * value factors follow the vanilla constructor:
 * {@code lowestFreqInputFactor = sum(amp * 2^(firstOctave+i))} and
 * {@code lowestFreqValueFactor = sum(amp)}.
 */
public class PerlinNoise {
    private final ImprovedNoise[] noiseLevels;
    private final int firstOctave;
    private final List<Double> amplitudes;
    private final double lowestFreqValueFactor;
    private final double lowestFreqInputFactor;
    private final double maxValue;

    public PerlinNoise(RandomSource random, NoiseParameters parameters) {
        this(random, parameters.firstOctave(), parameters.amplitudes());
    }

    public PerlinNoise(RandomSource random, int firstOctave, List<Double> amplitudes) {
        this.firstOctave = firstOctave;
        this.amplitudes = amplitudes;
        this.noiseLevels = new ImprovedNoise[amplitudes.size()];
        for (int i = 0; i < amplitudes.size(); i++) {
            if (firstOctave + i < 0) {
                skipOctave(random);
                this.noiseLevels[i] = null;
            } else {
                this.noiseLevels[i] = new ImprovedNoise(random);
            }
        }

        double max = 0.0;
        double valueSum = 0.0;
        double inputSum = 0.0;
        for (int i = 0; i < amplitudes.size(); i++) {
            double amp = amplitudes.get(i);
            max += amp;
            valueSum += amp;
            inputSum += amp * Math.pow(2.0, firstOctave + i);
        }
        this.maxValue = max;
        this.lowestFreqValueFactor = valueSum;
        this.lowestFreqInputFactor = inputSum;
    }

    private static void skipOctave(RandomSource random) {
        new ImprovedNoise(random);
    }

    public double getValue(double x, double y, double z) {
        return this.getValue(x, y, z, 0.0, 0.0, false);
    }

    /**
     * Vanilla 6-arg getValue: each octave's noise is sampled at
     * {@code (x*f, y*f, z*f, yScale*f, yMax*f)} and weighted by {@code amp * valueFactor}, with both
     * {@code f} and {@code valueFactor} doubling each octave.
     */
    public double getValue(double x, double y, double z, double yScale, double yMax, boolean useY) {
        double value = 0.0;
        double valueFactor = this.lowestFreqValueFactor;
        double freq = this.lowestFreqInputFactor;
        for (int i = 0; i < this.noiseLevels.length; i++) {
            ImprovedNoise noise = this.noiseLevels[i];
            if (noise != null) {
                double f = freq;
                value += this.amplitudes.get(i) * valueFactor * noise.noise(
                        x * f, y * f, z * f, yScale * f, yMax * f);
            }
            freq *= 2.0;
            valueFactor *= 2.0;
        }
        return value;
    }

    public ImprovedNoise getOctaveNoise(int index) {
        return this.noiseLevels[index];
    }

    public double maxValue() {
        return this.maxValue;
    }
}
