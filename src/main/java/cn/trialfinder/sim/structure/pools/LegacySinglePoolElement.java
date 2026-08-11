package cn.trialfinder.sim.structure.pools;

import cn.trialfinder.sim.resources.Holder;
import cn.trialfinder.sim.resources.Identifier;
import cn.trialfinder.sim.structure.LiquidSettings;
import cn.trialfinder.sim.structure.StructureProcessorList;

import java.util.Optional;

/**
 * Port of net.minecraft.world.level.levelgen.structure.pools.LegacySinglePoolElement (1.21.11).
 * Behaviour is identical to SinglePoolElement for the simulation (the only difference is block
 * placement processor selection, which the simulation never applies).
 */
public class LegacySinglePoolElement extends SinglePoolElement {
    protected LegacySinglePoolElement(Identifier templateId, Holder<StructureProcessorList> processors,
                                      Projection projection, Optional<LiquidSettings> liquidSettings) {
        super(templateId, processors, projection, liquidSettings);
    }

    @Override
    public String toString() {
        return "LegacySingle[" + this.templateId + "]";
    }
}
