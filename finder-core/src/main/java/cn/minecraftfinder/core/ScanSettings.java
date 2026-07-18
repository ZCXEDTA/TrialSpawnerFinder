package cn.minecraftfinder.core;

public record ScanSettings(int threads, int shardSizeBlocks) {
    public ScanSettings {
        if (threads <= 0 || threads > 64) {
            throw new IllegalArgumentException("scan-threads 必须为 1..64");
        }
        if (shardSizeBlocks <= 0) {
            throw new IllegalArgumentException("scan-shard-size-blocks 必须大于 0");
        }
    }

    public static ScanSettings load(FinderProperties properties) {
        return new ScanSettings(
                properties.optionalInt("scan-threads",
                        Math.min(8, Runtime.getRuntime().availableProcessors())),
                properties.optionalInt("scan-shard-size-blocks", 262_144));
    }
}
