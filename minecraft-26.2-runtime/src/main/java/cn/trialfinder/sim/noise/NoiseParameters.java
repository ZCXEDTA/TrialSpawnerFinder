package cn.trialfinder.sim.noise;

/**
 * 噪声参数（26.2 语义）：首八度与各八度振幅。
 * 对应原版 {@code worldgen/noise/*.json} 的 {@code firstOctave} 与 {@code amplitudes}。
 */
public record NoiseParameters(int firstOctave, double[] amplitudes) {

    public int size() {
        return this.amplitudes.length;
    }

    /** 返回指定八度索引处的振幅；超出范围返回 0。 */
    public double amplitudeAt(int octaveIndex) {
        return octaveIndex >= 0 && octaveIndex < this.amplitudes.length
                ? this.amplitudes[octaveIndex]
                : 0.0D;
    }
}
