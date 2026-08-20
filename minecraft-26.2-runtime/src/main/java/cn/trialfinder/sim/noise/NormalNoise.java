package cn.trialfinder.sim.noise;

import cn.trialfinder.sim.random.RandomSource;

/**
 * 复刻 {@code net.minecraft.world.level.levelgen.synth.NormalNoise}（26.2 语义）。
 * <ul>
 *   <li>构造：先 {@code PerlinNoise.create(random, ...)} 生成 {@code first}，再同参数
 *       {@code PerlinNoise.create(random, ...)} 生成 {@code second}——两者各 {@code forkPositional()}
 *       消费 2 个 long，顺序与官方一致。</li>
 *   <li>{@code valueFactor = 0.16666666666666666 / expectedDeviation(maxIdx - minIdx)}，
 *       其中 minIdx/maxIdx 是非零振幅的最小/最大索引。</li>
 *   <li>{@code getValue = (first(x,y,z) + second(x*1.0181268882175227, y*..., z*...)) * valueFactor}。</li>
 * </ul>
 */
public class NormalNoise {
    private static final double INPUT_FACTOR = 1.0181268882175227D;
    private static final double TARGET_DEVIATION = 0.16666666666666666D;

    private final double valueFactor;
    private final PerlinNoise first;
    private final PerlinNoise second;
    private final double maxValue;
    private final NoiseParameters parameters;

    private NormalNoise(RandomSource random, NoiseParameters params, boolean advance) {
        this.parameters = params;
        int firstOctave = params.firstOctave();
        double[] amps = params.amplitudes();
        if (advance) {
            this.first = PerlinNoise.create(random, firstOctave, amps);
            this.second = PerlinNoise.create(random, firstOctave, amps);
        } else {
            throw new IllegalArgumentException("legacy NormalNoise 未复刻");
        }
        int minIdx = Integer.MAX_VALUE;
        int maxIdx = Integer.MIN_VALUE;
        for (int i = 0; i < amps.length; i++) {
            if (amps[i] != 0.0D) {
                minIdx = Math.min(minIdx, i);
                maxIdx = Math.max(maxIdx, i);
            }
        }
        this.valueFactor = TARGET_DEVIATION / expectedDeviation(maxIdx - minIdx);
        this.maxValue = (this.first.maxValue() + this.second.maxValue()) * this.valueFactor;
    }

    public static NormalNoise create(RandomSource random, NoiseParameters params) {
        return new NormalNoise(random, params, true);
    }

    private static double expectedDeviation(int i) {
        return 0.1D * (1.0D + 1.0D / (double) (i + 1));
    }

    public double getValue(double x, double y, double z) {
        return (this.first.getValue(x, y, z)
                + this.second.getValue(x * INPUT_FACTOR, y * INPUT_FACTOR, z * INPUT_FACTOR)) * this.valueFactor;
    }

    public double maxValue() {
        return this.maxValue;
    }

    public NoiseParameters parameters() {
        return this.parameters;
    }
}
