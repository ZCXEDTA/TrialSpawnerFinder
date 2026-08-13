package cn.trialfinder.sim.random;

/**
 * 复刻 {@code net.minecraft.util.RandomSource}（26.2 语义）。
 * 零依赖的逐位一致随机源接口。
 */
public interface RandomSource {

    static RandomSource create() {
        return create(RandomSupport.generateUniqueSeed());
    }

    /** 26.2 官方实现：{@code RandomSource.create(long)} 返回 {@link LegacyRandomSource}（LCG）。 */
    static RandomSource create(long seed) {
        return new LegacyRandomSource(seed);
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

    default void consumeCount(int count) {
        for (int i = 0; i < count; i++) {
            this.nextInt();
        }
    }
}
