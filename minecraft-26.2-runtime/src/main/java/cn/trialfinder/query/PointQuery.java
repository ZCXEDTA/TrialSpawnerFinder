package cn.trialfinder.query;

import cn.minecraftfinder.core.BlockPoint;
import cn.trialfinder.search.TrialChamberCandidates;
import cn.trialfinder.sim.pool.PoolRegistry;
import cn.trialfinder.sim.template.StructureTemplateManager;
import cn.trialfinder.sim.world.SimStructureConfig;
import cn.trialfinder.sim.world.TrialChamberPredictor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 定点查询：对给定坐标点，枚举周围半径内的试炼密室候选，用 {@link TrialChamberPredictor}
 * 生成每个密室的刷怪笼坐标。不需要全量搜索，秒级返回单个坐标附近的密室详情。
 *
 * <p>注意 {@link TrialChamberPredictor} 非线程安全（内部有可变随机状态）；并发查询需每线程一个实例。
 */
public final class PointQuery {
    private final long seed;
    private final int radius;
    private final TrialChamberPredictor predictor;

    public PointQuery(long seed, int radius, PoolRegistry pools,
                      StructureTemplateManager templateManager) {
        this.seed = seed;
        this.radius = radius;
        this.predictor = new TrialChamberPredictor(
                seed, SimStructureConfig.trialChambers(), pools, templateManager);
    }

    public long seed() {
        return this.seed;
    }

    public int radius() {
        return this.radius;
    }

    /** 查询一个点：返回该点半径内的密室（含各自刷怪笼坐标），按 X 再按 Z 排序。 */
    public QueryResult query(int qx, int qz) {
        long radiusSq = (long) this.radius * this.radius;
        BlockPoint queryPoint = new BlockPoint(qx, qz);
        List<BlockPoint> candidates = TrialChamberCandidates.enumerate(
                this.seed, (long) qx - this.radius, (long) qx + this.radius,
                (long) qz - this.radius, (long) qz + this.radius);
        List<ChamberOut> chambers = new ArrayList<>();
        for (BlockPoint candidate : candidates) {
            if (candidate.distanceSquared(queryPoint) > radiusSq) {
                continue;
            }
            TrialChamberPredictor.Prediction prediction = this.predictor.predict(candidate);
            if (!prediction.exists()) {
                continue;
            }
            List<SpawnerOut> spawners = new ArrayList<>(prediction.spawnerInfos().size());
            for (TrialChamberPredictor.SpawnerInfo info : prediction.spawnerInfos()) {
                spawners.add(toSpawnerOut(info));
            }
            if (spawners.isEmpty()) {
                // 兜底：spawnerInfos 为空时退回坐标列表
                for (cn.trialfinder.model.SpawnerPoint spawner : prediction.theoreticalSpawners()) {
                    spawners.add(new SpawnerOut(
                            spawner.x(), spawner.y(), spawner.z(),
                            "", "", null, 0, 0, 0.0, 0.0, 0.0, 0.0));
                }
            }
            List<VaultOut> vaults = prediction.vaults().stream()
                    .map(vault -> new VaultOut(vault.x(), vault.y(), vault.z(), vault.ominous()))
                    .toList();
            chambers.add(new ChamberOut(candidate.x(), candidate.z(), spawners, vaults));
        }
        chambers.sort(Comparator.comparingInt(ChamberOut::x).thenComparingInt(ChamberOut::z));
        return new QueryResult(qx, qz, chambers);
    }

    /** 一个刷怪笼：坐标 + 从 normal_config 解析的刷怪参数。 */
    public record SpawnerOut(int x, int y, int z, String mob, String config, String entity,
                             int weight, int ticksBetweenSpawn,
                             double simultaneousMobs, double simultaneousMobsPerPlayer,
                             double totalMobs, double totalMobsPerPlayer) {
        public SpawnerOut(int x, int y, int z) {
            this(x, y, z, "", "", null, 0, 0, 0.0, 0.0, 0.0, 0.0);
        }
    }

    private static SpawnerOut toSpawnerOut(TrialChamberPredictor.SpawnerInfo info) {
        String configId = info.normalConfig();
        SpawnerConfig.Config cfg = SpawnerConfig.load(configId);
        String entity = cfg != null ? cfg.primaryEntity() : null;
        String mob = entity != null
                ? entity.substring(entity.lastIndexOf(':') + 1)
                : "";
        int weight = cfg != null && !cfg.potentials().isEmpty()
                ? cfg.potentials().get(0).weight() : 0;
        int ticks = cfg != null ? cfg.ticksBetweenSpawn() : 0;
        double sim = cfg != null ? cfg.simultaneousMobs() : 0.0;
        double simPer = cfg != null ? cfg.simultaneousMobsPerPlayer() : 0.0;
        double total = cfg != null ? cfg.totalMobs() : 0.0;
        double totalPer = cfg != null ? cfg.totalMobsPerPlayer() : 0.0;
        return new SpawnerOut(info.x(), info.y(), info.z(), mob, configId, entity,
                weight, ticks, sim, simPer, total, totalPer);
    }

    /** 一个宝库：坐标 + 是否不祥。 */
    public record VaultOut(int x, int y, int z, boolean ominous) {
    }

    /** 一个密室：起点坐标 + 该密室的刷怪笼列表 + 宝库列表。 */
    public record ChamberOut(int x, int z, List<SpawnerOut> spawners, List<VaultOut> vaults) {
        public ChamberOut(int x, int z, List<SpawnerOut> spawners) {
            this(x, z, spawners, List.of());
        }

        public ChamberOut {
            spawners = List.copyOf(spawners);
            vaults = List.copyOf(vaults);
        }

        public int spawnerCount() {
            return this.spawners.size();
        }

        public int vaultCount() {
            return this.vaults.size();
        }
    }

    /** 一个查询点的聚合结果。 */
    public record QueryResult(int x, int z, List<ChamberOut> chambers) {
        public QueryResult {
            chambers = List.copyOf(chambers);
        }

        public int chamberCount() {
            return this.chambers.size();
        }

        public int spawnerCount() {
            return this.chambers.stream().mapToInt(ChamberOut::spawnerCount).sum();
        }
    }
}
