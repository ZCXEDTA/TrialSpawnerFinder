package cn.trialfinder.sim.random;

/**
 * Port of net.minecraft.world.level.levelgen.Xoroshiro128PlusPlus (1.21.11).
 * Bit-exact Xoroshiro128++ core, reconstructed from the compiled class.
 */
public class Xoroshiro128PlusPlus {
    private long seedLo;
    private long seedHi;

    public Xoroshiro128PlusPlus(RandomSupport.Seed128bit seed) {
        this(seed.seedLo(), seed.seedHi());
    }

    public Xoroshiro128PlusPlus(long seedLo, long seedHi) {
        this.seedLo = seedLo;
        this.seedHi = seedHi;
        if ((this.seedLo | this.seedHi) == 0L) {
            this.seedLo = RandomSupport.GOLDEN_RATIO_64;
            this.seedHi = RandomSupport.SILVER_RATIO_64;
        }
    }

    public long nextLong() {
        long lo = this.seedLo;
        long hi = this.seedHi;
        long result = Long.rotateLeft(lo + hi, 17) + lo;
        hi ^= lo;
        this.seedLo = Long.rotateLeft(lo, 49) ^ hi ^ (hi << 21);
        this.seedHi = Long.rotateLeft(hi, 28);
        return result;
    }
}
