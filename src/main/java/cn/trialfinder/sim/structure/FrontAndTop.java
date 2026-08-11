package cn.trialfinder.sim.structure;

import cn.trialfinder.sim.util.StringRepresentable;

/**
 * Port of net.minecraft.core.FrontAndTop (1.21.11) — the jigsaw block "orientation" property.
 * Serialized name is "{front}_{top}", e.g. "north_up", "up_east".
 */
public enum FrontAndTop implements StringRepresentable {
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
    /** Precomputed serialized name ("{front}_{top}") — avoids re-splicing on every parse/rotate. */
    private final String serializedName;

    /** Reverse lookup by serialized name; built once at class init. */
    private static final java.util.Map<String, FrontAndTop> BY_NAME = new java.util.HashMap<>();

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
     * Applies the OctahedralGroup rotation that the vanilla Rotation value maps to
     * (Rotation.rotation() in net.minecraft.world.level.block.Rotation):
     * <ul>
     *   <li>CLOCKWISE_90  → ROT_90_Y_NEG      (Y rotation; horizontal getClockWise)</li>
     *   <li>CLOCKWISE_180 → ROT_180_FACE_XZ   (180° around the XZ face diagonal: horizontal
     *       getClockWise AND vertical flipped)</li>
     *   <li>COUNTERCLOCKWISE_90 → ROT_90_Y_POS (Y rotation; horizontal getCounterClockWise)</li>
     * </ul>
     * This is the transform used when rotating a jigsaw block's orientation property, and it is
     * what keeps the facing direction consistent with {@code StructureTemplate.transform}.
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

    /** ROT_180_FACE_XZ = diag(-1, 1, -1): horizontal directions flip to their opposite, Y is unchanged. */
    private static Direction rotate180Xz(Direction direction) {
        return direction.getAxis() == Direction.Axis.Y ? direction : direction.getOpposite();
    }

    public static FrontAndTop fromFrontAndTop(Direction front, Direction top) {
        for (FrontAndTop value : values()) {
            if (value.front == front && value.top == top) {
                return value;
            }
        }
        throw new IllegalArgumentException("Invalid combination of front " + front + " and top " + top);
    }

    public static FrontAndTop parse(String serializedName) {
        FrontAndTop value = BY_NAME.get(serializedName);
        if (value == null) {
            throw new IllegalArgumentException("Unknown FrontAndTop: " + serializedName);
        }
        return value;
    }

    @Override
    public String getSerializedName() {
        return this.serializedName;
    }
}
