package cn.trialfinder.sim.climate;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 用 {@code parameter_table.txt} 表尾的固定样本行验证生物群系最近邻查找。
 *
 * <p>样本行格式 {@code T v1..v6 -> biome}，值已按 ×10000 量化。官方 dump 的样本与
 * {@code MultiNoiseBiomeSource.getNoiseBiome} 语义一致：先 unquantize 回 float，
 * 再走 {@link Climate#target}/{@link Climate.ParameterList#findValue} 的 RTree 查找。
 *
 * <p>同时做一次 RTree vs 暴力全表扫描（{@code findValueBruteForce}）交叉验证，
 * 确保 26.2 RTree 构建（bucketize/sort/剪枝）没有改变最近邻语义。
 */
class BiomeParameterListFixtureTest {

    /** 从资源表尾读取全部 {@code T ... -> biome} 固定样本行。 */
    private static List<Fixture> fixtures() {
        List<Fixture> result = new ArrayList<>();
        for (String line : BiomeParameterList.readLines(
                "data/minecraft/worldgen/biome/parameter_table.txt")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            String[] tokens = trimmed.split("\\s+");
            if (!tokens[0].equals("T")) {
                continue;
            }
            if (tokens.length != 9) {
                throw new IllegalStateException("固定样本行格式错误: " + trimmed);
            }
            long[] values = new long[6];
            for (int i = 0; i < 6; i++) {
                values[i] = Long.parseLong(tokens[i + 1]);
            }
            result.add(new Fixture(values, tokens[8]));
        }
        if (result.isEmpty()) {
            throw new IllegalStateException("parameter_table.txt 中未找到任何 T 固定样本行");
        }
        return result;
    }

    @Test
    void fixturesMatchOfficialBiomes() {
        Climate.ParameterList<String> biomes = BiomeParameterList.load();
        List<Fixture> fixtures = fixtures();
        // 官方 dump 目前固定 8 条样本；数量变化时保留断言便于感知上游改动
        assertEquals(8, fixtures.size(), "固定样本数量");

        for (Fixture fixture : fixtures) {
            Climate.TargetPoint target = Climate.target(
                    Climate.unquantizeCoord(fixture.values[0]),
                    Climate.unquantizeCoord(fixture.values[1]),
                    Climate.unquantizeCoord(fixture.values[2]),
                    Climate.unquantizeCoord(fixture.values[3]),
                    Climate.unquantizeCoord(fixture.values[4]),
                    Climate.unquantizeCoord(fixture.values[5]));
            String actual = biomes.findValue(target);
            assertNotNull(actual, "RTree 应命中一个生物群系: " + fixture);
            assertEquals(fixture.biome, actual, "RTree 结果不匹配: " + fixture);

            String brute = biomes.findValueBruteForce(target);
            assertEquals(actual, brute, "RTree 与暴力全表扫描不一致: " + fixture);
        }
    }

    @Test
    void bruteForceMatchesRTreeAcrossAllBiomeMidpoints() {
        Climate.ParameterList<String> biomes = BiomeParameterList.load();
        int checked = 0;
        for (var pair : biomes.values()) {
            Climate.ParameterPoint point = pair.first();
            // 每个参数区间的中点作为目标点——这覆盖了区间内部退化（单点）与边界情形
            Climate.TargetPoint target = new Climate.TargetPoint(
                    midpoint(point.temperature()),
                    midpoint(point.humidity()),
                    midpoint(point.continentalness()),
                    midpoint(point.erosion()),
                    midpoint(point.depth()),
                    midpoint(point.weirdness()));
            String rtree = biomes.findValue(target);
            String brute = biomes.findValueBruteForce(target);
            assertEquals(brute, rtree,
                    "中点不匹配 biome=" + pair.second()
                            + " params=" + point + " target=" + target);
            checked++;
        }
        // 7594 条主世界生物群系参数行全部过一遍
        assertEquals(7594, checked, "参数表条目数");
    }

    private static long midpoint(Climate.Parameter parameter) {
        return (parameter.min() + parameter.max()) / 2;
    }

    private record Fixture(long[] values, String biome) {
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder("T ");
            for (long v : values) {
                sb.append(v).append(' ');
            }
            return sb.append("-> ").append(biome).toString();
        }
    }
}
