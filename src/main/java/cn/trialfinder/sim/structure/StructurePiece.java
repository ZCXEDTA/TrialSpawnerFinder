package cn.trialfinder.sim.structure;

/**
 * Port of the subset of net.minecraft.world.level.levelgen.structure.StructurePiece used by
 * PoolElementStructurePiece: a mutable bounding box plus move().
 */
public abstract class StructurePiece {
    protected BoundingBox boundingBox;

    public StructurePiece(BoundingBox boundingBox) {
        this.boundingBox = boundingBox;
    }

    public BoundingBox getBoundingBox() {
        return this.boundingBox;
    }

    public void move(int dx, int dy, int dz) {
        this.boundingBox = this.boundingBox.moved(dx, dy, dz);
    }
}
