package cn.trialfinder.sim.random;

/**
 * Port of net.minecraft.world.level.levelgen.MarsagliaPolarGaussian (1.21.11).
 * Bit-exact polar-method Gaussian generator, identical to java.util.Random.nextGaussian.
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
        double v1;
        double v2;
        double s;
        do {
            v1 = 2.0 * this.randomSource.nextDouble() - 1.0;
            v2 = 2.0 * this.randomSource.nextDouble() - 1.0;
            s = v1 * v1 + v2 * v2;
        } while (s >= 1.0 || s == 0.0);

        double multiplier = Math.sqrt(-2.0 * Math.log(s) / s);
        this.nextGaussian = v2 * multiplier;
        this.hasNextGaussian = true;
        return v1 * multiplier;
    }
}
