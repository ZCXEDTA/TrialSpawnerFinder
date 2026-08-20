package cn.trialfinder.sim.world;

import cn.minecraftfinder.core.BlockPoint;
import cn.trialfinder.search.TrialChamberCandidates;
import cn.trialfinder.sim.pool.PoolRegistry;
import cn.trialfinder.sim.template.StructureTemplateManager;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 对照官方验证：生物群系过滤前后密室数量。
 *
 * <p>参数表 55 个主世界生物群系中，试炼密室标签只排除 {@code minecraft:deep_dark}
 * （远古城市所在生物群系）。官方在 biomePosition 采样生物群系，为 deep_dark 时该密室不存在。
 * 本测试用 {@link TrialChamberCandidates}（复刻官方 random_spread 放置）枚举候选，
 * 用快速路径 {@link TrialChamberPredictor#chamberPasses} 统计过滤前后密室数。
 */
class BiomeFilterVerificationTest {

    @Test
    void chamberPassesEqualsPredictExists() {
        // chamberPasses 是 predict().exists() 的快速代理；抽样验证等价性，防止快速路径失真
        TrialChamberPredictor filtered = predictor(0L, true);
        int checked = 0;
        for (BlockPoint candidate : TrialChamberCandidates.enumerate(
                0L, -20_000, 20_000, -20_000, 20_000)) {
            boolean fast = filtered.chamberPasses(candidate);
            boolean full = filtered.predict(candidate).exists();
            assertEquals(full, fast, "chamberPasses 与 predict().exists() 不一致: " + candidate);
            if (++checked >= 100) {
                break;
            }
        }
        assertTrue(checked > 0, "应至少检查一个候选");
    }

    @Test
    void biomeFilterRemovesOnlyDeepDarkChambers() {
        long seed = 0L;
        int radius = 12_000;
        List<BlockPoint> candidates = TrialChamberCandidates.enumerate(
                seed, -radius, radius, -radius, radius);
        TrialChamberPredictor filtered = predictor(seed, true);
        TrialChamberPredictor unfiltered = predictor(seed, false);
        int withFilter = 0;
        int withoutFilter = 0;
        for (BlockPoint candidate : candidates) {
            if (unfiltered.chamberPasses(candidate)) {
                withoutFilter++;
            }
            if (filtered.chamberPasses(candidate)) {
                withFilter++;
            }
        }
        System.out.printf("候选=%d 过滤前=%d 过滤后=%d 差值=%d%n",
                candidates.size(), withoutFilter, withFilter,
                withoutFilter - withFilter);
        assertTrue(withFilter <= withoutFilter, "过滤不应增加密室数量");
        assertTrue(withFilter > 0, "区域内应存在密室");
        // 只有 deep_dark 被排除：采样点足够多时必然命中若干远古城市位置
        assertTrue(withoutFilter > withFilter,
                "过滤应删除 deep_dark 生物群系内的密室，差值=" + (withoutFilter - withFilter));
    }

    private static TrialChamberPredictor predictor(long seed, boolean applyBiomeCheck) {
        PoolRegistry pools = new PoolRegistry(new StructureTemplateManager());
        pools.loadAll();
        return new TrialChamberPredictor(
                seed, SimStructureConfig.trialChambers(), pools,
                new StructureTemplateManager(), applyBiomeCheck);
    }
}
