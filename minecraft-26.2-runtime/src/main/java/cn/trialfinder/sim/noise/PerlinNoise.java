package cn.trialfinder.sim.noise;

import cn.trialfinder.sim.math.Mth;
import cn.trialfinder.sim.random.PositionalRandomFactory;
import cn.trialfinder.sim.random.RandomSource;

/**
 * 复刻 {@code net.minecraft.world.level.levelgen.synth.PerlinNoise}（26.2 语义）。
 * <ul>
 *   <li>构造（advance=true，NormalNoise 使用）：{@code positional = random.forkPositional()}；
 *       对每个非零振幅八度 {@code i}：{@code noiseLevels[i] = new ImprovedNoise(
 *       positional.fromHashOf("octave_" + (firstOctave + i)))}。</li>
 *   <li>{@code lowestFreqInputFactor = 2.0^firstOctave}；{@code lowestFreqValueFactor =
 *       2.0^(size-1) / (2.0^size - 1.0)}；{@code maxValue = edgeValue(2.0)}。</li>
 *   <li>{@code getValue(x,y,z,yScale,yMax)}：逐八度累加 {@code amp[i] * level.noise(...) * valueFactor}，
 *       输入与 yScale/yMax 都先经 {@link #wrap} 折回。</li>
 * </ul>
 */
public class PerlinNoise {
    private final ImprovedNoise[] noiseLevels;
    private final int firstOctave;
    private final double[] amplitudes;
    private final double lowestFreqValueFactor;
    private final double lowestFreqInputFactor;
    private final double maxValue;

    protected PerlinNoise(RandomSource random, int firstOctave, double[] amplitudes, boolean advance) {
        this.firstOctave = firstOctave;
        this.amplitudes = amplitudes;
        int size = amplitudes.length;
        this.noiseLevels = new ImprovedNoise[size];
        if (advance) {
            PositionalRandomFactory positional = random.forkPositional();
            for (int i = 0; i < size; i++) {
                if (amplitudes[i] != 0.0D) {
                    int octave = firstOctave + i;
                    this.noiseLevels[i] = new ImprovedNoise(positional.fromHashOf("octave_" + octave));
                }
            }
        }
        this.lowestFreqInputFactor = Math.pow(2.0D, firstOctave);
        this.lowestFreqValueFactor = Math.pow(2.0D, size - 1) / (Math.pow(2.0D, size) - 1.0D);
        this.maxValue = this.edgeValue(2.0D);
    }

    public static PerlinNoise create(RandomSource random, int firstOctave, double[] amplitudes) {
        return new PerlinNoise(random, firstOctave, amplitudes, true);
    }

    public double getValue(double x, double y, double z) {
        return this.getValue(x, y, z, 0.0D, 0.0D);
    }

    public double getValue(double x, double y, double z, double yScale, double yMax) {
        double d11 = 0.0D;
        double d13 = this.lowestFreqInputFactor;
        double d15 = this.lowestFreqValueFactor;
        for (int i = 0; i < this.noiseLevels.length; i++) {
            ImprovedNoise level = this.noiseLevels[i];
            if (level != null) {
                double d19 = level.noise(wrap(x * d13), wrap(y * d13), wrap(z * d13),
                        wrap(yScale * d13), wrap(yMax * d13));
                d11 += this.amplitudes[i] * d19 * d15;
            }
            d13 *= 2.0D;
            d15 /= 2.0D;
        }
        return d11;
    }

    public double maxValue() {
        return this.maxValue;
    }

    protected double edgeValue(double d) {
        double d1 = 0.0D;
        double d2 = this.lowestFreqValueFactor;
        for (int i = 0; i < this.noiseLevels.length; i++) {
            if (this.noiseLevels[i] != null) {
                d1 += this.amplitudes[i] * d * d2;
            }
            d2 /= 2.0D;
        }
        return d1;
    }

    /** 把输入折回 [-2^25, 2^25] 区间，避免大坐标溢出。 */
    public static double wrap(double value) {
        return value - (double) Mth.lfloor(value / 3.3554432E7D + 0.5D) * 3.3554432E7D;
    }
}
