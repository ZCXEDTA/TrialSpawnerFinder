package cn.trialfinder.sim.random;

/**
 * 复刻 {@code net.minecraft.world.level.levelgen.WorldgenRandom}（26.2 语义）。
 * 包装底层 {@link RandomSource}，提供结构种子派生方法。
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
    public int nextInt() {
        return this.next(32);
    }

    @Override
    public int nextInt(int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("Bound 必须为正: " + bound);
        }
        return this.randomSource instanceof LegacyRandomSource
                ? ((LegacyRandomSource) this.randomSource).nextInt(bound)
                : this.randomSource.nextInt(bound);
    }

    @Override
    public long nextLong() {
        return this.randomSource.nextLong();
    }

    @Override
    public boolean nextBoolean() {
        return this.randomSource.nextBoolean();
    }

    @Override
    public float nextFloat() {
        return this.randomSource.nextFloat();
    }

    @Override
    public double nextDouble() {
        return this.randomSource.nextDouble();
    }

    @Override
    public double nextGaussian() {
        return this.gaussianSource.nextGaussian();
    }

    @Override
    public void consumeCount(int count) {
        for (int i = 0; i < count; i++) {
            this.nextInt();
        }
    }

    /**
     * 原版 {@code setLargeFeatureWithSalt(worldSeed, regionX, regionZ, salt)}。
     * 用于随机散布结构放置（试炼密室：salt = 94251327）。
     */
    public void setLargeFeatureWithSalt(long worldSeed, int regionX, int regionZ, int salt) {
        long m = (long) regionX * 341873128712L + (long) regionZ * 132897987541L + worldSeed + salt;
        this.setSeed(m);
    }

    /**
     * 原版 {@code setLargeFeatureSeed(worldSeed, chunkX, chunkZ)}。
     * 用于结构内部（jigsaw）随机流 B。
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
}
