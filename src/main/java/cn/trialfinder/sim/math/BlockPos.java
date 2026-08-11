package cn.trialfinder.sim.math;

/**
 * Port of net.minecraft.core.BlockPos (1.21.11) — the subset the simulation uses.
 */
public class BlockPos extends Vec3i {
    public static final BlockPos ZERO = new BlockPos(0, 0, 0);

    public BlockPos(int x, int y, int z) {
        super(x, y, z);
    }

    public static BlockPos of(long packed) {
        return new BlockPos(
                (int) (packed >> 38),
                (int) (packed << 52 >> 52),
                (int) (packed << 26 >> 38));
    }

    public long asLong() {
        return (long) this.getX() << 38 | (long) this.getZ() << 12 | (long) (this.getY() & 0xFFF);
    }

    public BlockPos offset(int dx, int dy, int dz) {
        return new BlockPos(this.getX() + dx, this.getY() + dy, this.getZ() + dz);
    }

    public BlockPos offset(Vec3i offset) {
        return this.offset(offset.getX(), offset.getY(), offset.getZ());
    }

    public BlockPos offset(cn.trialfinder.sim.structure.Direction direction) {
        int dx = switch (direction) {
            case EAST -> 1;
            case WEST -> -1;
            default -> 0;
        };
        int dz = switch (direction) {
            case SOUTH -> 1;
            case NORTH -> -1;
            default -> 0;
        };
        return this.offset(dx, direction.getStepY(), dz);
    }

    public BlockPos subtract(Vec3i other) {
        return this.offset(-other.getX(), -other.getY(), -other.getZ());
    }

    public BlockPos above() {
        return new BlockPos(this.getX(), this.getY() + 1, this.getZ());
    }

    public BlockPos below() {
        return new BlockPos(this.getX(), this.getY() - 1, this.getZ());
    }

    public double distToCenterSqr(double x, double y, double z) {
        double dx = (double) this.getX() + 0.5 - x;
        double dy = (double) this.getY() + 0.5 - y;
        double dz = (double) this.getZ() + 0.5 - z;
        return dx * dx + dy * dy + dz * dz;
    }

    public static int getX(long packed) {
        return (int) (packed >> 38);
    }

    public static int getY(long packed) {
        return (int) (packed << 52 >> 52);
    }

    public static int getZ(long packed) {
        return (int) (packed << 26 >> 38);
    }
}
