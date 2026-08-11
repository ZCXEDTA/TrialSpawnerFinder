package cn.trialfinder.sim.random;

/**
 * Self-contained port of net.minecraft.util.RandomSource (Mojang official mappings, 1.21.11).
 * Bit-exact reimplementation with zero dependencies on the Minecraft server.
 */
public interface RandomSource {
    @Deprecated
    double GAUSSIAN_SPREAD_FACTOR = 2.297;

    static RandomSource create() {
        return create(RandomSupport.generateUniqueSeed());
    }

    @Deprecated
    static RandomSource createThreadSafe() {
        return new ThreadSafeLegacyRandomSource(RandomSupport.generateUniqueSeed());
    }

    static RandomSource create(long seed) {
        return new LegacyRandomSource(seed);
    }

    static RandomSource createNewThreadLocalInstance() {
        return new SingleThreadedRandomSource(java.util.concurrent.ThreadLocalRandom.current().nextLong());
    }

    RandomSource fork();

    PositionalRandomFactory forkPositional();

    void setSeed(long seed);

    int nextInt();

    int nextInt(int bound);

    default int nextIntBetweenInclusive(int min, int max) {
        return this.nextInt(max - min + 1) + min;
    }

    long nextLong();

    boolean nextBoolean();

    float nextFloat();

    double nextDouble();

    double nextGaussian();

    default double triangle(double mode, double spread) {
        return mode + spread * (this.nextDouble() - this.nextDouble());
    }

    default float triangle(float mode, float spread) {
        return mode + spread * (this.nextFloat() - this.nextFloat());
    }

    default void consumeCount(int count) {
        for (int i = 0; i < count; i++) {
            this.nextInt();
        }
    }

    default int nextInt(int origin, int bound) {
        if (origin >= bound) {
            throw new IllegalArgumentException("bound - origin is non positive");
        }
        return origin + this.nextInt(bound - origin);
    }
}
