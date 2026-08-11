package cn.trialfinder.sim.structure;

import cn.trialfinder.sim.math.BlockPos;

/**
 * Port of net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings
 * (1.21.11) — the subset the simulation uses. No processors are applied: the trial-chamber
 * processor lists only alter copper-block states, never jigsaw or trial_spawner blocks, so
 * they cannot affect spawner discovery.
 */
public final class StructurePlaceSettings {
    private Rotation rotation = Rotation.NONE;
    private BlockPos rotationPivot = BlockPos.ZERO;
    private BoundingBox boundingBox = null;

    public StructurePlaceSettings setRotation(Rotation rotation) {
        this.rotation = rotation;
        return this;
    }

    public StructurePlaceSettings setRotationPivot(BlockPos pivot) {
        this.rotationPivot = pivot;
        return this;
    }

    public StructurePlaceSettings setBoundingBox(BoundingBox boundingBox) {
        this.boundingBox = boundingBox;
        return this;
    }

    public Rotation getRotation() {
        return this.rotation;
    }

    public BlockPos getRotationPivot() {
        return this.rotationPivot;
    }

    public BoundingBox getBoundingBox() {
        return this.boundingBox;
    }
}
