package cn.trialfinder.sim.random;

/**
 * 复刻 {@code net.minecraft.world.level.levelgen.MarsagliaPolarGaussian}（26.2 语义）。
 * 基于 Box–Muller 的 Marsaglia 极坐标法生成高斯分布。
 */
public class MarsagliaPolarGaussian {
    private final RandomSource randomSource;
    private double nextGaussian;
    private boolean hasNextGaussian;

    public MarsagliaPolarGaussian(RandomSource randomSource) {
        this.randomSource = randomSource;
    }

    public void reset() {
        this.hasNextGaussian = false;
    }

    public double nextGaussian() {
        if (this.hasNextGaussian) {
            this.hasNextGaussian = false;
            return this.nextGaussian;
        }
        double d0;
        double d1;
        double d2;
        do {
            d0 = 2.0 * this.randomSource.nextDouble() - 1.0;
            d1 = 2.0 * this.randomSource.nextDouble() - 1.0;
            d2 = d0 * d0 + d1 * d1;
        } while (d2 >= 1.0 || d2 == 0.0);
        double d3 = Math.sqrt(-2.0 * Math.log(d2) / d2);
        this.nextGaussian = d1 * d3;
        this.hasNextGaussian = true;
        return d0 * d3;
    }
}
