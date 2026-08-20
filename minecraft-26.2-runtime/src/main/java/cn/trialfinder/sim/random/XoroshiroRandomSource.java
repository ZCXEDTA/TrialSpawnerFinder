package cn.trialfinder.sim.random;

import cn.trialfinder.sim.math.Mth;

/**
 * 复刻 {@code net.minecraft.world.level.levelgen.XoroshiroRandomSource}（26.2 语义）。
 * 逐位一致的 Xoroshiro128++ 随机源，含 positional factory。
 */
public class XoroshiroRandomSource implements RandomSource {
    private static final float FLOAT_UNIT = 5.9604645E-8F;
    private static final double DOUBLE_UNIT = 1.1102230246251565E-16;
    private Xoroshiro128PlusPlus randomNumberGenerator;
    private final MarsagliaPolarGaussian gaussianSource = new MarsagliaPolarGaussian(this);

    public XoroshiroRandomSource(long seed) {
        this.randomNumberGenerator = new Xoroshiro128PlusPlus(RandomSupport.upgradeSeedTo128bit(seed));
    }

    public XoroshiroRandomSource(RandomSupport.Seed128bit seed) {
        this.randomNumberGenerator = new Xoroshiro128PlusPlus(seed);
    }

    public XoroshiroRandomSource(long seedLo, long seedHi) {
        this.randomNumberGenerator = new Xoroshiro128PlusPlus(seedLo, seedHi);
    }

    @Override
    public RandomSource fork() {
        return new XoroshiroRandomSource(
                this.randomNumberGenerator.nextLong(), this.randomNumberGenerator.nextLong());
    }

    @Override
    public PositionalRandomFactory forkPositional() {
        return new XoroshiroPositionalRandomFactory(
                this.randomNumberGenerator.nextLong(), this.randomNumberGenerator.nextLong());
    }

    @Override
    public void setSeed(long seed) {
        this.randomNumberGenerator = new Xoroshiro128PlusPlus(RandomSupport.upgradeSeedTo128bit(seed));
        this.gaussianSource.reset();
    }

    @Override
    public int nextInt() {
        return (int) this.randomNumberGenerator.nextLong();
    }

    @Override
    public int nextInt(int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("Bound 必须为正: " + bound);
        }
        long l = Integer.toUnsignedLong(this.nextInt());
        long m = l * bound;
        long n = m & 0xFFFFFFFFL;
        if (n < bound) {
            for (int j = Integer.remainderUnsigned(~bound + 1, bound); n < j; n = m & 0xFFFFFFFFL) {
                l = Integer.toUnsignedLong(this.nextInt());
                m = l * bound;
            }
        }
        return (int) (m >> 32);
    }

    @Override
    public long nextLong() {
        return this.randomNumberGenerator.nextLong();
    }

    @Override
    public boolean nextBoolean() {
        return (this.randomNumberGenerator.nextLong() & 1L) != 0L;
    }

    @Override
    public float nextFloat() {
        return this.nextBits(24) * FLOAT_UNIT;
    }

    @Override
    public double nextDouble() {
        return this.nextBits(53) * DOUBLE_UNIT;
    }

    @Override
    public double nextGaussian() {
        return this.gaussianSource.nextGaussian();
    }

    @Override
    public void consumeCount(int count) {
        for (int i = 0; i < count; i++) {
            this.randomNumberGenerator.nextLong();
        }
    }

    private long nextBits(int bits) {
        return this.randomNumberGenerator.nextLong() >>> 64 - bits;
    }

    /** Xoroshiro128++ 核心。 */
    static final class Xoroshiro128PlusPlus {
        private long seedLo;
        private long seedHi;

        Xoroshiro128PlusPlus(RandomSupport.Seed128bit seed) {
            this(seed.seedLo(), seed.seedHi());
        }

        Xoroshiro128PlusPlus(long seedLo, long seedHi) {
            this.seedLo = seedLo;
            this.seedHi = seedHi;
            if ((this.seedLo | this.seedHi) == 0L) {
                this.seedLo = -7046029254386353131L;
                this.seedHi = 7640891576956012809L;
            }
        }

        long nextLong() {
            long l = this.seedLo;
            long m = this.seedHi;
            long n = Long.rotateLeft(l + m, 17) + l;
            m ^= l;
            this.seedLo = Long.rotateLeft(l, 49) ^ m ^ (m << 21);
            this.seedHi = Long.rotateLeft(m, 28);
            return n;
        }
    }

    public static final class XoroshiroPositionalRandomFactory implements PositionalRandomFactory {
        private final long seedLo;
        private final long seedHi;

        public XoroshiroPositionalRandomFactory(long seedLo, long seedHi) {
            this.seedLo = seedLo;
            this.seedHi = seedHi;
        }

        @Override
        public RandomSource at(int x, int y, int z) {
            long l = Mth.getSeed(x, y, z);
            return new XoroshiroRandomSource(l ^ this.seedLo, this.seedHi);
        }

        @Override
        public RandomSource fromHashOf(String name) {
            RandomSupport.Seed128bit seed = RandomSupport.seedFromHashOf(name);
            return new XoroshiroRandomSource(seed.xor(this.seedLo, this.seedHi));
        }

        @Override
        public RandomSource fromSeed(long seed) {
            return new XoroshiroRandomSource(seed);
        }

        @Override
        public void parityConfigString(StringBuilder builder) {
            builder.append("XoroshiroPositionalRandomFactory{")
                    .append(this.seedLo).append("L, ").append(this.seedHi).append("L}");
        }
    }
}
