package cn.trialfinder.sim.jigsaw;

import cn.trialfinder.sim.math.BoundingBox;

import java.util.ArrayList;
import java.util.List;

/**
 * Jigsaw 拼接的占用空间碰撞检测。复刻 {@code HandTrialChamberPredictor.FreeSpace} 的快速路径，
 * 去掉 vanilla {@code VoxelShape}/{@code Shapes}/{@code BooleanOp} 的 audit 对拍。
 *
 * <p>边界是<b>半开区间</b> {@code [min, maxExclusive)}，与 vanilla {@code AABB} 一致；候选方块是闭区间
 * 整数盒 {@code [min, max]}（占据 {@code [min, max+1)}）。{@code contains} 的 0.25/0.75 偏移判定
 * 保持与原版逐位一致。
 */
public final class FreeSpace {
    private static final int CELL_SIZE = 16;

    private final int minX;
    private final int minY;
    private final int minZ;
    private final int maxXExclusive;
    private final int maxYExclusive;
    private final int maxZExclusive;
    private final int baseX;
    private final int baseY;
    private final int baseZ;
    private final int cellsX;
    private final int cellsY;
    private final int cellsZ;
    private final List<BlockedBox> blocked = new ArrayList<>();
    private final List<BlockedBox>[] cells;
    private int queryId;

    @SuppressWarnings("unchecked")
    public FreeSpace(int minX, int minY, int minZ,
                     int maxXExclusive, int maxYExclusive, int maxZExclusive,
                     BoundingBox initiallyBlocked) {
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxXExclusive = maxXExclusive;
        this.maxYExclusive = maxYExclusive;
        this.maxZExclusive = maxZExclusive;
        this.baseX = minX;
        this.baseY = minY;
        this.baseZ = minZ;
        this.cellsX = Math.max(1, Math.ceilDiv(maxXExclusive - minX, CELL_SIZE));
        this.cellsY = Math.max(1, Math.ceilDiv(maxYExclusive - minY, CELL_SIZE));
        this.cellsZ = Math.max(1, Math.ceilDiv(maxZExclusive - minZ, CELL_SIZE));
        this.cells = initiallyBlocked == null
                ? null
                : (List<BlockedBox>[]) new List<?>[cellsX * cellsY * cellsZ];
        if (initiallyBlocked != null) {
            add(initiallyBlocked);
        }
    }

    public boolean contains(BoundingBox candidate) {
        return containsFast(candidate);
    }

    public void occupy(BoundingBox box) {
        add(box);
    }

    private boolean containsFast(BoundingBox candidate) {
        if (candidate.minX() + 0.25 < minX
                || candidate.maxX() + 0.75 > maxXExclusive
                || candidate.minY() + 0.25 < minY
                || candidate.maxY() + 0.75 > maxYExclusive
                || candidate.minZ() + 0.25 < minZ
                || candidate.maxZ() + 0.75 > maxZExclusive) {
            return false;
        }
        if (cells == null) {
            for (BlockedBox entry : blocked) {
                if (intersects(candidate, entry.box)) {
                    return false;
                }
            }
            return true;
        }

        int currentQuery = ++queryId;
        for (int x = cellX(candidate.minX()); x <= cellX(candidate.maxX()); x++) {
            for (int y = cellY(candidate.minY()); y <= cellY(candidate.maxY()); y++) {
                for (int z = cellZ(candidate.minZ()); z <= cellZ(candidate.maxZ()); z++) {
                    List<BlockedBox> entries = cells[index(x, y, z)];
                    if (entries == null) {
                        continue;
                    }
                    for (BlockedBox entry : entries) {
                        if (entry.lastQuery == currentQuery) {
                            continue;
                        }
                        entry.lastQuery = currentQuery;
                        if (intersects(candidate, entry.box)) {
                            return false;
                        }
                    }
                }
            }
        }
        return true;
    }

    private void add(BoundingBox box) {
        BlockedBox entry = new BlockedBox(box);
        blocked.add(entry);
        if (cells == null) {
            return;
        }
        for (int x = cellX(box.minX()); x <= cellX(box.maxX()); x++) {
            for (int y = cellY(box.minY()); y <= cellY(box.maxY()); y++) {
                for (int z = cellZ(box.minZ()); z <= cellZ(box.maxZ()); z++) {
                    int index = index(x, y, z);
                    List<BlockedBox> entries = cells[index];
                    if (entries == null) {
                        entries = new ArrayList<>();
                        cells[index] = entries;
                    }
                    entries.add(entry);
                }
            }
        }
    }

    private int cellX(int x) {
        return Math.max(0, Math.min(cellsX - 1, Math.floorDiv(x - baseX, CELL_SIZE)));
    }

    private int cellY(int y) {
        return Math.max(0, Math.min(cellsY - 1, Math.floorDiv(y - baseY, CELL_SIZE)));
    }

    private int cellZ(int z) {
        return Math.max(0, Math.min(cellsZ - 1, Math.floorDiv(z - baseZ, CELL_SIZE)));
    }

    private int index(int x, int y, int z) {
        return (x * cellsY + y) * cellsZ + z;
    }

    private static boolean intersects(BoundingBox first, BoundingBox second) {
        return first.maxX() >= second.minX() && first.minX() <= second.maxX()
                && first.maxY() >= second.minY() && first.minY() <= second.maxY()
                && first.maxZ() >= second.minZ() && first.minZ() <= second.maxZ();
    }

    private static final class BlockedBox {
        private final BoundingBox box;
        private int lastQuery;

        private BlockedBox(BoundingBox box) {
            this.box = box;
        }
    }
}
