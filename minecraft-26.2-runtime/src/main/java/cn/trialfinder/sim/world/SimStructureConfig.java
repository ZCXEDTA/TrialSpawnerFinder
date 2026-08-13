package cn.trialfinder.sim.world;

import cn.trialfinder.sim.data.PoolAliasBinding;
import cn.trialfinder.sim.data.TrialChambersData;
import cn.trialfinder.sim.math.BlockPos;
import cn.trialfinder.sim.pool.DimensionPadding;
import cn.trialfinder.sim.pool.MaxDistance;
import cn.trialfinder.sim.pool.StructureTemplatePool;
import cn.trialfinder.sim.random.RandomSource;
import cn.trialfinder.sim.resource.Identifier;
import cn.trialfinder.sim.resource.ResourceKey;

import java.util.List;
import java.util.Optional;

/**
 * 试炼密室结构的完整配置面——对应官方 {@code JigsawStructure} 中被 mixin accessor 暴露的字段。
 * 剥离后这些字段全部来自数据文件，不再需要 Minecraft 注册表。
 */
public record SimStructureConfig(
        ResourceKey<StructureTemplatePool> startPool,
        Optional<Identifier> startJigsawName,
        int size,
        int startHeightMin,
        int startHeightMax,
        boolean useExpansionHack,
        boolean projectStartToHeightmap,
        MaxDistance maxDistance,
        DimensionPadding dimensionPadding,
        List<PoolAliasBinding> poolAliases) {

    public static SimStructureConfig trialChambers() {
        return new SimStructureConfig(
                ResourceKey.create(TrialChambersData.START_POOL),
                Optional.empty(),
                TrialChambersData.SIZE,
                TrialChambersData.START_HEIGHT_MIN,
                TrialChambersData.START_HEIGHT_MAX,
                TrialChambersData.USE_EXPANSION_HACK,
                false,
                new MaxDistance(TrialChambersData.MAX_DISTANCE_FROM_CENTER),
                new DimensionPadding(
                        TrialChambersData.DIMENSION_PADDING, TrialChambersData.DIMENSION_PADDING),
                TrialChambersData.aliases());
    }

    /** 原版 uniform start_height 采样（消费一次 RNG）。 */
    public int sampleStartHeight(RandomSource random) {
        return random.nextInt(this.startHeightMax - this.startHeightMin + 1) + this.startHeightMin;
    }

    public int minY() {
        return -64;
    }

    public int maxY() {
        return 320;
    }

    /** 26.2 trial_chambers 无 start_jigsaw_name，起始位置就是 start。 */
    public BlockPos resolveStartPos(BlockPos start, BlockPos biomePosition) {
        return start;
    }
}
