package cn.trialfinder.sim.structure;

import cn.trialfinder.sim.random.RandomSource;
import cn.trialfinder.sim.util.StringRepresentable;
import cn.trialfinder.sim.util.Util;

import java.util.List;

/**
 * Port of net.minecraft.world.level.block.Rotation (1.21.11) — the subset the simulation uses.
 * All four values rotate around the Y axis.
 */
public enum Rotation implements StringRepresentable {
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

    /** All four rotations as an immutable list (avoids allocating {@code List.of(values())} per call). */
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

    @Override
    public String getSerializedName() {
        return this.id;
    }
}
