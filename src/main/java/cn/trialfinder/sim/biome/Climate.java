package cn.trialfinder.sim.biome;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Port of net.minecraft.world.level.biome.Climate (1.21.11) — the parameter-point distance metric
 * and nearest-point search. The vanilla RTree is an optimization; a linear scan over the parameter
 * list is semantically identical (same distance metric, same tie-break order).
 */
public final class Climate {
    private Climate() {
    }

    /** A single dimension's inclusive range [-max, max]. */
    public record Parameter(float min, float max) {
        public static Parameter point(float value) {
            return new Parameter(value, value);
        }

        public static Parameter range(float min, float max) {
            return new Parameter(min, max);
        }

        public float distance(float value) {
            if (value < this.min) {
                return this.min - value;
            }
            return value > this.max ? value - this.max : 0.0F;
        }
    }

    /** The 7-parameter point a biome maps to (temperature, humidity, continentalness, erosion, depth, weirdness, offset). */
    public record ParameterPoint(Parameter temperature, Parameter humidity, Parameter continentalness,
                                 Parameter erosion, Parameter depth, Parameter weirdness, Parameter offset) {
        public static ParameterPoint parameters(float tempMin, float tempMax,
                                                float humMin, float humMax,
                                                float contMin, float contMax,
                                                float eroMin, float eroMax,
                                                float depthMin, float depthMax,
                                                float weirdMin, float weirdMax,
                                                float offset) {
            return new ParameterPoint(
                    Parameter.range(tempMin, tempMax),
                    Parameter.range(humMin, humMax),
                    Parameter.range(contMin, contMax),
                    Parameter.range(eroMin, eroMax),
                    Parameter.range(depthMin, depthMax),
                    Parameter.range(weirdMin, weirdMax),
                    Parameter.point(offset));
        }
    }

    /** A concrete climate sample (the 6 noise dims plus offset=0). */
    public record TargetPoint(float temperature, float humidity, float continentalness,
                              float erosion, float depth, float weirdness, float offset) {
        public static TargetPoint of(float temperature, float humidity, float continentalness,
                                     float erosion, float depth, float weirdness) {
            return new TargetPoint(temperature, humidity, continentalness, erosion, depth, weirdness, 0.0F);
        }
    }

    /** The vanilla distance metric over the 7 parameters (weights match the game). */
    public static float distance(TargetPoint target, ParameterPoint point) {
        float d = 0.0F;
        d += weightedDistance(target.temperature(), point.temperature(), 1.0F);
        d += weightedDistance(target.humidity(), point.humidity(), 1.0F);
        d += weightedDistance(target.continentalness(), point.continentalness(), 1.0F);
        d += weightedDistance(target.erosion(), point.erosion(), 1.0F);
        d += weightedDistance(target.depth(), point.depth(), 1.0F);
        d += weightedDistance(target.weirdness(), point.weirdness(), 1.0F);
        d += weightedDistance(target.offset(), point.offset(), 0.0F);
        return d;
    }

    private static float weightedDistance(float value, Parameter parameter, float weight) {
        float d = parameter.distance(value);
        return d * d * weight;
    }

    /** A parameter list mapping points to objects (biomes); nearest-point lookup. */
    public static final class ParameterList<T> {
        private final List<Entry<T>> entries;

        public ParameterList(List<Entry<T>> entries) {
            this.entries = List.copyOf(entries);
        }

        public List<Entry<T>> entries() {
            return this.entries;
        }

        /** Finds the entry whose ParameterPoint is nearest to {@code target}. */
        public Optional<T> findNearest(TargetPoint target) {
            Entry<T> best = null;
            float bestDist = Float.MAX_VALUE;
            for (Entry<T> entry : this.entries) {
                float d = Climate.distance(target, entry.point());
                if (best == null || d < bestDist) {
                    best = entry;
                    bestDist = d;
                }
            }
            return best == null ? Optional.empty() : Optional.of(best.value());
        }
    }

    public record Entry<T>(ParameterPoint point, T value) {
    }

    /** Fluent builder matching the vanilla Climate.parameters(...) usage. */
    public static final class ParameterPointBuilder {
        private final List<Parameter> params = new ArrayList<>();

        public static ParameterPointBuilder builder() {
            return new ParameterPointBuilder();
        }

        public ParameterPointBuilder add(Parameter p) {
            this.params.add(p);
            return this;
        }

        public ParameterPoint build() {
            if (this.params.size() != 7) {
                throw new IllegalArgumentException("need 7 parameters, got " + this.params.size());
            }
            return new ParameterPoint(this.params.get(0), this.params.get(1), this.params.get(2),
                    this.params.get(3), this.params.get(4), this.params.get(5), this.params.get(6));
        }
    }
}
