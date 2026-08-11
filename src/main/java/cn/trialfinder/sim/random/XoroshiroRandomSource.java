package cn.trialfinder.sim.random;

import cn.trialfinder.sim.math.Mth;

/**
 * Port of net.minecraft.world.level.levelgen.XoroshiroRandomSource (1.21.11).
 * Bit-exact Xoroshiro128++ RandomSource.
 */
public class XoroshiroRandomSource implements RandomSource {
    private static final float FLOAT_UNIT = 5.9604645E-8F;
    private static final double DOUBLE_UNIT = 1.110223E-16;
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
        return new XoroshiroRandomSource(this.randomNumberGenerator.nextLong(), this.randomNumberGenerator.nextLong());
    }

    @Override
    public PositionalRandomFactory forkPositional() {
        return new XoroshiroPositionalRandomFactory(this.randomNumberGenerator.nextLong(), this.randomNumberGenerator.nextLong());
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
            throw new IllegalArgumentException("Bound must be positive");
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
        return (float) this.nextBits(24) * FLOAT_UNIT;
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

    public static class XoroshiroPositionalRandomFactory implements PositionalRandomFactory {
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
            builder.append("XoroshiroPositionalRandomFactory{").append(this.seedLo).append("L, ").append(this.seedHi).append("L}");
        }
    }
}
