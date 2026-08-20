package cn.trialfinder.config;

import cn.minecraftfinder.core.AreaShape;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FinderConfigTest {
    @Test
    void optimizedPointContainmentMatchesSearchAreaAtBoundaries() {
        FinderConfig[] configs = {
                new FinderConfig(0, -17, 23, 128, false, AreaShape.CIRCLE,
                        128, AreaShape.CIRCLE, 2, 20, 2, 32_768),
                new FinderConfig(0, 30_000_000, -30_000_000, 1, false, AreaShape.SQUARE,
                        128, AreaShape.CIRCLE, 2, 20, 2, 32_768),
                new FinderConfig(0, 0, 0, 1, true, AreaShape.CIRCLE,
                        128, AreaShape.CIRCLE, 2, 20, 2, 32_768)
        };
        Random random = new Random(0x51A7);
        for (FinderConfig config : configs) {
            for (int index = 0; index < 10_000; index++) {
                long x = random.nextLong(-30_000_100L, 30_000_101L);
                long z = random.nextLong(-30_000_100L, 30_000_101L);
                assertEquals(config.searchArea().contains(x, z),
                        config.containsSearchPoint(x, z),
                        () -> "mismatch at " + x + "," + z + " for " + config);
            }
        }
    }

    @TempDir
    Path directory;

    @Test
    void loadsNegativeSeedAndCoordinates() throws IOException {
        Path file = directory.resolve("finder.properties");
        Files.writeString(file, """
                seed=-123
                search-center-x=-100
                search-center-z=200
                search-radius-blocks=10000
                cluster-radius-blocks=256
                area-shape=square
                min-structures=2
                min-spawners=20
                """);

        FinderConfig config = FinderConfig.load(file);

        assertEquals(-123, config.seed());
        assertEquals(-100, config.searchCenterX());
        assertEquals(AreaShape.SQUARE, config.areaShape());
        assertEquals(AreaShape.CIRCLE, config.searchAreaShape());
        assertEquals(false, config.fullWorld());
        assertEquals(262_144, config.scanShardSizeBlocks());
        assertEquals(Math.min(8, Runtime.getRuntime().availableProcessors()), config.scanThreads());
    }

    @Test
    void loadsPrefixedTrialSettings() throws IOException {
        Path file = directory.resolve("finder.properties");
        Files.writeString(file, """
                seed=1
                search-center-x=0
                search-center-z=0
                search-radius-blocks=1000
                trial-cluster-radius-blocks=192
                trial-area-shape=square
                trial-min-structures=3
                trial-min-spawners=24
                """);

        FinderConfig config = FinderConfig.load(file);

        assertEquals(192, config.clusterRadiusBlocks());
        assertEquals(AreaShape.SQUARE, config.areaShape());
        assertEquals(3, config.minStructures());
        assertEquals(24, config.minSpawners());
    }

    @Test
    void supportsSquareOuterSearchArea() throws IOException {
        Path file = directory.resolve("finder.properties");
        Files.writeString(file, """
                seed=0
                search-center-x=0
                search-center-z=0
                search-radius-blocks=100
                search-area-shape=square
                cluster-radius-blocks=128
                area-shape=circle
                min-structures=1
                min-spawners=0
                """);

        FinderConfig config = FinderConfig.load(file);

        assertEquals(true, config.containsSearchPoint(100, 100));
    }

    @Test
    void rejectsNonPositiveRadius() throws IOException {
        Path file = directory.resolve("finder.properties");
        Files.writeString(file, """
                seed=0
                search-center-x=0
                search-center-z=0
                search-radius-blocks=0
                cluster-radius-blocks=256
                area-shape=circle
                min-structures=2
                min-spawners=20
                """);

        assertThrows(IllegalArgumentException.class, () -> FinderConfig.load(file));
    }

    @Test
    void rejectsUnknownAreaShape() throws IOException {
        Path file = directory.resolve("finder.properties");
        Files.writeString(file, """
                seed=0
                search-center-x=0
                search-center-z=0
                search-radius-blocks=100
                cluster-radius-blocks=128
                area-shape=triangle
                min-structures=1
                min-spawners=0
                """);

        assertThrows(IllegalArgumentException.class, () -> FinderConfig.load(file));
    }

    @Test
    void fullWorldIgnoresCenterAndRadius() throws IOException {
        Path file = directory.resolve("finder.properties");
        Files.writeString(file, """
                seed=0
                search-center-x=999999999
                search-center-z=-999999999
                search-radius-blocks=0
                full-world=true
                cluster-radius-blocks=128
                area-shape=circle
                min-structures=3
                min-spawners=20
                """);

        FinderConfig config = FinderConfig.load(file);

        assertEquals(-30_000_000, config.searchMinX());
        assertEquals(30_000_000, config.searchMaxZ());
        assertEquals(true, config.containsSearchPoint(-30_000_000, 30_000_000));
    }

    @Test
    void radiusIsClippedAtWorldBorderWithoutClippingOtherDirections() throws IOException {
        Path file = directory.resolve("finder.properties");
        Files.writeString(file, """
                seed=0
                search-center-x=-29000000
                search-center-z=-29000000
                search-radius-blocks=2000000
                full-world=false
                cluster-radius-blocks=128
                area-shape=circle
                min-structures=3
                min-spawners=20
                """);

        FinderConfig config = FinderConfig.load(file);

        assertEquals(-30_000_000, config.searchMinX());
        assertEquals(-27_000_000, config.searchMaxX());
        assertEquals(true, config.containsSearchPoint(-27_000_000, -29_000_000));
        assertEquals(false, config.containsSearchPoint(-27_000_000, -27_000_000));
    }

    @Test
    void hugeRadiusCanCoverTheWholeWorldWithoutOverflow() throws IOException {
        Path file = directory.resolve("finder.properties");
        Files.writeString(file, """
                seed=0
                search-center-x=10000000
                search-center-z=10000000
                search-radius-blocks=300000000
                full-world=false
                cluster-radius-blocks=128
                area-shape=circle
                min-structures=3
                min-spawners=20
                """);

        FinderConfig config = FinderConfig.load(file);

        assertEquals(-30_000_000, config.searchMinX());
        assertEquals(30_000_000, config.searchMaxX());
        assertEquals(true, config.containsSearchPoint(-30_000_000, -30_000_000));
    }
}
