package cn.trialfinder.sim.biome;

/**
 * Biome identifiers used by the trial-chambers biome check.
 * The game's check uses the {@code #minecraft:has_structure/trial_chambers} tag (data-driven);
 * this enum carries the biome keys so the check can run without a registry.
 */
public enum BiomeType {
    BADLANDS("minecraft:badlands"),
    BAMBOO_JUNGLE("minecraft:bamboo_jungle"),
    BASALT_DELTAS("minecraft:basalt_deltas"),
    BEACH("minecraft:beach"),
    BIRCH_FOREST("minecraft:birch_forest"),
    CHERRY_GROVE("minecraft:cherry_grove"),
    COLD_OCEAN("minecraft:cold_ocean"),
    CRIMSON_FOREST("minecraft:crimson_forest"),
    DARK_FOREST("minecraft:dark_forest"),
    DEEP_COLD_OCEAN("minecraft:deep_cold_ocean"),
    DEEP_DARK("minecraft:deep_dark"),
    DEEP_FROZEN_OCEAN("minecraft:deep_frozen_ocean"),
    DEEP_LUKEWARM_OCEAN("minecraft:deep_lukewarm_ocean"),
    DEEP_OCEAN("minecraft:deep_ocean"),
    DESERT("minecraft:desert"),
    DRIPSTONE_CAVES("minecraft:dripstone_caves"),
    END_BARRENS("minecraft:end_barrens"),
    END_HIGHLANDS("minecraft:end_highlands"),
    END_MIDLANDS("minecraft:end_midlands"),
    ERODED_BADLANDS("minecraft:eroded_badlands"),
    FLOWER_FOREST("minecraft:flower_forest"),
    FOREST("minecraft:forest"),
    FROZEN_OCEAN("minecraft:frozen_ocean"),
    FROZEN_PEAKS("minecraft:frozen_peaks"),
    FROZEN_RIVER("minecraft:frozen_river"),
    GROVE("minecraft:grove"),
    ICE_SPIKES("minecraft:ice_spikes"),
    JAGGED_PEAKS("minecraft:jagged_peaks"),
    JUNGLE("minecraft:jungle"),
    LUKEWARM_OCEAN("minecraft:lukewarm_ocean"),
    LUSH_CAVES("minecraft:lush_caves"),
    MANGROVE_SWAMP("minecraft:mangrove_swamp"),
    MEADOW("minecraft:meadow"),
    MUSHROOM_FIELDS("minecraft:mushroom_fields"),
    NETHER_WASTES("minecraft:nether_wastes"),
    OCEAN("minecraft:ocean"),
    OLD_GROWTH_BIRCH_FOREST("minecraft:old_growth_birch_forest"),
    OLD_GROWTH_PINE_TAIGA("minecraft:old_growth_pine_taiga"),
    OLD_GROWTH_SPRUCE_TAIGA("minecraft:old_growth_spruce_taiga"),
    PLAINS("minecraft:plains"),
    RIVER("minecraft:river"),
    SAVANNA("minecraft:savanna"),
    SAVANNA_PLATEAU("minecraft:savanna_plateau"),
    SNOWY_BEACH("minecraft:snowy_beach"),
    SNOWY_TAIGA("minecraft:snowy_taiga"),
    SNOWY_PLAINS("minecraft:snowy_plains"),
    SNOWY_SLOPES("minecraft:snowy_slopes"),
    SOUL_SAND_VALLEY("minecraft:soul_sand_valley"),
    SPARSE_JUNGLE("minecraft:sparse_jungle"),
    STONY_PEAKS("minecraft:stony_peaks"),
    STONY_SHORE("minecraft:stony_shore"),
    SUNFLOWER_PLAINS("minecraft:sunflower_plains"),
    SWAMP("minecraft:swamp"),
    TAIGA("minecraft:taiga"),
    WARPED_FOREST("minecraft:warped_forest"),
    WINDSWEPT_FOREST("minecraft:windswept_forest"),
    WINDSWEPT_GRAVELLY_HILLS("minecraft:windswept_gravelly_hills"),
    WINDSWEPT_HILLS("minecraft:windswept_hills"),
    WINDSWEPT_SAVANNA("minecraft:windswept_savanna"),
    WOODED_BADLANDS("minecraft:wooded_badlands");

    private final String id;

    BiomeType(String id) {
        this.id = id;
    }

    public String id() {
        return this.id;
    }
}
