package cn.trialfinder.sim.random;

/**
 * 复刻 {@code net.minecraft.world.level.levelgen.SingleThreadedRandomSource}（26.2 语义）。
 * 与 {@link LegacyRandomSource} 相同的 LCG，但非线程安全（用普通 long 状态）。
 */
public class SingleThreadedRandomSource implements BitRandomSource {
    private static final float FLOAT_UNIT = 5.9604645E-8F;
    private static final double DOUBLE_UNIT = 1.1102230246251565E-16;
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
    public int nextInt() {
        return this.next(32);
    }

    @Override
    public int nextInt(int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("Bound 必须为正: " + bound);
        }
        if ((bound & -bound) == bound) {
            return (int) ((bound * (long) this.next(31)) >> 31);
        }
        int bits;
        int value;
        do {
            bits = this.next(31);
            value = bits % bound;
        } while (bits - value + (bound - 1) < 0);
        return value;
    }

    @Override
    public long nextLong() {
        return ((long) this.next(32) << 32) + this.next(32);
    }

    @Override
    public boolean nextBoolean() {
        return this.next(1) != 0;
    }

    @Override
    public float nextFloat() {
        return this.next(24) * FLOAT_UNIT;
    }

    @Override
    public double nextDouble() {
        return this.next(53) * DOUBLE_UNIT;
    }

    @Override
    public double nextGaussian() {
        return this.gaussianSource.nextGaussian();
    }
}
