package cn.trialfinder.sim.structure;

/**
 * Simplified block state for the simulation: only the block id string and, for jigsaw blocks,
 * the orientation property value (e.g. "north_up"). Positions/transforms of the blocks that
 * matter (jigsaw, trial_spawner) do not depend on any other property.
 */
public record BlockState(String name, String orientation) {

    /** Cache of parsed {@link FrontAndTop} by orientation string (jigsaw blocks only). */
    private static final java.util.concurrent.ConcurrentHashMap<String, FrontAndTop> FRONT_AND_TOP_CACHE =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** Cache of rotated block states: keyed by (orientation, rotation-index). */
    private static final java.util.concurrent.ConcurrentHashMap<String, BlockState> ROTATION_CACHE =
            new java.util.concurrent.ConcurrentHashMap<>();

    public static BlockState of(String name) {
        return new BlockState(name, null);
    }

    public boolean isJigsaw() {
        return this.name.equals("minecraft:jigsaw");
    }

    public boolean isTrialSpawner() {
        return this.name.equals("minecraft:trial_spawner");
    }

    public FrontAndTop frontAndTop() {
        if (this.orientation == null) {
            return null;
        }
        // get() fast path avoids computeIfAbsent's hash-table write-side (CAS) machinery on every
        // call; the finite jigsaw orientation set makes the cache saturate after the first chamber.
        FrontAndTop value = FRONT_AND_TOP_CACHE.get(this.orientation);
        if (value == null) {
            value = FRONT_AND_TOP_CACHE.computeIfAbsent(this.orientation, FrontAndTop::parse);
        }
        return value;
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
                ignored -> new BlockState(this.name, this.frontAndTop().rotate(rotation).getSerializedName()));
    }

    @Override
    public String toString() {
        return this.orientation == null ? this.name : this.name + "[" + this.orientation + "]";
    }
}
