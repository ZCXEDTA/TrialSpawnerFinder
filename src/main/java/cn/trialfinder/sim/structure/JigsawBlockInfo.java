package cn.trialfinder.sim.structure;

import cn.trialfinder.sim.nbt.NbtTag;
import cn.trialfinder.sim.resources.Identifier;
import cn.trialfinder.sim.resources.ResourceKey;
import cn.trialfinder.sim.structure.pools.StructureTemplatePool;

import java.util.Objects;

/**
 * Port of net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate$JigsawBlockInfo (1.21.11).
 */
public record JigsawBlockInfo(
        StructureBlockInfo info,
        JointType jointType,
        Identifier name,
        ResourceKey<StructureTemplatePool> pool,
        Identifier target,
        int placementPriority,
        int selectionPriority) {

    public static JigsawBlockInfo of(StructureBlockInfo info) {
        NbtTag.Compound nbt = Objects.requireNonNull(info.nbt(), () -> "jigsaw block at " + info.pos() + " has no NBT");
        JointType jointType = getJointType(nbt, info.state());
        Identifier name = nbt.contains("name") && !nbt.getString("name").isEmpty()
                ? Identifier.parse(nbt.getString("name"))
                : Identifier.withDefaultNamespace("empty");
        ResourceKey<StructureTemplatePool> pool = nbt.contains("pool") && !nbt.getString("pool").isEmpty()
                ? ResourceKey.create(Identifier.parse(nbt.getString("pool")))
                : StructureTemplatePool.EMPTY_KEY;
        Identifier target = nbt.contains("target") && !nbt.getString("target").isEmpty()
                ? Identifier.parse(nbt.getString("target"))
                : Identifier.withDefaultNamespace("empty");
        int placementPriority = nbt.getInt("placement_priority");
        int selectionPriority = nbt.getInt("selection_priority");
        return new JigsawBlockInfo(info, jointType, name, pool, target, placementPriority, selectionPriority);
    }

    public JigsawBlockInfo withInfo(StructureBlockInfo newInfo) {
        return new JigsawBlockInfo(newInfo, this.jointType, this.name, this.pool, this.target,
                this.placementPriority, this.selectionPriority);
    }

    public static JointType getJointType(NbtTag.Compound nbt, BlockState state) {
        if (nbt.contains("joint")) {
            return JointType.byName(nbt.getString("joint"));
        }
        return getDefaultJointType(state);
    }

    public static JointType getDefaultJointType(BlockState state) {
        FrontAndTop frontAndTop = state.frontAndTop();
        return frontAndTop != null && frontAndTop.front().getAxis().isHorizontal() ? JointType.ALIGNED : JointType.ROLLABLE;
    }
}
