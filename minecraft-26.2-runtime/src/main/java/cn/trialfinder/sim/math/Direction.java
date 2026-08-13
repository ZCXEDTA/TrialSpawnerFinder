package cn.trialfinder.sim.math;

/**
 * 复刻 {@code net.minecraft.core.Direction}（26.2 语义）—— 模拟使用的子集。
 */
public enum Direction {
    DOWN(0, 0, -1, 0, "down"),
    UP(1, 0, 1, 0, "up"),
    NORTH(2, 0, 0, -1, "north"),
    SOUTH(3, 0, 0, 1, "south"),
    WEST(4, -1, 0, 0, "west"),
    EAST(5, 1, 0, 0, "east");

    private final int id;
    private final int stepX;
    private final int stepY;
    private final int stepZ;
    private final String name;

    Direction(int id, int stepX, int stepY, int stepZ, String name) {
        this.id = id;
        this.stepX = stepX;
        this.stepY = stepY;
        this.stepZ = stepZ;
        this.name = name;
    }

    public int getId() {
        return this.id;
    }

    public int getStepX() {
        return this.stepX;
    }

    public int getStepY() {
        return this.stepY;
    }

    public int getStepZ() {
        return this.stepZ;
    }

    public Axis getAxis() {
        return switch (this) {
            case DOWN, UP -> Axis.Y;
            case NORTH, SOUTH -> Axis.Z;
            default -> Axis.X;
        };
    }

    public boolean isHorizontal() {
        return this != DOWN && this != UP;
    }

    public Direction getOpposite() {
        return switch (this) {
            case DOWN -> UP;
            case UP -> DOWN;
            case NORTH -> SOUTH;
            case SOUTH -> NORTH;
            case WEST -> EAST;
            case EAST -> WEST;
        };
    }

    public Direction getClockWise() {
        return switch (this) {
            case NORTH -> EAST;
            case EAST -> SOUTH;
            case SOUTH -> WEST;
            case WEST -> NORTH;
            default -> throw new IllegalStateException("无法获取 " + this + " 的 Y 轴顺时针方向");
        };
    }

    public Direction getCounterClockWise() {
        return switch (this) {
            case NORTH -> WEST;
            case WEST -> SOUTH;
            case SOUTH -> EAST;
            case EAST -> NORTH;
            default -> throw new IllegalStateException("无法获取 " + this + " 的 Y 轴逆时针方向");
        };
    }

    public static Direction byId(int id) {
        for (Direction direction : values()) {
            if (direction.id == id) {
                return direction;
            }
        }
        throw new IllegalArgumentException("无效方向 id: " + id);
    }

    public String getSerializedName() {
        return this.name;
    }

    public enum Axis {
        X,
        Y,
        Z;

        public boolean isHorizontal() {
            return this != Y;
        }
    }
}
