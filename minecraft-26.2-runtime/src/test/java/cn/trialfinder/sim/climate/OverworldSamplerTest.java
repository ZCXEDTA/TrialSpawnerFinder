package cn.trialfinder.sim.climate;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 端到端冒烟测试：构造 {@link OverworldSampler} 会完整走一遍
 * 噪声实例化（Xoroshiro → forkPositional → fromHashOf）、官方 DF JSON 解析、
 * shifted_noise/spline/y_clamped_gradient 采样链与 RTree 生物群系查找。
 */
class OverworldSamplerTest {

    @Test
    void tagContainsExpectedBiomeCount() {
        Set<String> tag = TrialChambersBiomeTag.load();
        assertEquals(54, tag.size(), "trial_chambers 生物群系标签应为 54 个");
        assertTrue(tag.contains("minecraft:plains"));
        // deep_dark 是远古城市生物群系，试炼密室不生成于此，不应在标签内
        assertTrue(!tag.contains("minecraft:deep_dark"));
    }

    @Test
    void constructsAndSamplesKnownPositions() {
        long seed = 123456789L;
        OverworldSampler sampler = new OverworldSampler(seed);

        // 采样一批地表坐标，确认返回的 id 都来自参数表（非空且可被 isTrialChamberBiome 判定）
        int[][] positions = {
                {8, 64, 8},
                {-500, 70, 1200},
                {304, -30, -208},
                {9000, 90, -7400},
                {-12345, 50, 67890},
        };
        int inTag = 0;
        for (int[] pos : positions) {
            String biome = sampler.sampleBiome(pos[0], pos[1], pos[2]);
            assertNotNull(biome, "sampleBiome(" + pos[0] + "," + pos[1] + "," + pos[2] + ")");
            assertTrue(biome.startsWith("minecraft:"), "非法生物群系 id: " + biome);
            if (sampler.isTrialChamberBiome(biome)) {
                inTag++;
            }
        }
        // 这 5 个点至少命中一个可生成试炼密室的生物群系，保证过滤链没有把结果全部抹掉
        assertTrue(inTag > 0, "采样结果应命中 trial_chambers 标签内的生物群系");
    }

    @Test
    void differentSeedsProduceDifferentBiomes() {
        OverworldSampler a = new OverworldSampler(111L);
        OverworldSampler b = new OverworldSampler(222L);
        int differing = 0;
        for (int x = 0; x < 64; x += 8) {
            for (int z = 0; z < 64; z += 8) {
                String ba = a.sampleBiome(x, 64, z);
                String bb = b.sampleBiome(x, 64, z);
                if (!ba.equals(bb)) {
                    differing++;
                }
            }
        }
        assertTrue(differing > 0, "不同种子应产生不同生物群系分布");
    }
}
