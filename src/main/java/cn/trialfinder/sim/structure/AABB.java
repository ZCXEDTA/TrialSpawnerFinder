package cn.trialfinder.sim.structure;

/**
 * Port of net.minecraft.world.phys.AABB (1.21.11) — minimal subset used for jigsaw collision
 * boxes. Inclusive-min / exclusive-max double bounds.
 */
public final class AABB {
    private final double minX;
    private final double minY;
    private final double minZ;
    private final double maxX;
    private final double maxY;
    private final double maxZ;

    public AABB(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
    }

    public static AABB of(BoundingBox box) {
        return new AABB(box.minX(), box.minY(), box.minZ(),
                box.maxX() + 1, box.maxY() + 1, box.maxZ() + 1);
    }

    public AABB deflate(double value) {
        return this.inflate(-value);
    }

    public AABB inflate(double value) {
        return new AABB(
                this.minX - value, this.minY - value, this.minZ - value,
                this.maxX + value, this.maxY + value, this.maxZ + value);
    }

    public double getXsize() {
        return this.maxX - this.minX;
    }

    public double getYsize() {
        return this.maxY - this.minY;
    }

    public double getZsize() {
        return this.maxZ - this.minZ;
    }
}
