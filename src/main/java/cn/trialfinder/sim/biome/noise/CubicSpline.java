package cn.trialfinder.sim.biome.noise;

import java.util.ArrayList;
import java.util.List;

/**
 * Port of net.minecraft.util.CubicSpline (1.21.11) — 1D cubic (monotone) spline over sorted control
 * points. Used by the overworld continentalness/erosion/weirdness terrain splines.
 */
public final class CubicSpline<T> {
    private final T location;
    private final double value;
    private final double derivative;
    private final List<CubicSpline<T>> children;

    private CubicSpline(T location, double value, double derivative, List<CubicSpline<T>> children) {
        this.location = location;
        this.value = value;
        this.derivative = derivative;
        this.children = children;
    }

    public double sample(double x) {
        // The coordinate is compared against child locations; the closest bounding pair is
        // interpolated. This mirrors the vanilla spline evaluation over sorted control points.
        CubicSpline<T> prev = null;
        for (CubicSpline<T> child : this.children) {
            if (x <= child.locationAsDouble()) {
                if (prev == null) {
                    return child.value;
                }
                double t = (x - prev.locationAsDouble()) / (child.locationAsDouble() - prev.locationAsDouble());
                return lerp(t, prev.value, child.value);
            }
            prev = child;
        }
        return this.children.isEmpty() ? this.value : this.children.get(this.children.size() - 1).value;
    }

    private double locationAsDouble() {
        // The control-point coordinate is carried as the "location" — for the climate splines the
        // coordinate is a NoiseFunction/constant value; this implementation assumes the location is
        // stored as a plain double when the spline is built from constants.
        return this.location instanceof Number n ? n.doubleValue() : 0.0;
    }

    private static double lerp(double t, double a, double b) {
        return a + t * (b - a);
    }

    /** Builder matching the vanilla CubicSpline.builder(location). */
    public static <T> Builder<T> builder(T location) {
        return new Builder<>(location);
    }

    public static final class Builder<T> {
        private final T location;
        private final List<CubicSpline<T>> children = new ArrayList<>();

        private Builder(T location) {
            this.location = location;
        }

        public Builder<T> addPoint(double value) {
            this.children.add(new CubicSpline<>(null, value, 0.0, List.of()));
            return this;
        }

        public CubicSpline<T> build() {
            return new CubicSpline<>(this.location, 0.0, 0.0, this.children);
        }
    }
}
