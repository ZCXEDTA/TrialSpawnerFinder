package cn.trialfinder.sim.climate;

import cn.trialfinder.sim.json.Json;
import cn.trialfinder.sim.noise.NormalNoise;
import cn.trialfinder.sim.noise.NoiseParameters;
import cn.trialfinder.sim.random.PositionalRandomFactory;
import cn.trialfinder.sim.random.XoroshiroRandomSource;
import cn.trialfinder.sim.resource.ClasspathResourceLoader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * 主世界生物群系采样器（26.2 语义）——复刻 {@code RandomState} 中气候采样器的构建。
 * <ul>
 *   <li>噪声注册：{@code random = new XoroshiroRandomSource(seed).forkPositional()}；
 *       每个噪声 {@code NormalNoise.create(random.fromHashOf("minecraft:"+key), params)}。</li>
 *   <li>采样器：temperature/vegetation 为未缓存的 {@code shifted_noise}，
 *       continentalness/erosion/depth/weirdness 直接加载官方 DF JSON
 *       （{@code overworld/continents|erosion|depth|ridges}）。</li>
 *   <li>生物群系查找：{@link BiomeParameterList} + {@link TrialChambersBiomeTag}。</li>
 * </ul>
 */
public final class OverworldSampler {
    private static final List<String> NOISE_KEYS = List.of(
            "temperature", "vegetation", "continentalness", "erosion", "ridge", "offset");

    private final Climate.Sampler sampler;
    private final Climate.ParameterList<String> biomes;
    private final Set<String> trialChambersBiomes;

    public OverworldSampler(long seed) {
        PositionalRandomFactory noiseRandom =
                new XoroshiroRandomSource(seed).forkPositional();
        Map<String, NormalNoise> noises = new HashMap<>();
        for (String key : NOISE_KEYS) {
            noises.put(key, NormalNoise.create(
                    noiseRandom.fromHashOf("minecraft:" + key),
                    loadNoiseParameters(key)));
        }
        Function<String, NormalNoise> noiseResolver =
                key -> noises.get(key.substring("minecraft:".length()));
        DensityFunctionCodec codec =
                new DensityFunctionCodec(noiseResolver, this::loadDensityFunction);

        DensityFunction shiftX = codec.load("minecraft:shift_x");
        DensityFunction shiftZ = codec.load("minecraft:shift_z");
        this.sampler = new Climate.Sampler(
                shiftedNoise(noises.get("temperature"), shiftX, shiftZ),
                shiftedNoise(noises.get("vegetation"), shiftX, shiftZ),
                codec.load("minecraft:overworld/continents"),
                codec.load("minecraft:overworld/erosion"),
                codec.load("minecraft:overworld/depth"),
                codec.load("minecraft:overworld/ridges"));
        this.biomes = BiomeParameterList.load();
        this.trialChambersBiomes = TrialChambersBiomeTag.load();
    }

    private static DensityFunction shiftedNoise(
            NormalNoise noise, DensityFunction shiftX, DensityFunction shiftZ) {
        return DensityFunctions.shiftedNoise(
                new NoiseHolder(noise), shiftX, DensityFunctions.constant(0.0D),
                shiftZ, 0.25D, 0.0D);
    }

    /** 采样方块位置对应的生物群系 id（自动按四分之一块对齐）。 */
    public String sampleBiome(int blockX, int blockY, int blockZ) {
        return this.biomes.findValue(this.sampler.sample(blockX, blockY, blockZ));
    }

    public boolean isTrialChamberBiome(String biome) {
        return this.trialChambersBiomes.contains(biome);
    }

    public Climate.Sampler sampler() {
        return this.sampler;
    }

    public Climate.ParameterList<String> biomes() {
        return this.biomes;
    }

    private static NoiseParameters loadNoiseParameters(String key) {
        Json.Object json = loadJson("data/minecraft/worldgen/noise/" + key + ".json").asObject();
        Json.Array amplitudes = json.get("amplitudes").asArray();
        double[] values = new double[amplitudes.size()];
        for (int index = 0; index < values.length; index++) {
            values[index] = ((Json.Num) amplitudes.get(index)).doubleValue();
        }
        return new NoiseParameters(json.getInt("firstOctave"), values);
    }

    private Json.JsonValue loadDensityFunction(String key) {
        String path = key.substring("minecraft:".length());
        return loadJson("data/minecraft/worldgen/density_function/" + path + ".json");
    }

    private static Json.JsonValue loadJson(String resource) {
        try (InputStream stream = ClasspathResourceLoader.open(resource)) {
            if (stream == null) {
                throw new IllegalStateException("缺失资源: " + resource);
            }
            return Json.parse(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("读取资源失败: " + resource, e);
        }
    }
}
