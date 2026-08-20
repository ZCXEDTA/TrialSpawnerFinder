package cn.trialfinder.sim.climate;

import cn.trialfinder.sim.json.Json;
import cn.trialfinder.sim.resource.ClasspathResourceLoader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

/**
 * 加载 {@code #minecraft:has_structure/trial_chambers} 生物群系标签（54 个）。
 * 试炼密室结构只会在这些生物群系里生成；模拟器在 {@code biomePosition} 采样生物群系，
 * 不在标签内则判定不存在。
 */
public final class TrialChambersBiomeTag {
    private static final String RESOURCE =
            "data/minecraft/tags/worldgen/biome/has_structure/trial_chambers.json";

    private TrialChambersBiomeTag() {
    }

    public static Set<String> load() {
        String text;
        try (InputStream stream = ClasspathResourceLoader.open(RESOURCE)) {
            if (stream == null) {
                throw new IllegalStateException("缺失资源: " + RESOURCE);
            }
            text = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("读取资源失败: " + RESOURCE, e);
        }
        Json.Array values = Json.parse(text).asObject().get("values").asArray();
        Set<String> result = new HashSet<>(values.size());
        for (int index = 0; index < values.size(); index++) {
            result.add(values.get(index).stringValue());
        }
        if (result.isEmpty()) {
            throw new IllegalStateException("trial_chambers 标签为空: " + RESOURCE);
        }
        return Set.copyOf(result);
    }
}
