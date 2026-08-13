package cn.trialfinder.sim.math;

/**
 * 复刻 {@code net.minecraft.world.level.levelgen.structure.BoundingBox}（26.2 语义）——
 * 闭区间整数盒，模拟使用的子集。
 */
public final class BoundingBox {
    private final int minX;
    private final int minY;
    private final int minZ;
    private final int maxX;
    private final int maxY;
    private final int maxZ;

    public BoundingBox(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
    }

    public int minX() {
        return this.minX;
    }

    public int minY() {
        return this.minY;
    }

    public int minZ() {
        return this.minZ;
    }

    public int maxX() {
        return this.maxX;
    }

    public int maxY() {
        return this.maxY;
    }

    public int maxZ() {
        return this.maxZ;
    }

    public int getXSpan() {
        return this.maxX - this.minX + 1;
    }

    public int getYSpan() {
        return this.maxY - this.minY + 1;
    }

    public int getZSpan() {
        return this.maxZ - this.minZ + 1;
    }

    public boolean isInside(int x, int y, int z) {
        return x >= this.minX && x <= this.maxX && z >= this.minZ && z <= this.maxZ
                && y >= this.minY && y <= this.maxY;
    }

    public boolean isInside(BlockPos pos) {
        return isInside(pos.getX(), pos.getY(), pos.getZ());
    }

    public boolean intersects(BoundingBox other) {
        return this.minX <= other.maxX
                && this.maxX >= other.minX
                && this.minZ <= other.maxZ
                && this.maxZ >= other.minZ
                && this.minY <= other.maxY
                && this.maxY >= other.minY;
    }

    public BoundingBox moved(int dx, int dy, int dz) {
        return new BoundingBox(
                this.minX + dx, this.minY + dy, this.minZ + dz,
                this.maxX + dx, this.maxY + dy, this.maxZ + dz);
    }

    public BoundingBox encapsulate(BlockPos pos) {
        return new BoundingBox(
                Math.min(this.minX, pos.getX()),
                Math.min(this.minY, pos.getY()),
                Math.min(this.minZ, pos.getZ()),
                Math.max(this.maxX, pos.getX()),
                Math.max(this.maxY, pos.getY()),
                Math.max(this.maxZ, pos.getZ()));
    }

    public BoundingBox encapsulate(BoundingBox other) {
        return new BoundingBox(
                Math.min(this.minX, other.minX),
                Math.min(this.minY, other.minY),
                Math.min(this.minZ, other.minZ),
                Math.max(this.maxX, other.maxX),
                Math.max(this.maxY, other.maxY),
                Math.max(this.maxZ, other.maxZ));
    }

    public BoundingBox min(int x, int y, int z, int dx, int dy, int dz) {
        return new BoundingBox(
                Math.max(this.minX, x), Math.max(this.minY, y), Math.max(this.minZ, z),
                Math.min(this.maxX, dx), Math.min(this.maxY, dy), Math.min(this.maxZ, dz));
    }

    public static BoundingBox fromCorners(BlockPos first, BlockPos second) {
        return new BoundingBox(
                Math.min(first.getX(), second.getX()),
                Math.min(first.getY(), second.getY()),
                Math.min(first.getZ(), second.getZ()),
                Math.max(first.getX(), second.getX()),
                Math.max(first.getY(), second.getY()),
                Math.max(first.getZ(), second.getZ()));
    }

    public static BoundingBox infinite() {
        return new BoundingBox(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE,
                Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);
    }

    @Override
    public String toString() {
        return "BoundingBox[" + this.minX + ", " + this.minY + ", " + this.minZ + " -> "
                + this.maxX + ", " + this.maxY + ", " + this.maxZ + "]";
    }
}
