package cn.trialfinder.search;

import cn.trialfinder.config.FinderConfig;
import cn.trialfinder.model.BlockPoint;

import java.util.ArrayList;
import java.util.List;

public final class TrialChamberCandidates {
    static final int SPACING_CHUNKS = 34;
    static final int SEPARATION_CHUNKS = 12;
    static final int SALT = 94_251_327;
    private static final long REGION_X_MULTIPLIER = 341_873_128_712L;
    private static final long REGION_Z_MULTIPLIER = 132_897_987_541L;

    private TrialChamberCandidates() {
    }

    public static List<BlockPoint> enumerate(FinderConfig config) {
        return enumerate(config, config.searchMinX(), config.searchMaxX(),
                config.searchMinZ(), config.searchMaxZ());
    }

    static List<BlockPoint> enumerate(FinderConfig config, long minX, long maxX, long minZ, long maxZ) {
        int minChunkX = Math.floorDiv(clampToInt(minX), 16);
        int maxChunkX = Math.floorDiv(clampToInt(maxX), 16);
        int minChunkZ = Math.floorDiv(clampToInt(minZ), 16);
        int maxChunkZ = Math.floorDiv(clampToInt(maxZ), 16);

        int minRegionX = Math.floorDiv(minChunkX, SPACING_CHUNKS) - 1;
        int maxRegionX = Math.floorDiv(maxChunkX, SPACING_CHUNKS) + 1;
        int minRegionZ = Math.floorDiv(minChunkZ, SPACING_CHUNKS) - 1;
        int maxRegionZ = Math.floorDiv(maxChunkZ, SPACING_CHUNKS) + 1;

        List<BlockPoint> result = new ArrayList<>();
        for (int regionX = minRegionX; regionX <= maxRegionX; regionX++) {
            for (int regionZ = minRegionZ; regionZ <= maxRegionZ; regionZ++) {
                BlockPoint candidate = candidateInRegion(config.seed(), regionX, regionZ);
                if (candidate.x() >= minX && candidate.x() <= maxX
                        && candidate.z() >= minZ && candidate.z() <= maxZ
                        && config.containsSearchPoint(candidate.x(), candidate.z())) {
                    result.add(candidate);
                }
            }
        }
        result.sort(BlockPoint::compareTo);
        return result;
    }

    static BlockPoint candidateInRegion(long seed, int regionX, int regionZ) {
        long randomSeed = (long) regionX * REGION_X_MULTIPLIER
                + (long) regionZ * REGION_Z_MULTIPLIER + seed + SALT;
        int bound = SPACING_CHUNKS - SEPARATION_CHUNKS;
        LegacyRandom random = new LegacyRandom(randomSeed);
        int chunkX = regionX * SPACING_CHUNKS + random.nextInt(bound);
        int chunkZ = regionZ * SPACING_CHUNKS + random.nextInt(bound);
        return new BlockPoint(chunkX * 16 + 8, chunkZ * 16 + 8);
    }

    private static int clampToInt(long value) {
        return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, value));
    }

    private static final class LegacyRandom {
        private static final long MULTIPLIER = 0x5DEECE66DL;
        private static final long ADDEND = 0xBL;
        private static final long MASK = (1L << 48) - 1;
        private long seed;

        private LegacyRandom(long seed) {
            this.seed = (seed ^ MULTIPLIER) & MASK;
        }

        private int next(int bits) {
            seed = (seed * MULTIPLIER + ADDEND) & MASK;
            return (int) (seed >>> (48 - bits));
        }

        private int nextInt(int bound) {
            int bits;
            int value;
            do {
                bits = next(31);
                value = bits % bound;
            } while (bits - value + (bound - 1) < 0);
            return value;
        }
    }
}
