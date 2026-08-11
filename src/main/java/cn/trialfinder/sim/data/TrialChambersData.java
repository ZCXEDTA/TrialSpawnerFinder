package cn.trialfinder.sim.data;

import cn.trialfinder.sim.resources.ResourceKey;
import cn.trialfinder.sim.structure.pools.StructureTemplatePool;
import cn.trialfinder.sim.structure.pools.alias.PoolAliasBinding;
import cn.trialfinder.sim.structure.placement.RandomSpreadStructurePlacement;
import cn.trialfinder.sim.structure.placement.RandomSpreadType;
import cn.trialfinder.sim.util.WeightedList;

import java.util.List;

/**
 * Data-driven constants for minecraft:trial_chambers (1.21.11), extracted from
 * data/minecraft/worldgen/structure_set/trial_chambers.json,
 * data/minecraft/worldgen/structure/trial_chambers.json and
 * TrialChambersStructurePools.ALIAS_BINDINGS.
 */
public final class TrialChambersData {
    private TrialChambersData() {
    }

    // ---- A flow: structure_set/trial_chambers.json ----
    public static final int SPACING_CHUNKS = 34;
    public static final int SEPARATION_CHUNKS = 12;
    public static final int SALT = 94_251_327;
    public static final RandomSpreadType SPREAD_TYPE = RandomSpreadType.LINEAR;

    public static final RandomSpreadStructurePlacement PLACEMENT =
            new RandomSpreadStructurePlacement(SPACING_CHUNKS, SEPARATION_CHUNKS, SPREAD_TYPE, SALT);

    // ---- B flow: structure/trial_chambers.json ----
    public static final String START_POOL = "trial_chambers/chamber/end";
    public static final String HALLWAY_FALLBACK = "trial_chambers/hallway/fallback";
    public static final int SIZE = 20;
    public static final int START_HEIGHT_MIN = -40;
    public static final int START_HEIGHT_MAX = -20;
    public static final boolean USE_EXPANSION_HACK = false;
    public static final int MAX_DISTANCE_FROM_CENTER = 116;
    public static final int DIMENSION_PADDING = 10;

    // ---- C flow: TrialChambersStructurePools.ALIAS_BINDINGS ----
    public static final List<PoolAliasBinding> ALIAS_BINDINGS = List.of(
            PoolAliasBinding.randomGroup(
                    WeightedList.<List<PoolAliasBinding>>builder()
                            .add(List.of(
                                    PoolAliasBinding.direct(spawner("contents/ranged"), spawner("ranged/skeleton")),
                                    PoolAliasBinding.direct(spawner("contents/slow_ranged"), spawner("slow_ranged/skeleton"))))
                            .add(List.of(
                                    PoolAliasBinding.direct(spawner("contents/ranged"), spawner("ranged/stray")),
                                    PoolAliasBinding.direct(spawner("contents/slow_ranged"), spawner("slow_ranged/stray"))))
                            .add(List.of(
                                    PoolAliasBinding.direct(spawner("contents/ranged"), spawner("ranged/poison_skeleton")),
                                    PoolAliasBinding.direct(spawner("contents/slow_ranged"), spawner("slow_ranged/poison_skeleton"))))
                            .build()),
            PoolAliasBinding.random(
                    spawner("contents/melee"),
                    WeightedList.<String>builder()
                            .add(spawner("melee/zombie"))
                            .add(spawner("melee/husk"))
                            .add(spawner("melee/spider"))
                            .build()),
            PoolAliasBinding.random(
                    spawner("contents/small_melee"),
                    WeightedList.<String>builder()
                            .add(spawner("small_melee/slime"))
                            .add(spawner("small_melee/cave_spider"))
                            .add(spawner("small_melee/silverfish"))
                            .add(spawner("small_melee/baby_zombie"))
                            .build()));

    public static ResourceKey<StructureTemplatePool> spawnerKey(String path) {
        return ResourceKey.create(spawner(path));
    }

    static String spawner(String suffix) {
        return "trial_chambers/spawner/" + suffix;
    }
}
