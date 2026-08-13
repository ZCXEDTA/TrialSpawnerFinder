package cn.trialfinder.sim.math;

import cn.trialfinder.sim.random.RandomSource;
import cn.trialfinder.sim.util.Util;

import java.util.List;

/**
 * 复刻 {@code net.minecraft.world.level.block.Rotation}（26.2 语义）—— 模拟使用的子集。
 * 四个值都绕 Y 轴旋转。
 */
public enum Rotation {
    NONE(0, "none"),
    CLOCKWISE_90(1, "clockwise_90"),
    CLOCKWISE_180(2, "180"),
    COUNTERCLOCKWISE_90(3, "counterclockwise_90");

    private final int index;
    private final String id;

    Rotation(int index, String id) {
        this.index = index;
        this.id = id;
    }

    public int getIndex() {
        return this.index;
    }

    public Rotation getRotated(Rotation rotation) {
        return switch (rotation) {
            case CLOCKWISE_90 -> switch (this) {
                case NONE -> CLOCKWISE_90;
                case CLOCKWISE_90 -> CLOCKWISE_180;
                case CLOCKWISE_180 -> COUNTERCLOCKWISE_90;
                case COUNTERCLOCKWISE_90 -> NONE;
            };
            case CLOCKWISE_180 -> switch (this) {
                case NONE -> CLOCKWISE_180;
                case CLOCKWISE_90 -> COUNTERCLOCKWISE_90;
                case CLOCKWISE_180 -> NONE;
                case COUNTERCLOCKWISE_90 -> CLOCKWISE_90;
            };
            case COUNTERCLOCKWISE_90 -> switch (this) {
                case NONE -> COUNTERCLOCKWISE_90;
                case CLOCKWISE_90 -> NONE;
                case CLOCKWISE_180 -> CLOCKWISE_90;
                case COUNTERCLOCKWISE_90 -> CLOCKWISE_180;
            };
            default -> this;
        };
    }

    public Direction rotate(Direction direction) {
        if (direction.getAxis() == Direction.Axis.Y) {
            return direction;
        }
        return switch (this) {
            case CLOCKWISE_90 -> direction.getClockWise();
            case CLOCKWISE_180 -> direction.getOpposite();
            case COUNTERCLOCKWISE_90 -> direction.getCounterClockWise();
            default -> direction;
        };
    }

    public int rotate(int value, int length) {
        return switch (this) {
            case CLOCKWISE_90 -> (value + length / 4) % length;
            case CLOCKWISE_180 -> (value + length / 2) % length;
            case COUNTERCLOCKWISE_90 -> (value + length * 3 / 4) % length;
            default -> value;
        };
    }

    private static final List<Rotation> ROTATIONS = List.of(values());

    public static Rotation getRandom(RandomSource random) {
        return Util.getRandom(values(), random);
    }

    public static List<Rotation> getShuffled(RandomSource random) {
        return Util.shuffledCopy(ROTATIONS, random);
    }

    public static Rotation byIndex(int index) {
        for (Rotation rotation : values()) {
            if (rotation.index == index) {
                return rotation;
            }
        }
        return NONE;
    }

    public String getSerializedName() {
        return this.id;
    }
}
