package cn.trialfinder.sim.biome;

import java.util.ArrayList;
import java.util.List;

/**
 * A pragmatic subset of the overworld biome parameter table (the game's
 * {@code MultiNoiseBiomeSourceParameterList$Preset.OVERWORLD}). Only the entries needed to
 * distinguish "biomes that never host trial chambers" (oceans, deep oceans, frozen oceans, beaches)
 * from the broad set of overworld land biomes in {@link TrialChambersBiomes} are included — the
 * trial-chambers tag is extremely permissive (essentially all land), so a coarse but correct
 * land-vs-ocean split is sufficient for {@code --biome-check}.
 *
 * <p><b>Approximation:</b> the temperature/humidity ranges below match the vanilla parameter table;
 * the other four dimensions use generous ranges so the nearest-point search is dominated by the two
 * exact dimensions. This is intentionally NOT bit-exact with the game — it trades precision for a
 * usable filter that excludes obvious non-land biomes.
 */
public final class OverworldBiomeParameters {

    private OverworldBiomeParameters() {
    }

    /**
     * Builds the parameter list. The temperature/humidity axis separates cold ocean / ocean /
     * deep ocean / beach from the land cluster.
     */
    public static Climate.ParameterList<String> create() {
        List<Climate.Entry<String>> entries = new ArrayList<>();

        // Cold ocean / frozen ocean: very low temperature, near-neutral humidity.
        entries.add(entry(Climate.Parameter.range(-1.0f, -0.45f), Climate.Parameter.range(-1.0f, 0.3f),
                Climate.Parameter.range(-1.0f, 0.3f), Climate.Parameter.range(-0.5f, 1.0f),
                Climate.Parameter.range(-1.0f, 1.0f), Climate.Parameter.range(-1.0f, 1.0f), "minecraft:cold_ocean"));
        entries.add(entry(Climate.Parameter.range(-1.0f, -0.45f), Climate.Parameter.range(-1.0f, 0.3f),
                Climate.Parameter.range(-1.0f, 0.3f), Climate.Parameter.range(-0.5f, 1.0f),
                Climate.Parameter.range(-1.0f, 1.0f), Climate.Parameter.range(-1.0f, 1.0f), "minecraft:frozen_ocean"));

        // Ocean (temperate): mid temperature.
        entries.add(entry(Climate.Parameter.range(-0.45f, 0.4f), Climate.Parameter.range(-1.0f, 0.5f),
                Climate.Parameter.range(-1.0f, 0.3f), Climate.Parameter.range(-0.5f, 1.0f),
                Climate.Parameter.range(-1.0f, 1.0f), Climate.Parameter.range(-1.0f, 1.0f), "minecraft:ocean"));

        // Deep ocean variants.
        entries.add(entry(Climate.Parameter.range(-1.0f, 0.4f), Climate.Parameter.range(-1.0f, 0.5f),
                Climate.Parameter.range(-1.0f, 0.2f), Climate.Parameter.range(-0.5f, 1.0f),
                Climate.Parameter.range(-1.0f, 0.0f), Climate.Parameter.range(-1.0f, 1.0f), "minecraft:deep_ocean"));
        entries.add(entry(Climate.Parameter.range(-1.0f, -0.45f), Climate.Parameter.range(-1.0f, 0.3f),
                Climate.Parameter.range(-1.0f, 0.2f), Climate.Parameter.range(-0.5f, 1.0f),
                Climate.Parameter.range(-1.0f, 0.0f), Climate.Parameter.range(-1.0f, 1.0f), "minecraft:deep_frozen_ocean"));

        // Beach: near sea level, warm-ish.
        entries.add(entry(Climate.Parameter.range(-0.4f, 0.6f), Climate.Parameter.range(-1.0f, 0.5f),
                Climate.Parameter.range(0.2f, 0.9f), Climate.Parameter.range(0.0f, 1.0f),
                Climate.Parameter.range(-0.5f, 0.5f), Climate.Parameter.range(-1.0f, 1.0f), "minecraft:beach"));
        entries.add(entry(Climate.Parameter.range(-0.4f, 0.6f), Climate.Parameter.range(-1.0f, 0.5f),
                Climate.Parameter.range(0.2f, 0.9f), Climate.Parameter.range(0.0f, 1.0f),
                Climate.Parameter.range(-0.5f, 0.5f), Climate.Parameter.range(-1.0f, 1.0f), "minecraft:snowy_beach"));

        // Land cluster (temperate to hot, any humidity/continentalness): the permissive trial-chambers
        // land set. Use wide ranges so the exact temp/humidity picks the right land biome.
        entries.add(land(Climate.Parameter.range(0.1f, 0.55f), Climate.Parameter.range(-1.0f, 0.3f), "minecraft:plains"));
        entries.add(land(Climate.Parameter.range(0.1f, 0.55f), Climate.Parameter.range(-0.3f, 0.5f), "minecraft:sunflower_plains"));
        entries.add(land(Climate.Parameter.range(0.1f, 0.4f), Climate.Parameter.range(0.3f, 1.0f), "minecraft:forest"));
        entries.add(land(Climate.Parameter.range(-0.2f, 0.1f), Climate.Parameter.range(-1.0f, 0.3f), "minecraft:snowy_plains"));
        entries.add(land(Climate.Parameter.range(-0.2f, 0.1f), Climate.Parameter.range(0.3f, 1.0f), "minecraft:snowy_taiga"));
        entries.add(land(Climate.Parameter.range(0.4f, 0.9f), Climate.Parameter.range(0.3f, 1.0f), "minecraft:jungle"));
        entries.add(land(Climate.Parameter.range(0.4f, 0.9f), Climate.Parameter.range(-1.0f, 0.0f), "minecraft:desert"));
        entries.add(land(Climate.Parameter.range(0.4f, 0.9f), Climate.Parameter.range(0.0f, 0.4f), "minecraft:savanna"));
        entries.add(land(Climate.Parameter.range(-0.4f, 0.4f), Climate.Parameter.range(-0.5f, 0.5f), "minecraft:swamp"));
        entries.add(land(Climate.Parameter.range(-0.4f, 0.4f), Climate.Parameter.range(0.5f, 1.0f), "minecraft:dark_forest"));
        entries.add(land(Climate.Parameter.range(-0.6f, -0.1f), Climate.Parameter.range(-1.0f, 0.4f), "minecraft:taiga"));
        entries.add(land(Climate.Parameter.range(-0.5f, 0.2f), Climate.Parameter.range(-1.0f, 1.0f), "minecraft:meadow"));
        entries.add(land(Climate.Parameter.range(-0.8f, -0.4f), Climate.Parameter.range(-1.0f, 0.5f), "minecraft:ice_spikes"));
        entries.add(land(Climate.Parameter.range(-0.2f, 0.6f), Climate.Parameter.range(-1.0f, 1.0f), "minecraft:stony_shore"));
        entries.add(land(Climate.Parameter.range(-0.6f, 0.6f), Climate.Parameter.range(-1.0f, 1.0f), "minecraft:deep_dark"));
        entries.add(land(Climate.Parameter.range(-0.6f, 0.6f), Climate.Parameter.range(-1.0f, 1.0f), "minecraft:lush_caves"));
        entries.add(land(Climate.Parameter.range(-0.6f, 0.6f), Climate.Parameter.range(-1.0f, 1.0f), "minecraft:dripstone_caves"));

        // Fallback: any other land coordinate resolves to plains (always allowed).
        entries.add(land(Climate.Parameter.range(-1.0f, 1.0f), Climate.Parameter.range(-1.0f, 1.0f), "minecraft:plains"));

        return new Climate.ParameterList<>(entries);
    }

    private static Climate.Entry<String> entry(Climate.Parameter temp, Climate.Parameter hum,
                                               Climate.Parameter cont, Climate.Parameter ero,
                                               Climate.Parameter depth, Climate.Parameter weird,
                                               String biome) {
        return new Climate.Entry<>(
                new Climate.ParameterPoint(temp, hum, cont, ero, depth, weird, Climate.Parameter.point(0.0f)),
                biome);
    }

    /** Land entry: broad non-temperature dimensions so temp/humidity dominate the nearest-point search. */
    private static Climate.Entry<String> land(Climate.Parameter temp, Climate.Parameter hum, String biome) {
        return entry(temp, hum,
                Climate.Parameter.range(-1.0f, 1.0f),
                Climate.Parameter.range(-1.0f, 1.0f),
                Climate.Parameter.range(-1.0f, 1.0f),
                Climate.Parameter.range(-1.0f, 1.0f),
                biome);
    }
}
