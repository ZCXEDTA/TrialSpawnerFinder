package cn.trialfinder.sim.math;

/**
 * Port of net.minecraft.core.Vec3i (1.21.11) — the subset the simulation uses.
 */
public class Vec3i implements Comparable<Vec3i> {
    public static final Vec3i ZERO = new Vec3i(0, 0, 0);
    protected final int x;
    protected final int y;
    protected final int z;

    public Vec3i(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public int getX() {
        return this.x;
    }

    public int getY() {
        return this.y;
    }

    public int getZ() {
        return this.z;
    }

    public Vec3i offset(int dx, int dy, int dz) {
        return new Vec3i(this.x + dx, this.y + dy, this.z + dz);
    }

    public Vec3i offset(Vec3i other) {
        return this.offset(other.getX(), other.getY(), other.getZ());
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Vec3i vec3i)) {
            return false;
        }
        return this.x == vec3i.x && this.y == vec3i.y && this.z == vec3i.z;
    }

    @Override
    public int hashCode() {
        long l = (long) (this.x * 3129871) ^ (long) this.z * 116129781L ^ (long) this.y;
        l = l * l * 42317861L + l * 11L;
        return (int) (l >> 16);
    }

    @Override
    public int compareTo(Vec3i other) {
        if (this.getY() != other.getY()) {
            return this.getY() - other.getY();
        }
        if (this.getZ() != other.getZ()) {
            return this.getZ() - other.getZ();
        }
        return this.getX() - other.getX();
    }

    @Override
    public String toString() {
        return "(" + this.x + ", " + this.y + ", " + this.z + ")";
    }
}
