package cn.trialfinder.sim.biome;

import java.util.Set;

/**
 * The set of biomes in {@code #minecraft:has_structure/trial_chambers} (1.21.x).
 *
 * <p><b>Note:</b> the authoritative list is the data tag
 * {@code data/minecraft/tags/worldgen/biome/has_structure/trial_chambers.json}, which is not
 * present in the decompiled source set. The list below is the commonly used set for 1.21 trial
 * chambers (all overworld land + cave biomes where they generate). It is intended to be
 * replaced/verified against the exact tag when the data file is available.
 */
public final class TrialChambersBiomes {

    /** The tag identifier. */
    public static final String TAG = "#minecraft:has_structure/trial_chambers";

    /** Allowed biome IDs (see class javadoc caveat). */
    public static final Set<String> ALLOWED = Set.of(
            "minecraft:badlands",
            "minecraft:bamboo_jungle",
            "minecraft:beach",
            "minecraft:birch_forest",
            "minecraft:cherry_grove",
            "minecraft:dark_forest",
            "minecraft:deep_dark",
            "minecraft:desert",
            "minecraft:dripstone_caves",
            "minecraft:eroded_badlands",
            "minecraft:flower_forest",
            "minecraft:forest",
            "minecraft:frozen_peaks",
            "minecraft:grove",
            "minecraft:ice_spikes",
            "minecraft:jagged_peaks",
            "minecraft:jungle",
            "minecraft:lush_caves",
            "minecraft:mangrove_swamp",
            "minecraft:meadow",
            "minecraft:mushroom_fields",
            "minecraft:old_growth_birch_forest",
            "minecraft:old_growth_pine_taiga",
            "minecraft:old_growth_spruce_taiga",
            "minecraft:plains",
            "minecraft:savanna",
            "minecraft:savanna_plateau",
            "minecraft:snowy_beach",
            "minecraft:snowy_plains",
            "minecraft:snowy_slopes",
            "minecraft:snowy_taiga",
            "minecraft:sparse_jungle",
            "minecraft:stony_peaks",
            "minecraft:stony_shore",
            "minecraft:sunflower_plains",
            "minecraft:swamp",
            "minecraft:taiga",
            "minecraft:windswept_forest",
            "minecraft:windswept_gravelly_hills",
            "minecraft:windswept_hills",
            "minecraft:windswept_savanna",
            "minecraft:wooded_badlands");

    private TrialChambersBiomes() {
    }

    public static boolean contains(String biomeId) {
        return ALLOWED.contains(biomeId);
    }
}
