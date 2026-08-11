package cn.trialfinder.cli;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * All assertions use block coordinates. Tiling is step-based: adjacent tiles advance by
 * {@code step = tileSize - overlap} blocks and overlap by exactly {@code overlap} blocks
 * (except at the world edge).
 */
class WorldTilerTest {

    @Test
    void coversWholeWorldWithExpectedCount() {
        WorldTiler tiler = new WorldTiler(100_000, 1_000);
        long step = 100_000L - 1_000L;
        // Number of x-tiles: k such that -30M + k*step <= 30M  =>  k <= 60M/step = 606.06 -> 607.
        long tilesPerDim = 60_000_000L / step + 1;
        assertEquals(tilesPerDim, tiler.tilesPerDim());
        assertEquals(tilesPerDim * tilesPerDim, tiler.getTiles().size());
        assertEquals(607L, tilesPerDim);
        assertEquals(607L * 607L, tiler.getTiles().size());
    }

    @Test
    void tilesCoverWorldWithExpectedOverlap() {
        long tileSize = 100_000;
        long overlap = 1_000;
        long step = tileSize - overlap;
        WorldTiler tiler = new WorldTiler(tileSize, overlap);
        List<SearchRegion> tiles = tiler.getTiles();

        // First/last bounds.
        assertEquals(-30_000_000, tiles.get(0).minX());
        assertEquals(-30_000_000, tiles.get(0).minZ());
        assertEquals(30_000_000, tiles.get(tiles.size() - 1).maxX());
        assertEquals(30_000_000, tiles.get(tiles.size() - 1).maxZ());

        // Each tile is tileSize wide/tall except the world-edge tiles.
        for (SearchRegion r : tiles) {
            assertTrue(r.minX() <= r.maxX() && r.minZ() <= r.maxZ());
            if (r.maxX() < WorldTiler.WORLD_LIMIT) {
                assertEquals(tileSize, r.width());
            }
            if (r.maxZ() < WorldTiler.WORLD_LIMIT) {
                assertEquals(tileSize, r.height());
            }
        }

        // Tiles are built in row-major order (x outer, z inner). Within a row, consecutive tiles
        // overlap in z by exactly `overlap`; between rows, the x step is exactly `step`.
        SearchRegion prev = tiles.get(0);
        long prevRowMinX = prev.minX();
        long prevMaxZ = prev.maxZ();
        for (int i = 1; i < tiles.size(); i++) {
            SearchRegion r = tiles.get(i);
            if (r.minX() == prevRowMinX) {
                // Same x row: next tile starts `overlap` blocks before the previous tile's maxZ.
                assertEquals(prevMaxZ - overlap + 1, r.minZ(),
                        "z placement of adjacent tile at index " + i);
                long actualOverlap = prev.maxZ() - r.minZ() + 1;
                assertEquals(overlap, actualOverlap, "z-overlap between adjacent tiles at index " + i);
            } else {
                // New x row: x must step by exactly `step`.
                assertEquals(prevRowMinX + step, r.minX(), "x step between rows at index " + i);
                prevRowMinX = r.minX();
            }
            prev = r;
            prevMaxZ = r.maxZ();
        }
    }

    @Test
    void anyBlockCoordCoveredByAtMostFourTiles() {
        long tileSize = 100_000;
        long overlap = 1_000;
        WorldTiler tiler = new WorldTiler(tileSize, overlap);
        List<SearchRegion> tiles = tiler.getTiles();

        // Sample points across the world (corners, edges, centres, and overlap boundaries).
        long[] xs = {-30_000_000L, -29_999_000L, -29_901_000L, -29_900_500L, -29_900_000L,
                -29_802_000L, -1000L, 0L, 999L, 1000L, 30_000_000L};
        long[] zs = xs;
        for (long x : xs) {
            for (long z : zs) {
                int covering = 0;
                for (SearchRegion r : tiles) {
                    if (r.contains(x, z)) {
                        covering++;
                    }
                }
                assertTrue(covering >= 1 && covering <= 4,
                        "coverage of (" + x + "," + z + ") = " + covering + " (expected 1..4)");
            }
        }
    }

    @Test
    void owningTileIsUniqueAndMatchesOwns() {
        long tileSize = 100_000;
        long overlap = 1_000;
        WorldTiler tiler = new WorldTiler(tileSize, overlap);
        List<SearchRegion> tiles = tiler.getTiles();

        long[] coords = {-30_000_000L, -29_901_000L, -29_900_500L, -29_900_000L,
                -1000L, 0L, 999L, 29_994_000L, 30_000_000L};
        for (long x : coords) {
            for (long z : coords) {
                int owner = tiler.owningTileId(x, z);
                assertTrue(owner >= 0 && owner < tiles.size(), "owner id in range");
                // The owning tile must contain the point.
                assertTrue(tiles.get(owner).contains(x, z),
                        "owning tile " + owner + " must contain (" + x + "," + z + ")");
                // owns() agrees, and no other tile claims ownership.
                for (SearchRegion r : tiles) {
                    boolean expected = r.tileId() == owner;
                    assertEquals(expected, tiler.owns(r, x, z),
                            "owns() for tile " + r.tileId() + " at (" + x + "," + z + ")");
                }
            }
        }
    }

    @Test
    void rejectsInvalidParameters() {
        assertThrows(IllegalArgumentException.class, () -> new WorldTiler(0, 1_000));
        assertThrows(IllegalArgumentException.class, () -> new WorldTiler(-5, 1_000));
        assertThrows(IllegalArgumentException.class, () -> new WorldTiler(100_000, -1));
        assertThrows(IllegalArgumentException.class, () -> new WorldTiler(100_000, 100_000));
        assertThrows(IllegalArgumentException.class, () -> new WorldTiler(100_000, 200_000));
    }
}
