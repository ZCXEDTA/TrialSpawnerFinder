package cn.trialfinder.config;

import cn.minecraftfinder.core.AreaShape;
import cn.minecraftfinder.core.FinderProperties;
import cn.minecraftfinder.core.MinecraftWorld;
import cn.minecraftfinder.core.SearchArea;
import cn.minecraftfinder.core.SearchBounds;
import cn.minecraftfinder.core.ScanSettings;

import java.io.IOException;
import java.nio.file.Path;

public record FinderConfig(
        long seed,
        int searchCenterX,
        int searchCenterZ,
        int searchRadiusBlocks,
        boolean fullWorld,
        AreaShape searchAreaShape,
        int clusterRadiusBlocks,
        AreaShape areaShape,
        int minStructures,
        int minSpawners,
        int scanThreads,
        int scanShardSizeBlocks,
        TrialSearchMode searchMode,
        int predictionCalibrationStructures) {

    public FinderConfig(
            long seed, int searchCenterX, int searchCenterZ, int searchRadiusBlocks,
            boolean fullWorld, int clusterRadiusBlocks, AreaShape areaShape,
            int minStructures, int minSpawners, int scanThreads, int scanShardSizeBlocks) {
        this(seed, searchCenterX, searchCenterZ, searchRadiusBlocks, fullWorld,
                AreaShape.CIRCLE, clusterRadiusBlocks, areaShape, minStructures, minSpawners,
                scanThreads, scanShardSizeBlocks, TrialSearchMode.AUTO, 512);
    }

    public FinderConfig(
            long seed, int searchCenterX, int searchCenterZ, int searchRadiusBlocks,
            boolean fullWorld, AreaShape searchAreaShape, int clusterRadiusBlocks,
            AreaShape areaShape, int minStructures, int minSpawners, int scanThreads,
            int scanShardSizeBlocks, TrialSearchMode searchMode) {
        this(seed, searchCenterX, searchCenterZ, searchRadiusBlocks, fullWorld,
                searchAreaShape, clusterRadiusBlocks, areaShape, minStructures, minSpawners,
                scanThreads, scanShardSizeBlocks, searchMode, 512);
    }

    public static FinderConfig load(Path path) throws IOException {
        FinderProperties properties = FinderProperties.load(path);
        SearchArea searchArea = SearchArea.load(properties, MinecraftWorld.BLOCK_LIMIT);
        ScanSettings scan = ScanSettings.load(properties);

        FinderConfig config = new FinderConfig(
                properties.requiredLong("seed"),
                searchArea.centerX(), searchArea.centerZ(), searchArea.radiusBlocks(),
                searchArea.fullWorld(), searchArea.shape(),
                requiredInt(properties, "trial-cluster-radius-blocks", "cluster-radius-blocks"),
                AreaShape.parse(required(properties, "trial-area-shape", "area-shape")),
                requiredInt(properties, "trial-min-structures", "min-structures"),
                requiredInt(properties, "trial-min-spawners", "min-spawners"),
                scan.threads(), scan.shardSizeBlocks(),
                TrialSearchMode.parse(properties.optional("trial-search-mode", "auto")),
                properties.optionalInt("trial-prediction-calibration-structures", 512));
        config.validate();
        return config;
    }

    private void validate() {
        if (clusterRadiusBlocks <= 0) throw new IllegalArgumentException("聚类半径必须大于 0");
        if (minStructures <= 0 || minSpawners < 0) {
            throw new IllegalArgumentException("min-structures 必须大于 0，min-spawners 不能小于 0");
        }
        if (predictionCalibrationStructures < 0) {
            throw new IllegalArgumentException("预测校准座数不能小于 0");
        }
    }

    private static String required(FinderProperties properties, String key, String legacyKey) {
        return properties.contains(key) ? properties.required(key) : properties.required(legacyKey);
    }

    private static int requiredInt(FinderProperties properties, String key, String legacyKey) {
        return properties.contains(key) ? properties.requiredInt(key) : properties.requiredInt(legacyKey);
    }

    public SearchBounds searchBounds() {
        return searchArea().bounds();
    }

    public SearchArea searchArea() {
        return new SearchArea(searchCenterX, searchCenterZ, searchRadiusBlocks,
                searchAreaShape, fullWorld, MinecraftWorld.BLOCK_LIMIT);
    }

    public long searchMinX() { return searchBounds().minX(); }
    public long searchMaxX() { return searchBounds().maxX(); }
    public long searchMinZ() { return searchBounds().minZ(); }
    public long searchMaxZ() { return searchBounds().maxZ(); }

    public boolean containsSearchPoint(long x, long z) {
        if (fullWorld) return true;
        long dx = x - searchCenterX;
        long dz = z - searchCenterZ;
        return searchAreaShape == AreaShape.SQUARE
                || dx * dx + dz * dz <= (long) searchRadiusBlocks * searchRadiusBlocks;
    }
}
