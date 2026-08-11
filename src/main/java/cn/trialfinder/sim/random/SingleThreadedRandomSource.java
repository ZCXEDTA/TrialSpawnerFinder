package cn.trialfinder.sim.random;

/**
 * Port of net.minecraft.world.level.levelgen.SingleThreadedRandomSource (1.21.11).
 * Simple non-thread-safe BitRandomSource used for {@link RandomSource#createNewThreadLocalInstance()}.
 */
public class SingleThreadedRandomSource implements BitRandomSource {
    private static final long MODULUS_MASK = (1L << 48) - 1;
    private static final long MULTIPLIER = 0x5DEECE66DL;
    private static final long INCREMENT = 0xBL;
    private long seed;
    private final MarsagliaPolarGaussian gaussianSource = new MarsagliaPolarGaussian(this);

    public SingleThreadedRandomSource(long seed) {
        this.setSeed(seed);
    }

    @Override
    public RandomSource fork() {
        return new SingleThreadedRandomSource(this.nextLong());
    }

    @Override
    public PositionalRandomFactory forkPositional() {
        return new LegacyRandomSource(this.nextLong()).forkPositional();
    }

    @Override
    public void setSeed(long seed) {
        this.seed = (seed ^ MULTIPLIER) & MODULUS_MASK;
        this.gaussianSource.reset();
    }

    @Override
    public int next(int bits) {
        this.seed = this.seed * MULTIPLIER + INCREMENT & MODULUS_MASK;
        return (int) (this.seed >>> 48 - bits);
    }

    @Override
    public double nextGaussian() {
        return this.gaussianSource.nextGaussian();
    }
}
