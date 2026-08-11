package cn.trialfinder.sim.structure;

import cn.trialfinder.sim.util.StringRepresentable;

/**
 * Port of net.minecraft.core.Direction (1.21.11) — the subset the simulation uses.
 * Names match the vanilla block-state property values.
 */
public enum Direction implements StringRepresentable {
    DOWN(0, -1, "down"),
    UP(1, 1, "up"),
    NORTH(2, 0, "north"),
    SOUTH(3, 0, "south"),
    WEST(4, 0, "west"),
    EAST(5, 0, "east");

    private final int id;
    private final int stepY;
    private final String name;

    Direction(int id, int stepY, String name) {
        this.id = id;
        this.stepY = stepY;
        this.name = name;
    }

    public int getId() {
        return this.id;
    }

    public int getStepY() {
        return this.stepY;
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
            default -> throw new IllegalStateException("Unable to get Y-rotated clockwise of " + this);
        };
    }

    public Direction getCounterClockWise() {
        return switch (this) {
            case NORTH -> WEST;
            case WEST -> SOUTH;
            case SOUTH -> EAST;
            case EAST -> NORTH;
            default -> throw new IllegalStateException("Unable to get Y-rotated counterclockwise of " + this);
        };
    }

    public static Direction byId(int id) {
        for (Direction direction : values()) {
            if (direction.id == id) {
                return direction;
            }
        }
        throw new IllegalArgumentException("Invalid direction id: " + id);
    }

    @Override
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
