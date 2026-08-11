package cn.trialfinder.cli;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Resolves the detailed parameters of a trial-spawner configuration from the bundled datapack
 * JSONs ({@code data/minecraft/trial_spawner/trial_chamber/<...>.json}). Each trial spawner block
 * in a chamber carries a {@code normal_config} id like
 * {@code "minecraft:trial_chamber/ranged/skeleton/normal"}; this class maps that id to the config
 * file and extracts the spawn parameters.
 */
public final class SpawnerConfig {

    /** One spawn potential: an entity id and its relative weight. */
    public record Potential(String entity, int weight) {
    }

    /** Parsed trial-spawner parameters. */
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

        /** Primary entity id (highest-weight potential), or null when none. */
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
     * Loads and parses the trial-spawner config for the given config id (e.g.
     * {@code "minecraft:trial_chamber/ranged/skeleton/normal"}). Returns {@code null} when the
     * config id is blank or the resource is missing/unparsable.
     */
    public static Config load(String configId) {
        if (configId == null || configId.isBlank()) {
            return null;
        }
        String resource = toResourcePath(configId);
        try (InputStream in = SpawnerConfig.class.getResourceAsStream(resource)) {
            if (in == null) {
                return null;
            }
            JsonObject root = JsonParser.parseReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            return parse(configId, root);
        } catch (Exception e) {
            return null;
        }
    }

    /** Maps a config id to the classpath resource, e.g. {@code minecraft:...} → {@code /data/minecraft/...}. */
    static String toResourcePath(String configId) {
        String path = configId.startsWith("minecraft:")
                ? configId.substring("minecraft:".length())
                : configId;
        // minecraft:trial_chamber/x → data/minecraft/trial_spawner/trial_chamber/x.json
        return "/data/minecraft/trial_spawner/" + path + ".json";
    }

    private static Config parse(String configId, JsonObject root) {
        List<Potential> potentials = new ArrayList<>();
        JsonArray spawnPotentials = root.has("spawn_potentials")
                ? root.getAsJsonArray("spawn_potentials") : new JsonArray();
        for (JsonElement element : spawnPotentials) {
            JsonObject entry = element.getAsJsonObject();
            int weight = entry.has("weight") ? entry.get("weight").getAsInt() : 0;
            JsonObject data = entry.has("data") ? entry.getAsJsonObject("data") : null;
            String entity = null;
            if (data != null && data.has("entity")) {
                JsonObject entityObj = data.getAsJsonObject("entity");
                if (entityObj.has("id")) {
                    entity = entityObj.get("id").getAsString();
                }
            }
            if (entity != null) {
                potentials.add(new Potential(entity, weight));
            }
        }
        return new Config(
                configId,
                potentials,
                root.has("ticks_between_spawn") ? root.get("ticks_between_spawn").getAsInt() : 0,
                root.has("simultaneous_mobs") ? root.get("simultaneous_mobs").getAsDouble() : 0.0,
                root.has("simultaneous_mobs_added_per_player")
                        ? root.get("simultaneous_mobs_added_per_player").getAsDouble() : 0.0,
                root.has("total_mobs") ? root.get("total_mobs").getAsDouble() : 0.0,
                root.has("total_mobs_added_per_player")
                        ? root.get("total_mobs_added_per_player").getAsDouble() : 0.0);
    }
}
