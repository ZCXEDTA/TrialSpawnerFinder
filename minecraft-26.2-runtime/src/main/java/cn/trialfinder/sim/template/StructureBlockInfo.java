package cn.trialfinder.sim.template;

import cn.trialfinder.sim.math.BlockPos;
import cn.trialfinder.sim.nbt.NbtTag;

/**
 * 复刻 {@code StructureTemplate.StructureBlockInfo}（26.2 语义）。
 */
public record StructureBlockInfo(BlockPos pos, BlockState state, NbtTag.Compound nbt) {
}
