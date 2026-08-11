package cn.trialfinder.sim.random;

/**
 * Port of net.minecraft.world.level.levelgen.WorldgenRandom (1.21.11).
 * Wraps an inner {@link RandomSource} and provides the structure-seeding helpers.
 * Note: in the real class WorldgenRandom extends LegacyRandomSource and delegates
 * {@code next(int)} to the wrapped source. Here we reimplement the same behaviour as a
 * standalone class; the seed state lives in the wrapped source.
 */
public class WorldgenRandom implements BitRandomSource {
    private final RandomSource randomSource;
    private final MarsagliaPolarGaussian gaussianSource = new MarsagliaPolarGaussian(this);
    private int count;

    public WorldgenRandom(RandomSource randomSource) {
        this.randomSource = randomSource;
    }

    public int getCount() {
        return this.count;
    }

    @Override
    public RandomSource fork() {
        return this.randomSource.fork();
    }

    @Override
    public PositionalRandomFactory forkPositional() {
        return this.randomSource.forkPositional();
    }

    @Override
    public int next(int bits) {
        this.count++;
        return this.randomSource instanceof LegacyRandomSource legacy
                ? legacy.next(bits)
                : (int) (this.randomSource.nextLong() >>> 64 - bits);
    }

    @Override
    public void setSeed(long seed) {
        this.randomSource.setSeed(seed);
    }

    @Override
    public double nextGaussian() {
        return this.gaussianSource.nextGaussian();
    }

    /**
     * setLargeFeatureWithSalt(worldSeed, regionX, regionZ, salt).
     * Used by random-spread structure placement (trial chambers: salt = 94251327).
     */
    public void setLargeFeatureWithSalt(long worldSeed, int regionX, int regionZ, int salt) {
        long m = (long) regionX * 341873128712L + (long) regionZ * 132897987541L + worldSeed + salt;
        this.setSeed(m);
    }

    /**
     * setLargeFeatureSeed(worldSeed, chunkX, chunkZ).
     * Used for the structure-internal (jigsaw) random stream B.
     */
    public void setLargeFeatureSeed(long worldSeed, int chunkX, int chunkZ) {
        this.setSeed(worldSeed);
        long m = this.nextLong();
        long n = this.nextLong();
        long o = (long) chunkX * m ^ (long) chunkZ * n ^ worldSeed;
        this.setSeed(o);
    }

    public void setFeatureSeed(long worldSeed, int chunkX, int chunkZ) {
        long m = worldSeed + (long) chunkX + 10000L * chunkZ;
        this.setSeed(m);
    }

    public long setDecorationSeed(long worldSeed, int chunkX, int chunkZ) {
        this.setSeed(worldSeed);
        long m = this.nextLong() | 1L;
        long n = this.nextLong() | 1L;
        long o = (long) chunkX * m + (long) chunkZ * n ^ worldSeed;
        this.setSeed(o);
        return o;
    }

    public static RandomSource seedSlimeChunk(int x, int z, long worldSeed, long salt) {
        return RandomSource.create(worldSeed + (long) x * x * 4987142 + (long) x * 5947611 + (long) z * z * 4392871L + (long) z * 389711 ^ salt);
    }
}
