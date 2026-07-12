package cn.trialfinder.config;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public record FinderConfig(
        long seed,
        int searchCenterX,
        int searchCenterZ,
        int searchRadiusBlocks,
        boolean fullWorld,
        int clusterRadiusBlocks,
        AreaShape areaShape,
        int minStructures,
        int minSpawners,
        int scanThreads,
        int scanShardSizeBlocks) {

    public static FinderConfig load(Path path) throws IOException {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }

        FinderConfig config = new FinderConfig(
                parseLong(properties, "seed"),
                parseInt(properties, "search-center-x"),
                parseInt(properties, "search-center-z"),
                parseInt(properties, "search-radius-blocks"),
                parseOptionalBoolean(properties, "full-world", false),
                parseInt(properties, "cluster-radius-blocks"),
                AreaShape.parse(properties.getProperty("area-shape")),
                parseInt(properties, "min-structures"),
                parseInt(properties, "min-spawners"),
                parseOptionalInt(properties, "scan-threads",
                        Math.min(8, Runtime.getRuntime().availableProcessors())),
                parseOptionalInt(properties, "scan-shard-size-blocks", 262_144));
        config.validate();
        return config;
    }

    private void validate() {
        if ((!fullWorld && searchRadiusBlocks <= 0) || clusterRadiusBlocks <= 0) {
            throw new IllegalArgumentException("普通搜索的搜索半径和聚类半径必须大于 0");
        }
        if (minStructures <= 0 || minSpawners < 0) {
            throw new IllegalArgumentException("min-structures 必须大于 0，min-spawners 不能小于 0");
        }
        if (scanThreads <= 0 || scanThreads > 64 || scanShardSizeBlocks <= 0) {
            throw new IllegalArgumentException("scan-threads 必须为 1..64，scan-shard-size-blocks 必须大于 0");
        }
        if (!fullWorld && (Math.abs((long) searchCenterX) > WORLD_LIMIT
                || Math.abs((long) searchCenterZ) > WORLD_LIMIT)) {
            throw new IllegalArgumentException("搜索中心超出了 Minecraft 世界边界");
        }
    }

    public static final int WORLD_LIMIT = 30_000_000;

    public long searchMinX() {
        return fullWorld ? -WORLD_LIMIT : Math.max(-WORLD_LIMIT, (long) searchCenterX - searchRadiusBlocks);
    }

    public long searchMaxX() {
        return fullWorld ? WORLD_LIMIT : Math.min(WORLD_LIMIT, (long) searchCenterX + searchRadiusBlocks);
    }

    public long searchMinZ() {
        return fullWorld ? -WORLD_LIMIT : Math.max(-WORLD_LIMIT, (long) searchCenterZ - searchRadiusBlocks);
    }

    public long searchMaxZ() {
        return fullWorld ? WORLD_LIMIT : Math.min(WORLD_LIMIT, (long) searchCenterZ + searchRadiusBlocks);
    }

    public boolean containsSearchPoint(long x, long z) {
        if (x < -WORLD_LIMIT || x > WORLD_LIMIT || z < -WORLD_LIMIT || z > WORLD_LIMIT) return false;
        if (fullWorld) return true;
        long dx = x - searchCenterX;
        long dz = z - searchCenterZ;
        long radius = searchRadiusBlocks;
        return dx * dx + dz * dz <= radius * radius;
    }

    private static int parseInt(Properties properties, String key) {
        return Math.toIntExact(parseLong(properties, key));
    }

    private static int parseOptionalInt(Properties properties, String key, int defaultValue) {
        String value = properties.getProperty(key);
        return value == null || value.isBlank() ? defaultValue : parseInt(properties, key);
    }

    private static boolean parseOptionalBoolean(
            Properties properties, String key, boolean defaultValue) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) return defaultValue;
        if (value.trim().equalsIgnoreCase("true")) return true;
        if (value.trim().equalsIgnoreCase("false")) return false;
        throw new IllegalArgumentException("配置项只能是 true 或 false: " + key + "=" + value);
    }

    private static long parseLong(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("缺少配置项: " + key);
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("配置项不是有效整数: " + key + "=" + value, e);
        }
    }
}
