package cn.trialfinder.sim.random;

import cn.trialfinder.sim.math.Mth;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 复刻 {@code net.minecraft.world.level.levelgen.LegacyRandomSource}（26.2 语义）。
 * 与 {@code java.util.Random} 相同的 LCG；用 AtomicLong 保持状态（线程安全）。
 */
public class LegacyRandomSource implements BitRandomSource {
    private static final float FLOAT_UNIT = 5.9604645E-8F;
    private static final double DOUBLE_UNIT = 1.1102230246251565E-16;
    private static final long MODULUS_MASK = (1L << 48) - 1;
    private static final long MULTIPLIER = 0x5DEECE66DL;
    private static final long INCREMENT = 0xBL;
    private final AtomicLong seed = new AtomicLong();
    private final MarsagliaPolarGaussian gaussianSource = new MarsagliaPolarGaussian(this);

    public LegacyRandomSource(long seed) {
        this.setSeed(seed);
    }

    @Override
    public RandomSource fork() {
        return new LegacyRandomSource(this.nextLong());
    }

    @Override
    public PositionalRandomFactory forkPositional() {
        return new LegacyPositionalRandomFactory(this.nextLong());
    }

    @Override
    public void setSeed(long seed) {
        this.seed.set((seed ^ MULTIPLIER) & MODULUS_MASK);
        this.gaussianSource.reset();
    }

    @Override
    public int next(int bits) {
        long l = this.seed.get();
        long m = l * MULTIPLIER + INCREMENT & MODULUS_MASK;
        this.seed.set(m);
        return (int) (m >>> 48 - bits);
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

    public static final class LegacyPositionalRandomFactory implements PositionalRandomFactory {
        private final long seed;

        public LegacyPositionalRandomFactory(long seed) {
            this.seed = seed;
        }

        @Override
        public RandomSource at(int x, int y, int z) {
            long l = Mth.getSeed(x, y, z);
            return new LegacyRandomSource(l ^ this.seed);
        }

        @Override
        public RandomSource fromHashOf(String name) {
            return new LegacyRandomSource((long) name.hashCode() ^ this.seed);
        }

        @Override
        public RandomSource fromSeed(long seed) {
            return new LegacyRandomSource(seed);
        }

        @Override
        public void parityConfigString(StringBuilder builder) {
            builder.append("LegacyPositionalRandomFactory{").append(this.seed).append('}');
        }
    }
}
