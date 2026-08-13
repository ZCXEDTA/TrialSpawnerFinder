package cn.trialfinder.sim.math;

/**
 * 复刻 {@code net.minecraft.core.BlockPos}（26.2 语义）—— 模拟使用的子集。
 */
public class BlockPos extends Vec3i {
    public static final BlockPos ZERO = new BlockPos(0, 0, 0);

    public BlockPos(int x, int y, int z) {
        super(x, y, z);
    }

    public static BlockPos of(Vec3i vec) {
        return new BlockPos(vec.getX(), vec.getY(), vec.getZ());
    }

    public static long asLong(int x, int y, int z) {
        long l = 0L;
        l |= (long) x & 0x3FFFFFFL;
        l |= ((long) z & 0x3FFFFFFL) << 26;
        l |= ((long) y & 0xFFF) << 52;
        return l;
    }

    @Override
    public BlockPos offset(int dx, int dy, int dz) {
        return new BlockPos(this.x + dx, this.y + dy, this.z + dz);
    }

    @Override
    public BlockPos offset(Vec3i other) {
        return this.offset(other.getX(), other.getY(), other.getZ());
    }

    public BlockPos subtract(Vec3i other) {
        return new BlockPos(this.x - other.getX(), this.y - other.getY(), this.z - other.getZ());
    }

    public BlockPos relative(Direction direction) {
        return new BlockPos(
                this.x + direction.getStepX(),
                this.y + direction.getStepY(),
                this.z + direction.getStepZ());
    }

    public BlockPos relative(Direction direction, int distance) {
        return distance == 0
                ? this
                : new BlockPos(
                        this.x + direction.getStepX() * distance,
                        this.y + direction.getStepY() * distance,
                        this.z + direction.getStepZ() * distance);
    }
}
