package cn.trialfinder.sim.climate;

import cn.trialfinder.sim.math.Mth;

import java.util.List;

/**
 * 复刻 {@code net.minecraft.util.CubicSpline}（26.2 语义）。Multipoint 采样与
 * 区间二分、线性外推均按反编译字节码逐一对齐：
 * <ul>
 *   <li>{@code a = d0*(x1-x0) - (s1-s0)}、{@code b = -d1*(x1-x0) + (s1-s0)}；
 *       {@code result = lerp(t2,s0,s1) + t2*(1-t2)*lerp(t2,a,b)}。</li>
 *   <li>{@code findIntervalStart} 用 <tt>binarySearch(0, len, t &lt; locations[idx]) - 1</tt>。</li>
 *   <li>越界时 {@code linearExtend}：{@code derivative==0 ? value : value + derivative*(t-loc)}。</li>
 * </ul>
 */
public abstract class CubicSpline {

    protected abstract float compute(DensityFunction.FunctionContext context);

    public static CubicSpline constant(float value) {
        return new Constant(value);
    }

    public static CubicSpline multipoint(
            DensityFunction coordinate, float[] locations, float[] derivatives,
            List<CubicSpline> values) {
        return new Multipoint(coordinate, locations, values, derivatives);
    }

    private static float sample(
            CubicSpline spline, DensityFunction.FunctionContext context) {
        return spline.compute(context);
    }

    private static float sample(
            DensityFunction coordinate, float[] locations, float[] derivatives,
            List<CubicSpline> values, DensityFunction.FunctionContext context) {
        float t = (float) coordinate.compute(context);
        int i = findIntervalStart(locations, t);
        int j = locations.length - 1;
        if (i < 0) {
            return linearExtend(t, locations, sample(values.get(0), context), derivatives, 0);
        } else if (i == j) {
            return linearExtend(t, locations, sample(values.get(j), context), derivatives, j);
        } else {
            float x0 = locations[i];
            float x1 = locations[i + 1];
            float t2 = (t - x0) / (x1 - x0);
            float s0 = sample(values.get(i), context);
            float s1 = sample(values.get(i + 1), context);
            float d0 = derivatives[i];
            float d1 = derivatives[i + 1];
            float a = d0 * (x1 - x0) - (s1 - s0);
            float b = -d1 * (x1 - x0) + (s1 - s0);
            return Mth.lerp(t2, s0, s1) + t2 * (1.0f - t2) * Mth.lerp(t2, a, b);
        }
    }

    private static int findIntervalStart(float[] locations, float t) {
        int i = 0;
        int j = locations.length;
        while (i < j) {
            int k = (i + j) >> 1;
            if (t < locations[k]) {
                j = k;
            } else {
                i = k + 1;
            }
        }
        return i - 1;
    }

    private static float linearExtend(
            float t, float[] locations, float value, float[] derivatives, int i) {
        return derivatives[i] == 0.0f ? value : value + derivatives[i] * (t - locations[i]);
    }

    static final class Constant extends CubicSpline {
        private final float value;

        Constant(float value) {
            this.value = value;
        }

        @Override
        protected float compute(DensityFunction.FunctionContext context) {
            return this.value;
        }
    }

    static final class Multipoint extends CubicSpline {
        private final DensityFunction coordinate;
        private final float[] locations;
        private final List<CubicSpline> values;
        private final float[] derivatives;

        Multipoint(
                DensityFunction coordinate, float[] locations,
                List<CubicSpline> values, float[] derivatives) {
            this.coordinate = coordinate;
            this.locations = locations;
            this.values = values;
            this.derivatives = derivatives;
        }

        @Override
        protected float compute(DensityFunction.FunctionContext context) {
            return sample(this.coordinate, this.locations, this.derivatives, this.values, context);
        }
    }
}
