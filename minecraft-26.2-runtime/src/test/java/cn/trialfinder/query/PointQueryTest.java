package cn.trialfinder.query;

import cn.trialfinder.sim.pool.PoolRegistry;
import cn.trialfinder.sim.template.StructureTemplateManager;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PointQueryTest {

    private static PointQuery newQuery(long seed, int radius) {
        StructureTemplateManager templates = new StructureTemplateManager();
        PoolRegistry pools = new PoolRegistry(templates);
        pools.loadAll();
        return new PointQuery(seed, radius, pools, templates);
    }

    @Test
    void queryFindsKnownChambers() {
        // seed=0 全量搜索已知密室对：3032,7272 与 3272,7224（查询点取聚类中心 3145,7232）
        PointQuery query = newQuery(0L, 2000);
        PointQuery.QueryResult result = query.query(3145, 7232);
        assertTrue(result.chamberCount() >= 2, "半径 2000 内应有至少 2 个密室，实际 " + result.chamberCount());
        assertTrue(result.spawnerCount() > 0, "密室应含刷怪笼");
        boolean foundFirst = result.chambers().stream()
                .anyMatch(c -> c.x() == 3032 && c.z() == 7272);
        boolean foundSecond = result.chambers().stream()
                .anyMatch(c -> c.x() == 3272 && c.z() == 7224);
        assertTrue(foundFirst, "应包含已知密室 3032,7272");
        assertTrue(foundSecond, "应包含已知密室 3272,7224");
        for (PointQuery.ChamberOut chamber : result.chambers()) {
            assertTrue(chamber.spawnerCount() > 0, "密室 " + chamber.x() + "," + chamber.z() + " 应有刷怪笼");
            for (PointQuery.SpawnerOut spawner : chamber.spawners()) {
                assertTrue(spawner.y() >= -64 && spawner.y() <= 320,
                        "刷怪笼 Y 应在世界范围内: " + spawner);
            }
        }
    }

    @Test
    void emptyRadiusFindsNothing() {
        PointQuery query = newQuery(0L, 1);
        PointQuery.QueryResult result = query.query(0, 0);
        assertEquals(0, result.chamberCount());
    }

    @Test
    void tinyRadiusAtFarPointFindsNothing() {
        // 远离任何密室起点的查询点 + 极小半径不应命中
        PointQuery query = newQuery(0L, 1);
        PointQuery.QueryResult result = query.query(100_000, 100_000);
        assertEquals(0, result.chamberCount());
    }

    @Test
    void resultSortsByXThenZ() {
        PointQuery query = newQuery(0L, 5000);
        PointQuery.QueryResult result = query.query(0, 0);
        for (int i = 1; i < result.chambers().size(); i++) {
            PointQuery.ChamberOut prev = result.chambers().get(i - 1);
            PointQuery.ChamberOut curr = result.chambers().get(i);
            assertTrue(prev.x() < curr.x() || (prev.x() == curr.x() && prev.z() <= curr.z()),
                    "密室应按 X 再按 Z 排序");
        }
    }
}
