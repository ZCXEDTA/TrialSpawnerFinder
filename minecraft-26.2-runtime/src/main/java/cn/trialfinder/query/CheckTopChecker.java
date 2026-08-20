package cn.trialfinder.query;

import cn.minecraftfinder.core.BlockPoint;
import cn.trialfinder.io.ResultWriter;
import cn.trialfinder.model.SearchResult;
import cn.trialfinder.sim.pool.PoolRegistry;
import cn.trialfinder.sim.template.StructureTemplateManager;
import cn.trialfinder.sim.world.SimStructureConfig;
import cn.trialfinder.sim.world.TrialChamberPredictor;

import java.util.ArrayList;
import java.util.List;

/**
 * 检查前 N 个搜索结果，统计每个结果成员密室的快速/慢速刷怪笼与宝库数量（对应旧项目 {@code --check-top}）。
 *
 * <p>刷怪笼由 {@link TrialChamberPredictor} 生成；配置 id 含 {@code slow_ranged} 的为慢速刷怪笼
 * （ticks_between_spawn = 160），其余为快速（ticks_between_spawn = 20）。
 */
public final class CheckTopChecker {

    private final long seed;
    private final TrialChamberPredictor predictor;

    public CheckTopChecker(long seed, PoolRegistry pools, StructureTemplateManager templates) {
        this.seed = seed;
        this.predictor = new TrialChamberPredictor(
                seed, SimStructureConfig.trialChambers(), pools, templates);
    }

    /**
     * 检查前 {@code checkTop} 个结果（{@code checkTop <= 0} 返回空列表）。
     *
     * @param results  搜索结果（输出顺序）
     * @param checkTop 要检查的头部结果数
     */
    public List<ResultWriter.CheckResult> check(List<SearchResult> results, int checkTop) {
        if (checkTop <= 0 || results == null) {
            return List.of();
        }
        int limit = Math.min(checkTop, results.size());
        List<ResultWriter.CheckResult> out = new ArrayList<>(limit);
        for (int i = 0; i < limit; i++) {
            out.add(inspect(results.get(i)));
        }
        return out;
    }

    private ResultWriter.CheckResult inspect(SearchResult result) {
        int fast = 0;
        int slow = 0;
        int vaults = 0;
        for (BlockPoint structure : result.structures()) {
            TrialChamberPredictor.Prediction prediction = this.predictor.predict(structure);
            if (!prediction.exists()) {
                continue;
            }
            for (TrialChamberPredictor.SpawnerInfo info : prediction.spawnerInfos()) {
                if (isSlowSpawner(info.normalConfig())) {
                    slow++;
                } else {
                    fast++;
                }
            }
            vaults += prediction.vaults().size();
        }
        return new ResultWriter.CheckResult(fast, slow, vaults);
    }

    /** 配置 id 属于 {@code slow_ranged} 类别（ticks_between_spawn = 160）时为慢速刷怪笼。 */
    private static boolean isSlowSpawner(String config) {
        return config != null && config.contains("slow_ranged");
    }
}
