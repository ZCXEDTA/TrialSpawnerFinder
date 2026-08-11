package cn.trialfinder.cli;

/**
 * A rectangular region of the world to search. Bounds are inclusive block coordinates.
 * {@code tileId} uniquely identifies the region (used for temp-file naming).
 */
public record SearchRegion(long minX, long maxX, long minZ, long maxZ, int tileId) {

    public long width() {
        return this.maxX - this.minX + 1;
    }

    public long height() {
        return this.maxZ - this.minZ + 1;
    }

    public boolean contains(long x, long z) {
        return x >= this.minX && x <= this.maxX && z >= this.minZ && z <= this.maxZ;
    }

    @Override
    public String toString() {
        return "SearchRegion[" + this.minX + "," + this.maxX + " x " + this.minZ + "," + this.maxZ
                + " tile=" + this.tileId + "]";
    }
}
