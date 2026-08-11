package cn.trialfinder.sim.structure;

import cn.trialfinder.sim.util.StringRepresentable;

/**
 * Port of net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings (1.21.11).
 */
public enum LiquidSettings implements StringRepresentable {
    APPLY_WATERLOGGING("apply_waterlogging"),
    IGNORE_WATERLOGGING("ignore_waterlogging");

    private final String id;

    LiquidSettings(String id) {
        this.id = id;
    }

    @Override
    public String getSerializedName() {
        return this.id;
    }
}
