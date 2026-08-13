package cn.trialfinder.sim.data;

import cn.trialfinder.sim.json.Json;
import cn.trialfinder.sim.resource.ClasspathResourceLoader;
import cn.trialfinder.sim.util.WeightedList;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 26.2 {@code minecraft:trial_chambers} 的数据驱动常量与配置。
 * 从资源 {@code data/minecraft/worldgen/structure_set/trial_chambers.json} 和
 * {@code data/minecraft/worldgen/structure/trial_chambers.json} 提取。
 */
public final class TrialChambersData {
    private static final String STRUCTURE_JSON = "data/minecraft/worldgen/structure/trial_chambers.json";

    private TrialChambersData() {
    }

    // ---- A 流：structure_set/trial_chambers.json ----
    public static final int SPACING_CHUNKS = 34;
    public static final int SEPARATION_CHUNKS = 12;
    public static final int SALT = 94_251_327;

    // ---- B 流：structure/trial_chambers.json ----
    public static final String START_POOL = "trial_chambers/chamber/end";
    public static final int SIZE = 20;
    public static final int START_HEIGHT_MIN = -40;
    public static final int START_HEIGHT_MAX = -20;
    public static final boolean USE_EXPANSION_HACK = false;
    public static final int MAX_DISTANCE_FROM_CENTER = 116;
    public static final int DIMENSION_PADDING = 10;

    private static volatile List<PoolAliasBinding> aliases;

    /** C 流别名绑定（从 structure JSON 的 pool_aliases 解析）。 */
    public static List<PoolAliasBinding> aliases() {
        List<PoolAliasBinding> result = aliases;
        if (result == null) {
            synchronized (TrialChambersData.class) {
                result = aliases;
                if (result == null) {
                    result = loadAliases();
                    aliases = result;
                }
            }
        }
        return result;
    }

    private static List<PoolAliasBinding> loadAliases() {
        try (InputStream stream = ClasspathResourceLoader.open(STRUCTURE_JSON)) {
            if (stream == null) {
                throw new IllegalStateException("缺少资源: " + STRUCTURE_JSON);
            }
            String text = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            Json.Object root = (Json.Object) Json.parse(text);
            Json.Array poolAliases = root.getArray("pool_aliases");
            List<PoolAliasBinding> bindings = new ArrayList<>();
            for (Json.JsonValue aliasJson : poolAliases.elements()) {
                bindings.add(parseAlias((Json.Object) aliasJson));
            }
            return List.copyOf(bindings);
        } catch (IOException e) {
            throw new IllegalStateException("解析试炼密室别名失败: " + STRUCTURE_JSON, e);
        }
    }

    private static PoolAliasBinding parseAlias(Json.Object alias) {
        String type = alias.getString("type");
        return switch (type) {
            case "minecraft:direct" -> PoolAliasBinding.direct(
                    alias.getString("alias"), alias.getString("target"));
            case "minecraft:random" -> {
                String aliasId = alias.getString("alias");
                WeightedList.Builder<String> builder = WeightedList.builder();
                for (Json.JsonValue target : alias.getArray("targets").elements()) {
                    Json.Object targetObject = (Json.Object) target;
                    builder.add(targetObject.getString("data"), targetObject.getInt("weight"));
                }
                yield PoolAliasBinding.random(aliasId, builder.build());
            }
            case "minecraft:random_group" -> {
                WeightedList.Builder<List<PoolAliasBinding>> groups = WeightedList.builder();
                for (Json.JsonValue groupJson : alias.getArray("groups").elements()) {
                    Json.Object group = (Json.Object) groupJson;
                    Json.Array data = group.getArray("data");
                    List<PoolAliasBinding> groupBindings = new ArrayList<>();
                    for (Json.JsonValue bindingJson : data.elements()) {
                        groupBindings.add(parseAlias((Json.Object) bindingJson));
                    }
                    groups.add(List.copyOf(groupBindings), group.getInt("weight"));
                }
                yield PoolAliasBinding.randomGroup(groups.build());
            }
            default -> throw new IllegalStateException("未知别名类型: " + type);
        };
    }
}
