package cn.trialfinder.sim.math;

import java.util.HashMap;
import java.util.Map;

/**
 * 复刻 {@code net.minecraft.core.FrontAndTop}（26.2 语义）—— jigsaw 方块的 "orientation" 属性。
 * 序列化名 "{front}_{top}"，如 "north_up"、"up_east"。
 */
public enum FrontAndTop {
    DOWN_EAST(Direction.DOWN, Direction.EAST),
    DOWN_NORTH(Direction.DOWN, Direction.NORTH),
    DOWN_SOUTH(Direction.DOWN, Direction.SOUTH),
    DOWN_WEST(Direction.DOWN, Direction.WEST),
    UP_EAST(Direction.UP, Direction.EAST),
    UP_NORTH(Direction.UP, Direction.NORTH),
    UP_SOUTH(Direction.UP, Direction.SOUTH),
    UP_WEST(Direction.UP, Direction.WEST),
    EAST_UP(Direction.EAST, Direction.UP),
    NORTH_UP(Direction.NORTH, Direction.UP),
    SOUTH_UP(Direction.SOUTH, Direction.UP),
    WEST_UP(Direction.WEST, Direction.UP);

    private final Direction top;
    private final Direction front;
    private final String serializedName;

    private static final Map<String, FrontAndTop> BY_NAME = new HashMap<>();

    static {
        for (FrontAndTop value : values()) {
            BY_NAME.put(value.serializedName, value);
        }
    }

    FrontAndTop(Direction front, Direction top) {
        this.front = front;
        this.top = top;
        this.serializedName = front.getSerializedName() + "_" + top.getSerializedName();
    }

    public Direction front() {
        return this.front;
    }

    public Direction top() {
        return this.top;
    }

    /**
     * 应用 vanilla {@code Rotation} 对应的八面体群旋转（复刻 {@code Rotation.rotation()}）：
     * <ul>
     *   <li>CLOCKWISE_90  → ROT_90_Y_NEG（水平方向 getClockWise）</li>
     *   <li>CLOCKWISE_180 → ROT_180_FACE_XZ（水平方向取反，垂直方向不变）</li>
     *   <li>COUNTERCLOCKWISE_90 → ROT_90_Y_POS（水平方向 getCounterClockWise）</li>
     * </ul>
     */
    public FrontAndTop rotate(Rotation rotation) {
        return switch (rotation) {
            case NONE -> this;
            case CLOCKWISE_90 -> fromFrontAndTop(rotate90Neg(this.front), rotate90Neg(this.top));
            case CLOCKWISE_180 -> fromFrontAndTop(rotate180Xz(this.front), rotate180Xz(this.top));
            case COUNTERCLOCKWISE_90 -> fromFrontAndTop(rotate90Pos(this.front), rotate90Pos(this.top));
        };
    }

    private static Direction rotate90Neg(Direction direction) {
        return direction.getAxis() == Direction.Axis.Y ? direction : direction.getClockWise();
    }

    private static Direction rotate90Pos(Direction direction) {
        return direction.getAxis() == Direction.Axis.Y ? direction : direction.getCounterClockWise();
    }

    /** ROT_180_FACE_XZ：水平方向取反，垂直方向不变。 */
    private static Direction rotate180Xz(Direction direction) {
        return direction.getAxis() == Direction.Axis.Y ? direction : direction.getOpposite();
    }

    public static FrontAndTop fromFrontAndTop(Direction front, Direction top) {
        for (FrontAndTop value : values()) {
            if (value.front == front && value.top == top) {
                return value;
            }
        }
        throw new IllegalArgumentException("无效的 front/top 组合: " + front + " / " + top);
    }

    public static FrontAndTop parse(String serializedName) {
        FrontAndTop value = BY_NAME.get(serializedName);
        if (value == null) {
            throw new IllegalArgumentException("未知 FrontAndTop: " + serializedName);
        }
        return value;
    }

    public String getSerializedName() {
        return this.serializedName;
    }
}
