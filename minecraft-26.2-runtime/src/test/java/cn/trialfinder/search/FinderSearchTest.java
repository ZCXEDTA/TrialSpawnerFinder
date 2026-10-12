package cn.trialfinder.search;

import cn.minecraftfinder.core.AreaShape;
import cn.minecraftfinder.core.BlockPoint;
import cn.minecraftfinder.core.ProgressReporter;
import cn.trialfinder.config.FinderConfig;
import cn.trialfinder.model.SearchResult;
import cn.trialfinder.model.SpawnerPoint;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FinderSearchTest {
    @Test
    void boundsThreadsAndInFlightTasks() {
        assertEquals(1, FinderSearch.fineThreadCount(1));
        assertEquals(2, FinderSearch.fineThreadCount(4));
        assertEquals(14, FinderSearch.fineThreadCount(16));
        assertEquals(4, FinderSearch.maxInFlightTasks(1));
        assertEquals(8, FinderSearch.maxInFlightTasks(4));
        assertEquals(56, FinderSearch.maxInFlightTasks(16));
    }

    @Test
    void statusMetricIncludesRatioWhenTotalKnown() {
        assertEquals("候选：25/100=25%", FinderSearch.formatStatusMetric("候选", 25, 100));
        assertEquals("候选 25", FinderSearch.formatStatusMetric("候选", 25, -1));
        assertEquals("候选：150/150=100%", FinderSearch.formatStatusMetric("候选", 150, 100));
        assertEquals("聚类：0/10=0%", FinderSearch.formatStatusMetric("聚类", 0, 10));
    }

    @Test
    void statusProjectsClusterAndPruneTotalsFromCandidateProgress() {
        assertEquals(
                "候选：100/1,000=10%；聚类：5/50=10%；裁剪：2/20=10%",
                FinderSearch.formatStatus(100, 1_000, 5, 2));
        assertEquals(
                "候选：0/1,000=0%；聚类：0/0=0%；裁剪：0/0=0%",
                FinderSearch.formatStatus(0, 1_000, 0, 0));
        assertEquals(
                "候选：150/150=100%；聚类：20/20=100%；裁剪：7/7=100%",
                FinderSearch.formatStatus(150, 100, 20, 7));
        assertEquals(50, FinderSearch.projectTotal(5, 100, 1_000));
        assertEquals(20, FinderSearch.projectTotal(20, 100, 50));
    }

    @Test
    void predictedCoordinatesUseTheExactCenterRankingPath() {
        FinderConfig config = new FinderConfig(
                1, 0, 0, 10_000, false, AreaShape.CIRCLE,
                128, AreaShape.CIRCLE, 2, 0, 4, 262_144);
        FinderSearch search = new FinderSearch(
                config, Path.of("results.csv"), ProgressReporter.NONE,
                new cn.trialfinder.sim.pool.PoolRegistry(
                        new cn.trialfinder.sim.template.StructureTemplateManager()),
                new cn.trialfinder.sim.template.StructureTemplateManager());
        BlockPoint first = new BlockPoint(0, 0);
        BlockPoint absent = new BlockPoint(32, 0);
        BlockPoint second = new BlockPoint(64, 0);
        Map<BlockPoint, List<SpawnerPoint>> spawners = Map.of(
                first, List.of(new SpawnerPoint(0, 0, 0), new SpawnerPoint(4, 0, 0)),
                absent, List.of(),
                second, List.of(new SpawnerPoint(64, 0, 0)));

        SearchResult result = search.evaluateCluster(
                List.of(first, absent, second), spawners::get);
        ExactCenterOptimizer.CenterScore expected = ExactCenterOptimizer.find(
                AreaShape.CIRCLE, 128, List.of(first, second),
                new TreeSet<>(spawners.values().stream().flatMap(List::stream).toList()));

        assertEquals(new SearchResult(
                expected.x(), expected.z(), 2, expected.spawners(), List.of(first, second)), result);
    }
}
