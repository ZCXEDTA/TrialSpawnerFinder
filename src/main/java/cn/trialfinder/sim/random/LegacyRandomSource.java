package cn.trialfinder.sim.random;

import cn.trialfinder.sim.math.Mth;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Port of net.minecraft.world.level.levelgen.LegacyRandomSource (1.21.11).
 * Bit-exact LCG identical to java.util.Random. The threading guard from the original
 * (which throws when setSeed races with next) is dropped; the AtomicLong state is kept.
 */
public class LegacyRandomSource implements BitRandomSource {
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
    public double nextGaussian() {
        return this.gaussianSource.nextGaussian();
    }

    public static class LegacyPositionalRandomFactory implements PositionalRandomFactory {
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
