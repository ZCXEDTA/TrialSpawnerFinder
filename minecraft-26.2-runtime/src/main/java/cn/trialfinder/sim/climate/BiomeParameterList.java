package cn.trialfinder.sim.climate;

import cn.trialfinder.sim.resource.ClasspathResourceLoader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 从 {@code data/minecraft/worldgen/biome/parameter_table.txt} 加载主世界生物群系参数表。
 * 行格式（与 dump 一致）：
 * <pre>biome tempMin tempMax humMin humMax contMin contMax erosMin erosMax depthMin depthMax weirdMin weirdMax offset</pre>
 * 值已按 ×10000 量化。表尾的 {@code T ... -> biome} 固定样本行用于验证，不进入索引。
 */
public final class BiomeParameterList {
    private static final String RESOURCE =
            "data/minecraft/worldgen/biome/parameter_table.txt";

    private BiomeParameterList() {
    }

    public static Climate.ParameterList<String> load() {
        List<Climate.Pair<Climate.ParameterPoint, String>> entries = new ArrayList<>();
        for (String line : readLines(RESOURCE)) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            String[] tokens = trimmed.split("\\s+");
            if (tokens[0].equals("T")) {
                continue; // 固定样本行，仅用于验证
            }
            if (tokens.length != 14) {
                throw new IllegalStateException(
                        "生物群系参数行格式错误 (" + RESOURCE + "): " + trimmed);
            }
            Climate.ParameterPoint point = new Climate.ParameterPoint(
                    new Climate.Parameter(longOf(tokens, 1), longOf(tokens, 2)),
                    new Climate.Parameter(longOf(tokens, 3), longOf(tokens, 4)),
                    new Climate.Parameter(longOf(tokens, 5), longOf(tokens, 6)),
                    new Climate.Parameter(longOf(tokens, 7), longOf(tokens, 8)),
                    new Climate.Parameter(longOf(tokens, 9), longOf(tokens, 10)),
                    new Climate.Parameter(longOf(tokens, 11), longOf(tokens, 12)),
                    longOf(tokens, 13));
            entries.add(new Climate.Pair<>(point, tokens[0]));
        }
        if (entries.isEmpty()) {
            throw new IllegalStateException("生物群系参数表为空: " + RESOURCE);
        }
        return new Climate.ParameterList<>(List.copyOf(entries));
    }

    private static long longOf(String[] tokens, int index) {
        return Long.parseLong(tokens[index]);
    }

    static List<String> readLines(String resource) {
        try (InputStream stream = ClasspathResourceLoader.open(resource)) {
            if (stream == null) {
                throw new IllegalStateException("缺失资源: " + resource);
            }
            return List.of(new String(stream.readAllBytes(), StandardCharsets.UTF_8)
                    .split("\\R"));
        } catch (IOException e) {
            throw new IllegalStateException("读取资源失败: " + resource, e);
        }
    }
}
