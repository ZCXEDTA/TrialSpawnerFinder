package cn.trialfinder.sim.math;

/**
 * 复刻 {@code net.minecraft.world.level.ChunkPos}（26.2 语义）—— 模拟使用的子集。
 */
public class ChunkPos {
    private final int x;
    private final int z;

    public ChunkPos(int x, int z) {
        this.x = x;
        this.z = z;
    }

    public int x() {
        return this.x;
    }

    public int z() {
        return this.z;
    }

    public int getMinBlockX() {
        return this.x << 4;
    }

    public int getMinBlockZ() {
        return this.z << 4;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ChunkPos chunkPos)) {
            return false;
        }
        return this.x == chunkPos.x && this.z == chunkPos.z;
    }

    @Override
    public int hashCode() {
        int i = 1664525 * this.x + 1013904223;
        int j = 1664525 * (this.z ^ -559038737) + 1013904223;
        return i ^ j;
    }

    @Override
    public String toString() {
        return "[" + this.x + ", " + this.z + "]";
    }
}
