package cn.trialfinder.sim.structure;

import java.util.Objects;

/**
 * Simplified block state for the simulation: only the block id string and, for jigsaw blocks,
 * the orientation property value (e.g. "north_up"). Positions/transforms of the blocks that
 * matter (jigsaw, trial_spawner) do not depend on any other property.
 *
 * <p>The jigsaw orientation is parsed eagerly at construction into a {@link FrontAndTop}
 * reference (the finite enum), so {@link #frontAndTop()} is a plain field read rather than a
 * {@code ConcurrentHashMap} lookup — the lookup was the dominant cost in {@link
 * JigsawBlock#canAttach}, which runs for every candidate connector on every placement attempt.
 */
public final class BlockState {
    private final String name;
    private final String orientation;
    /** Parsed orientation (null when not a jigsaw block); avoids per-call hash lookups. */
    private final FrontAndTop frontAndTop;
    /** Memo of rotated states: keyed by (orientation, rotation-index). */
    private static final java.util.concurrent.ConcurrentHashMap<String, BlockState> ROTATION_CACHE =
            new java.util.concurrent.ConcurrentHashMap<>();

    public BlockState(String name, String orientation) {
        this.name = name;
        this.orientation = orientation;
        this.frontAndTop = orientation != null ? FrontAndTop.parse(orientation) : null;
    }

    public static BlockState of(String name) {
        return new BlockState(name, null);
    }

    public String name() {
        return this.name;
    }

    public String orientation() {
        return this.orientation;
    }

    public boolean isJigsaw() {
        return this.name.equals("minecraft:jigsaw");
    }

    public boolean isTrialSpawner() {
        return this.name.equals("minecraft:trial_spawner");
    }

    /** Returns the parsed orientation, or null for non-jigsaw blocks. O(1) field read. */
    public FrontAndTop frontAndTop() {
        return this.frontAndTop;
    }

    public BlockState rotate(Rotation rotation) {
        if (this.orientation == null || rotation == Rotation.NONE) {
            return this;
        }
        // Rotated orientation depends only on (orientation, rotation); the finite set of jigsaw
        // orientations makes the memo table small and saves the per-call FrontAndTop parse
        // + new BlockState allocation that dominated B-flow garbage.
        String cacheKey = this.orientation + '|' + rotation.getIndex();
        return ROTATION_CACHE.computeIfAbsent(cacheKey,
                ignored -> new BlockState(this.name, this.frontAndTop.rotate(rotation).getSerializedName()));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof BlockState that)) {
            return false;
        }
        return this.name.equals(that.name) && Objects.equals(this.orientation, that.orientation);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.name, this.orientation);
    }

    @Override
    public String toString() {
        return this.orientation == null ? this.name : this.name + "[" + this.orientation + "]";
    }
}
