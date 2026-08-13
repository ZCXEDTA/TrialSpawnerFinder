package cn.trialfinder.query;

import cn.trialfinder.sim.json.Json;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 从捆绑的 datapack JSON（{@code data/minecraft/trial_spawner/trial_chamber/<...>.json}）解析
 * 试炼刷怪笼的详细参数。每个刷怪笼方块在模板 NBT 里带 {@code normal_config} id（如
 * {@code "minecraft:trial_chamber/ranged/skeleton/normal"}），本类把它映射到配置文件并提取刷怪参数。
 */
public final class SpawnerConfig {

    /** 一个刷怪候选：实体 id 和相对权重。 */
    public record Potential(String entity, int weight) {
    }

    /** 解析后的试炼刷怪笼参数。 */
    public record Config(
            String id,
            List<Potential> potentials,
            int ticksBetweenSpawn,
            double simultaneousMobs,
            double simultaneousMobsPerPlayer,
            double totalMobs,
            double totalMobsPerPlayer) {

        public Config {
            potentials = List.copyOf(potentials);
        }

        /** 主实体 id（权重最高的候选），无候选时返回 null。 */
        public String primaryEntity() {
            if (potentials.isEmpty()) {
                return null;
            }
            String best = potentials.get(0).entity();
            int bestWeight = potentials.get(0).weight();
            for (Potential p : potentials) {
                if (p.weight() > bestWeight) {
                    best = p.entity();
                    bestWeight = p.weight();
                }
            }
            return best;
        }
    }

    private SpawnerConfig() {
    }

    /**
     * 解析指定 config id（如 {@code "minecraft:trial_chamber/ranged/skeleton/normal"}）对应的刷怪笼配置。
     * config id 为空或资源缺失/解析失败时返回 null。
     */
    public static Config load(String configId) {
        if (configId == null || configId.isBlank()) {
            return null;
        }
        String resource = toResourcePath(configId);
        try (InputStream in = SpawnerConfig.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                return null;
            }
            Json.Object root = (Json.Object) Json.parse(
                    new String(in.readAllBytes(), StandardCharsets.UTF_8));
            return parse(configId, root);
        } catch (Exception e) {
            return null;
        }
    }

    /** 把 config id 映射到 classpath 资源，如 {@code minecraft:...} → {@code data/minecraft/...}。 */
    static String toResourcePath(String configId) {
        String path = configId.startsWith("minecraft:")
                ? configId.substring("minecraft:".length())
                : configId;
        return "data/minecraft/trial_spawner/" + path + ".json";
    }

    private static Config parse(String configId, Json.Object root) {
        List<Potential> potentials = new ArrayList<>();
        if (root.has("spawn_potentials")) {
            Json.Array spawnPotentials = root.getArray("spawn_potentials");
            for (Json.JsonValue element : spawnPotentials.elements()) {
                Json.Object entry = (Json.Object) element;
                int weight = entry.getInt("weight");
                if (entry.has("data")) {
                    Json.Object data = (Json.Object) entry.get("data");
                    if (data.has("entity")) {
                        Json.Object entity = (Json.Object) data.get("entity");
                        String id = entity.getString("id");
                        if (!id.isEmpty()) {
                            potentials.add(new Potential(id, weight));
                        }
                    }
                }
            }
        }
        return new Config(
                configId,
                potentials,
                root.getInt("ticks_between_spawn"),
                doubleOf(root, "simultaneous_mobs"),
                doubleOf(root, "simultaneous_mobs_added_per_player"),
                doubleOf(root, "total_mobs"),
                doubleOf(root, "total_mobs_added_per_player"));
    }

    private static double doubleOf(Json.Object root, String key) {
        if (!root.has(key)) {
            return 0.0;
        }
        Json.JsonValue value = root.get(key);
        if (value instanceof Json.Num num) {
            return num.doubleValue();
        }
        return 0.0;
    }
}
