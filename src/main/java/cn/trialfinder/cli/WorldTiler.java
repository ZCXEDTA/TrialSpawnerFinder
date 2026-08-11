package cn.trialfinder.cli;

import java.util.ArrayList;
import java.util.List;

/**
 * Divides the full Minecraft world (±30,000,000 blocks on each axis) into a grid of rectangular
 * {@link SearchRegion} tiles. All units are <b>blocks</b>.
 *
 * <p>Tiling is step-based: consecutive tiles are placed {@code step = tileSize - overlap} apart,
 * so adjacent tiles (in X and in Z) overlap by exactly {@code overlap} blocks (except at the world
 * edge, where the last tile is clamped and the overlap may be smaller). This ensures any cluster
 * straddling a tile boundary is fully visible to at least one tile.
 *
 * <p>Because tiles overlap, a block coordinate can be inside up to 4 tiles (2 per dimension).
 * {@link #owns(SearchRegion, long, long)} assigns each point to exactly one tile (the minimum
 * tileId containing it), so cluster ownership is deterministic and no global dedup state is needed
 * while streaming tiles.
 */
public final class WorldTiler {

    public static final int WORLD_LIMIT = 30_000_000;

    private final long tileSize;
    private final long overlap;
    private final long step;
    private final int worldLimit;
    private final int tilesPerDim;
    private final List<SearchRegion> tiles;

    public WorldTiler(long tileSize, long overlap) {
        this(tileSize, overlap, WORLD_LIMIT);
    }

    /** Package-private for tests: tiling over a reduced world extent. */
    WorldTiler(long tileSize, long overlap, int worldLimit) {
        if (tileSize <= 0) {
            throw new IllegalArgumentException("tileSize must be positive");
        }
        if (overlap < 0) {
            throw new IllegalArgumentException("tileOverlap must be non-negative");
        }
        if (overlap >= tileSize) {
            throw new IllegalArgumentException("tileOverlap must be smaller than tileSize (step must be positive)");
        }
        this.tileSize = tileSize;
        this.overlap = overlap;
        this.step = tileSize - overlap;
        this.worldLimit = worldLimit;

        List<SearchRegion> list = new ArrayList<>();
        int id = 0;
        int xTiles = 0;
        for (long minX = -this.worldLimit; minX <= this.worldLimit; minX += this.step) {
            xTiles++;
            long maxX = Math.min(this.worldLimit, minX + this.tileSize - 1);
            for (long minZ = -this.worldLimit; minZ <= this.worldLimit; minZ += this.step) {
                long maxZ = Math.min(this.worldLimit, minZ + this.tileSize - 1);
                list.add(new SearchRegion(minX, maxX, minZ, maxZ, id++));
            }
        }
        this.tiles = List.copyOf(list);
        this.tilesPerDim = xTiles;
    }

    public List<SearchRegion> getTiles() {
        return this.tiles;
    }

    public long tileSize() {
        return this.tileSize;
    }

    public long overlap() {
        return this.overlap;
    }

    public long step() {
        return this.step;
    }

    /** Number of tiles along one axis (x and z grids are square). */
    public int tilesPerDim() {
        return this.tilesPerDim;
    }

    /**
     * True iff {@code region} is the unique owning tile of point {@code (x,z)}: the tile with the
     * smallest tileId whose core contains the point. With overlapping tiles this assigns every
     * point to exactly one tile, so each cluster is scored by exactly one tile.
     */
    public boolean owns(SearchRegion region, long x, long z) {
        return region.tileId() == owningTileId(x, z);
    }

    /** The tileId of the unique owning tile for a point. */
    public int owningTileId(long x, long z) {
        int tx = minTileIndex(x);
        int tz = minTileIndex(z);
        return tx * this.tilesPerDim + tz;
    }

    /** Smallest x owned by the tile column of index {@code i} (dimension-agnostic). */
    long owningStart(int i) {
        if (i <= 0) {
            return -this.worldLimit;
        }
        return (long) (i - 1) * this.step - this.worldLimit + this.tileSize;
    }

    /** Largest x owned by the tile column of index {@code i} (dimension-agnostic). */
    long owningEnd(int i) {
        if (i >= this.tilesPerDim - 1) {
            return this.worldLimit;
        }
        return (long) i * this.step - this.worldLimit + this.tileSize - 1;
    }

    private int minTileIndex(long coord) {
        long rel = coord + this.worldLimit;          // [0, 2*limit]
        long numer = rel - this.tileSize + 1;
        long idx = ceilDiv(numer, this.step);
        if (idx < 0) {
            idx = 0;
        }
        if (idx >= this.tilesPerDim) {
            idx = this.tilesPerDim - 1;
        }
        return (int) idx;
    }

    private static long ceilDiv(long a, long b) {
        return -Math.floorDiv(-a, b);
    }
}
