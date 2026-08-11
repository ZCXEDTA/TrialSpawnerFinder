package cn.trialfinder.sim.structure.pools;

import cn.trialfinder.sim.math.BlockPos;
import cn.trialfinder.sim.structure.BoundingBox;
import cn.trialfinder.sim.structure.JigsawJunction;
import cn.trialfinder.sim.structure.Rotation;
import cn.trialfinder.sim.structure.StructurePiece;
import cn.trialfinder.sim.structure.StructureTemplateManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Port of net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece (1.21.11).
 */
public class PoolElementStructurePiece extends StructurePiece {
    protected final StructurePoolElement element;
    protected BlockPos position;
    private final int groundLevelDelta;
    protected final Rotation rotation;
    private final List<JigsawJunction> junctions = new ArrayList<>();
    private final StructureTemplateManager structureTemplateManager;

    public PoolElementStructurePiece(
            StructureTemplateManager structureTemplateManager,
            StructurePoolElement element,
            BlockPos position,
            int groundLevelDelta,
            Rotation rotation,
            BoundingBox boundingBox) {
        super(boundingBox);
        this.structureTemplateManager = structureTemplateManager;
        this.element = element;
        this.position = position;
        this.groundLevelDelta = groundLevelDelta;
        this.rotation = rotation;
    }

    @Override
    public void move(int dx, int dy, int dz) {
        super.move(dx, dy, dz);
        this.position = this.position.offset(dx, dy, dz);
    }

    public Rotation getRotation() {
        return this.rotation;
    }

    public StructurePoolElement getElement() {
        return this.element;
    }

    public BlockPos getPosition() {
        return this.position;
    }

    public int getGroundLevelDelta() {
        return this.groundLevelDelta;
    }

    public void addJunction(JigsawJunction junction) {
        this.junctions.add(junction);
    }

    public List<JigsawJunction> getJunctions() {
        return this.junctions;
    }

    public StructureTemplateManager getStructureTemplateManager() {
        return this.structureTemplateManager;
    }
}
