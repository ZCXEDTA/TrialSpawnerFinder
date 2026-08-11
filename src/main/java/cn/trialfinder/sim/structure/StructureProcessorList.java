package cn.trialfinder.sim.structure;

import java.util.List;

/**
 * Placeholder for net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList.
 * Trial-chamber processor lists only alter copper-block states and cannot remove or add jigsaw or
 * trial_spawner blocks, so the simulation does not apply them.
 */
public record StructureProcessorList(List<Object> list) {
    public static final StructureProcessorList EMPTY = new StructureProcessorList(List.of());
}
