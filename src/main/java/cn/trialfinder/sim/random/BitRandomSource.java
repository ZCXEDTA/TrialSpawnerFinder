package cn.trialfinder.sim.random;

/**
 * Port of net.minecraft.world.level.levelgen.BitRandomSource (1.21.11).
 * Supplies the java.util.Random-compatible default methods on top of {@link #next(int)}.
 */
public interface BitRandomSource extends RandomSource {
    float FLOAT_MULTIPLIER = 1.0F / (float) (1 << 24);
    double DOUBLE_MULTIPLIER = 1.0 / (double) (1L << 53);

    int next(int bits);

    @Override
    default int nextInt() {
        return this.next(32);
    }

    @Override
    default int nextInt(int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("Bound must be positive");
        }
        if ((bound & bound - 1) == 0) {
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
    default long nextLong() {
        return ((long) this.next(32) << 32) + this.next(32);
    }

    @Override
    default boolean nextBoolean() {
        return this.next(1) != 0;
    }

    @Override
    default float nextFloat() {
        return this.next(24) * FLOAT_MULTIPLIER;
    }

    @Override
    default double nextDouble() {
        return (((long) this.next(26) << 27) + this.next(27)) * DOUBLE_MULTIPLIER;
    }
}
