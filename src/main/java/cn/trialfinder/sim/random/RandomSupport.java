package cn.trialfinder.sim.random;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Port of net.minecraft.world.level.levelgen.RandomSupport (1.21.11).
 * {@link #seedFromHashOf} reproduces Guava's MD5-based hash using the JDK MessageDigest
 * (identical digest bytes, big-endian long extraction). Trial-chamber generation never calls
 * it, but it is kept for completeness of the port.
 */
public final class RandomSupport {
    public static final long GOLDEN_RATIO_64 = -7046029254386353131L;
    public static final long SILVER_RATIO_64 = 7640891576956012809L;
    private static final AtomicLong SEED_UNIQUIFIER = new AtomicLong(8682522807148012L);

    private RandomSupport() {
    }

    public static Seed128bit seedFromHashOf(String string) {
        byte[] bytes = md5(string.getBytes(StandardCharsets.UTF_8));
        long lo = fromBytes(bytes[0], bytes[1], bytes[2], bytes[3], bytes[4], bytes[5], bytes[6], bytes[7]);
        long hi = fromBytes(bytes[8], bytes[9], bytes[10], bytes[11], bytes[12], bytes[13], bytes[14], bytes[15]);
        return new Seed128bit(lo, hi);
    }

    private static byte[] md5(byte[] input) {
        try {
            return MessageDigest.getInstance("MD5").digest(input);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 not available", e);
        }
    }

    private static long fromBytes(byte b0, byte b1, byte b2, byte b3, byte b4, byte b5, byte b6, byte b7) {
        return ((long) b0 & 255L) << 56
                | ((long) b1 & 255L) << 48
                | ((long) b2 & 255L) << 40
                | ((long) b3 & 255L) << 32
                | ((long) b4 & 255L) << 24
                | ((long) b5 & 255L) << 16
                | ((long) b6 & 255L) << 8
                | (long) b7 & 255L;
    }

    public static long mixStafford13(long l) {
        l = (l ^ l >>> 30) * -4658895280553007687L;
        l = (l ^ l >>> 27) * -7723592293110705685L;
        return l ^ l >>> 31;
    }

    public static Seed128bit upgradeSeedTo128bitUnmixed(long seed) {
        long lo = seed ^ SILVER_RATIO_64;
        long hi = lo + GOLDEN_RATIO_64;
        return new Seed128bit(lo, hi);
    }

    public static Seed128bit upgradeSeedTo128bit(long seed) {
        return upgradeSeedTo128bitUnmixed(seed).mixed();
    }

    public static long generateUniqueSeed() {
        return SEED_UNIQUIFIER.updateAndGet(seed -> seed * 1181783497276652981L) ^ System.nanoTime();
    }

    public record Seed128bit(long seedLo, long seedHi) {
        public Seed128bit xor(long lo, long hi) {
            return new Seed128bit(this.seedLo ^ lo, this.seedHi ^ hi);
        }

        public Seed128bit xor(Seed128bit other) {
            return this.xor(other.seedLo, other.seedHi);
        }

        public Seed128bit mixed() {
            return new Seed128bit(RandomSupport.mixStafford13(this.seedLo), RandomSupport.mixStafford13(this.seedHi));
        }
    }
}
