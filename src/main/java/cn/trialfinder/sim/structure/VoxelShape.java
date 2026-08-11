package cn.trialfinder.sim.structure;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Analytic stand-in for net.minecraft.world.phys.shapes.VoxelShape, specialized for the way
 * JigsawPlacement uses shapes with trial chambers (RIGID projection, no expansion hack).
 *
 * <p>The vanilla jigsaw overlap check is
 * {@code Shapes.joinIsNotEmpty(free, Shapes.create(AABB.of(newBox).deflate(0.25)), BooleanOp.ONLY_SECOND)}.
 * Because every box involved is an integer-block AABB, the 0.5-voxel discretization of
 * {@code Shapes.create} plus the 0.25 deflate reduces exactly to standard integer AABB
 * collision (see design notes): a candidate box is rejected iff it is not fully inside the
 * free region's block range, or it intersects any previously subtracted box.
 *
 * <p>Subtracted boxes are indexed in a {@link #CELL_SIZE}-block spatial hash grid, so
 * {@link #joinIsNotEmpty} only checks the candidate's local cells instead of scanning every
 * previously subtracted box — O(1) average per candidate instead of O(n) per chamber.
 */
public final class VoxelShape {
    /** Grid cell side in blocks; chambers span a few hundred blocks, so this keeps cells sparse. */
    private static final int CELL_SIZE = 32;

    private final BoundingBox region;
    private final List<BoundingBox> subtracted;
    private final Map<Long, List<BoundingBox>> grid;

    public VoxelShape(BoundingBox region) {
        this.region = region;
        this.subtracted = new ArrayList<>();
        this.grid = new HashMap<>();
    }

    /** Shapes.create(AABB) for an integer box → free = the box, nothing subtracted yet. */
    public static VoxelShape create(BoundingBox integerBox) {
        return new VoxelShape(integerBox);
    }

    /**
     * Equivalent of {@code Shapes.joinIsNotEmpty(this, Shapes.create(AABB.of(box).deflate(0.25)), ONLY_SECOND)}.
     * Returns true iff the deflated candidate box has a voxel that is NOT in the free space
     * (i.e. the placement must be rejected).
     */
    public boolean joinIsNotEmpty(BoundingBox candidate) {
        // Every block of the candidate must lie within the region's block range.
        if (candidate.minX() < this.region.minX() || candidate.maxX() > this.region.maxX()
                || candidate.minY() < this.region.minY() || candidate.maxY() > this.region.maxY()
                || candidate.minZ() < this.region.minZ() || candidate.maxZ() > this.region.maxZ()) {
            return true;
        }
        int minCX = Math.floorDiv(candidate.minX(), CELL_SIZE);
        int maxCX = Math.floorDiv(candidate.maxX(), CELL_SIZE);
        int minCY = Math.floorDiv(candidate.minY(), CELL_SIZE);
        int maxCY = Math.floorDiv(candidate.maxY(), CELL_SIZE);
        int minCZ = Math.floorDiv(candidate.minZ(), CELL_SIZE);
        int maxCZ = Math.floorDiv(candidate.maxZ(), CELL_SIZE);
        for (int cx = minCX; cx <= maxCX; cx++) {
            for (int cy = minCY; cy <= maxCY; cy++) {
                for (int cz = minCZ; cz <= maxCZ; cz++) {
                    List<BoundingBox> cell = this.grid.get(cellKey(cx, cy, cz));
                    if (cell == null) {
                        continue;
                    }
                    for (BoundingBox box : cell) {
                        if (box.intersects(candidate)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    /**
     * Equivalent of {@code Shapes.joinUnoptimized(this, Shapes.create(AABB.of(box)), ONLY_FIRST)}:
     * subtract the (undeflated) box from the free space.
     */
    public void subtract(BoundingBox box) {
        this.subtracted.add(box);
        int minCX = Math.floorDiv(box.minX(), CELL_SIZE);
        int maxCX = Math.floorDiv(box.maxX(), CELL_SIZE);
        int minCY = Math.floorDiv(box.minY(), CELL_SIZE);
        int maxCY = Math.floorDiv(box.maxY(), CELL_SIZE);
        int minCZ = Math.floorDiv(box.minZ(), CELL_SIZE);
        int maxCZ = Math.floorDiv(box.maxZ(), CELL_SIZE);
        for (int cx = minCX; cx <= maxCX; cx++) {
            for (int cy = minCY; cy <= maxCY; cy++) {
                for (int cz = minCZ; cz <= maxCZ; cz++) {
                    this.grid.computeIfAbsent(cellKey(cx, cy, cz),
                            ignored -> new ArrayList<>()).add(box);
                }
            }
        }
    }

    private static long cellKey(int x, int y, int z) {
        return ((long) x << 42) ^ ((long) y << 21) ^ (z & 0x1fffff);
    }
}
