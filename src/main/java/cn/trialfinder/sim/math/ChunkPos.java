package cn.trialfinder.sim.math;

/**
 * Port of net.minecraft.world.level.ChunkPos (1.21.11) — the subset the simulation uses.
 */
public class ChunkPos {
    public static final int MAX_COORDINATE = 1875000;
    public static final long PACKED_XZ_MASK = 0xFFFFFFFFL;
    private static final long REGION_SIZE = 32L;
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

    public int getMaxBlockX() {
        return (this.x << 4) + 15;
    }

    public int getMaxBlockZ() {
        return (this.z << 4) + 15;
    }

    public long toLong() {
        return asLong(this.x, this.z);
    }

    public static long asLong(int x, int z) {
        return (long) x & 0xFFFFFFFFL | ((long) z & 0xFFFFFFFFL) << 32;
    }

    public static int getPackedX(long packed) {
        return (int) (packed & 0xFFFFFFFFL);
    }

    public static int getPackedZ(long packed) {
        return (int) (packed >>> 32);
    }

    public static ChunkPos fromLong(long packed) {
        return new ChunkPos(getPackedX(packed), getPackedZ(packed));
    }

    public int regionX() {
        return floorMod(this.x, 32);
    }

    public int regionZ() {
        return floorMod(this.z, 32);
    }

    public boolean isWithinDistance(int otherX, int otherZ, int distance) {
        return Math.abs(otherX - this.x) <= distance && Math.abs(otherZ - this.z) <= distance;
    }

    public boolean isRelevantForAnyStructure() {
        return this.x >= -1875000 && this.z >= -1875000 && this.x < 1875000 && this.z < 1875000;
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

    private static int floorMod(int a, int b) {
        int r = a % b;
        return r < 0 ? r + b : r;
    }
}
