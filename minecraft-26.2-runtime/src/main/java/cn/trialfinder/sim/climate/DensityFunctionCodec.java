package cn.trialfinder.sim.climate;

import cn.trialfinder.sim.json.Json;
import cn.trialfinder.sim.noise.NormalNoise;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 极简密度函数 JSON 解码器（26.2 语义），只覆盖 {@code overworld} 气候采样用到的类型。
 * <ul>
 *   <li>数字 → {@link DensityFunctions#constant}；字符串参数 → 引用其它 DF 键。</li>
 *   <li>{@code flat_cache}/{@code cache_2d}/{@code cache_once} 在单点采样下透明，直接解开 argument。</li>
 *   <li>{@code shift_a}/{@code shift_b} 的 {@code argument} 是噪声键字符串（{@link NoiseHolder}）。</li>
 *   <li>{@code shifted_noise} 的 {@code noise} 是噪声键，{@code shift_x/shift_y/shift_z} 是 DF。</li>
 * </ul>
 */
public final class DensityFunctionCodec {
    private final Function<String, NormalNoise> noiseResolver;
    private final Function<String, Json.JsonValue> jsonLoader;
    private final Map<String, DensityFunction> cache = new HashMap<>();

    public DensityFunctionCodec(
            Function<String, NormalNoise> noiseResolver,
            Function<String, Json.JsonValue> jsonLoader) {
        this.noiseResolver = noiseResolver;
        this.jsonLoader = jsonLoader;
    }

    public DensityFunction load(String key) {
        DensityFunction cached = this.cache.get(key);
        if (cached != null) {
            return cached;
        }
        // 解码过程中会递归 load 其它键（spline coordinate / 字符串参数引用），
        // 所以不能用 computeIfAbsent（对同一 HashMap 重入会抛 ConcurrentModificationException）。
        // 主世界 DF 是 DAG（无环），先解码后放入缓存即可。
        DensityFunction decoded = decode(this.jsonLoader.apply(key));
        this.cache.put(key, decoded);
        return decoded;
    }

    private DensityFunction decode(Json.JsonValue value) {
        if (value instanceof Json.Num num) {
            return DensityFunctions.constant(num.doubleValue());
        }
        Json.Object object = value.asObject();
        String type = object.getString("type");
        return switch (type) {
            case "minecraft:add" -> DensityFunctions.add(
                    decodeArgument(object, "argument1"), decodeArgument(object, "argument2"));
            case "minecraft:mul" -> DensityFunctions.mul(
                    decodeArgument(object, "argument1"), decodeArgument(object, "argument2"));
            case "minecraft:abs" -> DensityFunctions.abs(decodeArgument(object, "argument"));
            case "minecraft:flat_cache", "minecraft:cache_2d", "minecraft:cache_once" ->
                    decodeArgument(object, "argument");
            case "minecraft:shifted_noise" -> decodeShiftedNoise(object);
            case "minecraft:spline" -> DensityFunctions.spline(
                    decodeSpline(object.get("spline").asObject()));
            case "minecraft:y_clamped_gradient" -> DensityFunctions.yClampedGradient(
                    number(object, "from_y"), number(object, "to_y"),
                    number(object, "from_value"), number(object, "to_value"));
            case "minecraft:shift_a" -> DensityFunctions.shiftA(
                    noiseHolder(object.getString("argument")));
            case "minecraft:shift_b" -> DensityFunctions.shiftB(
                    noiseHolder(object.getString("argument")));
            case "minecraft:blend_alpha" -> DensityFunctions.blendAlpha();
            case "minecraft:blend_offset" -> DensityFunctions.blendOffset();
            case "minecraft:constant" -> DensityFunctions.constant(number(object, "value"));
            default -> throw new IllegalStateException("未知密度函数类型: " + type);
        };
    }

    private DensityFunction decodeArgument(Json.Object object, String key) {
        Json.JsonValue value = object.get(key);
        if (value instanceof Json.Str string) {
            return this.load(string.value());
        }
        if (value instanceof Json.Num num) {
            return DensityFunctions.constant(num.doubleValue());
        }
        return decode(value);
    }

    private DensityFunction decodeShiftedNoise(Json.Object object) {
        NoiseHolder noise = noiseHolder(object.getString("noise"));
        DensityFunction shiftX = decodeArgument(object, "shift_x");
        DensityFunction shiftY = decodeArgument(object, "shift_y");
        DensityFunction shiftZ = decodeArgument(object, "shift_z");
        double xzScale = number(object, "xz_scale");
        double yScale = number(object, "y_scale");
        return DensityFunctions.shiftedNoise(noise, shiftX, shiftY, shiftZ, xzScale, yScale);
    }

    private NoiseHolder noiseHolder(String noiseKey) {
        return new NoiseHolder(this.noiseResolver.apply(noiseKey));
    }

    private CubicSpline decodeSpline(Json.Object object) {
        DensityFunction coordinate = this.load(object.getString("coordinate"));
        Json.Array points = object.get("points").asArray();
        int size = points.size();
        float[] locations = new float[size];
        float[] derivatives = new float[size];
        List<CubicSpline> values = new java.util.ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            Json.Object point = points.get(index).asObject();
            locations[index] = (float) number(point, "location");
            derivatives[index] = (float) number(point, "derivative");
            Json.JsonValue value = point.get("value");
            if (value instanceof Json.Num num) {
                values.add(CubicSpline.constant((float) num.doubleValue()));
            } else {
                values.add(decodeSpline(value.asObject()));
            }
        }
        return CubicSpline.multipoint(coordinate, locations, derivatives, values);
    }

    private static double number(Json.Object object, String key) {
        return ((Json.Num) object.get(key)).doubleValue();
    }
}
