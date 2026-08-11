package cn.trialfinder.sim.biome.noise;

/**
 * Minimal port of net.minecraft.world.level.levelgen.DensityFunction (1.21.11) — the abstraction
 * used by the climate router. A function maps a block (or quart) coordinate context to a double.
 *
 * <p>Only the subset used by the overworld climate dimensions is implemented:
 * {@link Noise}, {@link ShiftedNoise2d}, {@link Constant}, {@link Add}, {@link Mul},
 * {@link Min}, {@link Max}, {@link Clamp}, {@link Abs}, and {@link Spline}.
 */
@FunctionalInterface
public interface DensityFunction {

    double compute(double x, double y, double z);

    /** A 2D/3D normal noise with optional y-scale — the "temperature/humidity/etc." building block. */
    final class Noise implements DensityFunction {
        private final NormalNoise noise;
        private final double xzScale;
        private final double yScale;

        public Noise(NormalNoise noise, double xzScale, double yScale) {
            this.noise = noise;
            this.xzScale = xzScale;
            this.yScale = yScale;
        }

        @Override
        public double compute(double x, double y, double z) {
            return this.noise.getValue(x * this.xzScale, y * this.yScale, z * this.xzScale);
        }
    }

    /** Shifted 2D noise: samples at (x - shiftX(x,y,z), z - shiftZ(x,y,z)) with a y offset. */
    final class ShiftedNoise2d implements DensityFunction {
        private final DensityFunction shiftX;
        private final DensityFunction shiftZ;
        private final double xzScale;
        private final NormalNoise noise;

        public ShiftedNoise2d(DensityFunction shiftX, DensityFunction shiftZ, double xzScale, NormalNoise noise) {
            this.shiftX = shiftX;
            this.shiftZ = shiftZ;
            this.xzScale = xzScale;
            this.noise = noise;
        }

        @Override
        public double compute(double x, double y, double z) {
            double sx = this.shiftX.compute(x, y, z);
            double sz = this.shiftZ.compute(x, y, z);
            return this.noise.getValue((x + sx) * this.xzScale, 0.0, (z + sz) * this.xzScale);
        }
    }

    record Constant(double value) implements DensityFunction {
        @Override
        public double compute(double x, double y, double z) {
            return this.value;
        }
    }

    record Add(DensityFunction a, DensityFunction b) implements DensityFunction {
        @Override
        public double compute(double x, double y, double z) {
            return a.compute(x, y, z) + b.compute(x, y, z);
        }
    }

    record Mul(DensityFunction a, DensityFunction b) implements DensityFunction {
        @Override
        public double compute(double x, double y, double z) {
            return a.compute(x, y, z) * b.compute(x, y, z);
        }
    }

    record Min(DensityFunction a, DensityFunction b) implements DensityFunction {
        @Override
        public double compute(double x, double y, double z) {
            return Math.min(a.compute(x, y, z), b.compute(x, y, z));
        }
    }

    record Max(DensityFunction a, DensityFunction b) implements DensityFunction {
        @Override
        public double compute(double x, double y, double z) {
            return Math.max(a.compute(x, y, z), b.compute(x, y, z));
        }
    }

    record Clamp(DensityFunction input, double min, double max) implements DensityFunction {
        @Override
        public double compute(double x, double y, double z) {
            double v = input.compute(x, y, z);
            return v < this.min ? this.min : Math.min(v, this.max);
        }
    }

    record Abs(DensityFunction input) implements DensityFunction {
        @Override
        public double compute(double x, double y, double z) {
            return Math.abs(input.compute(x, y, z));
        }
    }

    /** A cubic spline over a single coordinate (e.g. continentalness). */
    record Spline(CubicSpline<DensityFunction> spline) implements DensityFunction {
        @Override
        public double compute(double x, double y, double z) {
            return this.spline.sample(x);
        }
    }

    static Noise noise(NormalNoise noise) {
        return new Noise(noise, 1.0, 1.0);
    }

    static Noise noise(NormalNoise noise, double xzScale) {
        return new Noise(noise, xzScale, 1.0);
    }

    static Noise noise(NormalNoise noise, double xzScale, double yScale) {
        return new Noise(noise, xzScale, yScale);
    }

    static ShiftedNoise2d shiftedNoise2d(DensityFunction shiftX, DensityFunction shiftZ, double xzScale, NormalNoise noise) {
        return new ShiftedNoise2d(shiftX, shiftZ, xzScale, noise);
    }

    static DensityFunction constant(double value) {
        return new Constant(value);
    }

    static DensityFunction add(DensityFunction a, DensityFunction b) {
        return new Add(a, b);
    }

    static DensityFunction mul(DensityFunction a, DensityFunction b) {
        return new Mul(a, b);
    }

    static DensityFunction min(DensityFunction a, DensityFunction b) {
        return new Min(a, b);
    }

    static DensityFunction max(DensityFunction a, DensityFunction b) {
        return new Max(a, b);
    }

    static DensityFunction clamp(DensityFunction input, double min, double max) {
        return new Clamp(input, min, max);
    }

    static DensityFunction abs(DensityFunction input) {
        return new Abs(input);
    }
}
