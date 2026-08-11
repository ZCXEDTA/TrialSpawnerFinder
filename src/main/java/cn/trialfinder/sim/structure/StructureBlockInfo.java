package cn.trialfinder.sim.structure;

import cn.trialfinder.sim.math.BlockPos;
import cn.trialfinder.sim.nbt.NbtTag;

/**
 * Port of net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate$StructureBlockInfo (1.21.11).
 */
public record StructureBlockInfo(BlockPos pos, BlockState state, NbtTag.Compound nbt) {
}
