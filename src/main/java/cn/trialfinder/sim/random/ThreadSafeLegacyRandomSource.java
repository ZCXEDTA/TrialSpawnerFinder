package cn.trialfinder.sim.random;

/**
 * Port of net.minecraft.world.level.levelgen.ThreadSafeLegacyRandomSource (1.21.11).
 * The original serializes LCG advancement through an AtomicLong; that is exactly what
 * {@link LegacyRandomSource} already does here, so this class is an alias with the same state.
 */
public class ThreadSafeLegacyRandomSource implements BitRandomSource {
    private final LegacyRandomSource randomSource = new LegacyRandomSource(0L);

    public ThreadSafeLegacyRandomSource(long seed) {
        this.randomSource.setSeed(seed);
    }

    @Override
    public RandomSource fork() {
        return new ThreadSafeLegacyRandomSource(this.nextLong());
    }

    @Override
    public PositionalRandomFactory forkPositional() {
        return this.randomSource.forkPositional();
    }

    @Override
    public void setSeed(long seed) {
        this.randomSource.setSeed(seed);
    }

    @Override
    public int next(int bits) {
        return this.randomSource.next(bits);
    }

    @Override
    public double nextGaussian() {
        return this.randomSource.nextGaussian();
    }
}
