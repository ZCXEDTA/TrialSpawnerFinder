package cn.trialfinder.sim.biome.noise;

import cn.trialfinder.sim.random.RandomSource;

/**
 * Port of net.minecraft.world.level.levelgen.synth.SimplexNoise (1.21.11).
 * Bit-exact: 16-entry gradient table, permutation seeded from {@link RandomSource},
 * 2D ({@link #getValue(double, double)}) and 3D ({@link #getValue(double, double, double)})
 * simplex noise.
 */
public class SimplexNoise {
    protected static final int[][] GRADIENT = {
            {1, 1, 0}, {-1, 1, 0}, {1, -1, 0}, {-1, -1, 0},
            {1, 0, 1}, {-1, 0, 1}, {1, 0, -1}, {-1, 0, -1},
            {0, 1, 1}, {0, -1, 1}, {0, 1, -1}, {0, -1, -1},
            {0, 1, 1}, {0, -1, 1}, {-1, 1, 0}, {0, -1, -1}
    };
    private static final double SQRT_3 = Math.sqrt(3.0);
    private static final double F2 = 0.5 * (SQRT_3 - 1.0);
    private static final double G2 = (3.0 - SQRT_3) / 6.0;
    private static final double F3 = 1.0 / 3.0;
    private static final double G3 = 1.0 / 6.0;

    private final int[] p;
    public final double xo;
    public final double yo;
    public final double zo;

    public SimplexNoise(RandomSource random) {
        this.p = new int[256];
        this.xo = random.nextDouble() * 256.0;
        this.yo = random.nextDouble() * 256.0;
        this.zo = random.nextDouble() * 256.0;
        for (int i = 0; i < 256; i++) {
            this.p[i] = i;
        }
        for (int i = 0; i < 256; i++) {
            int swap = random.nextInt(256 - i);
            int tmp = this.p[i];
            this.p[i] = this.p[i + swap];
            this.p[i + swap] = tmp;
        }
    }

    private int p(int i) {
        return this.p[i & 255];
    }

    protected static int floor(double value) {
        int i = (int) value;
        return value < i ? i - 1 : i;
    }

    protected static double dot(int[] gradient, double x, double y, double z) {
        return gradient[0] * x + gradient[1] * y + gradient[2] * z;
    }

    private double getCornerNoise3D(int hash, double x, double y, double z, double threshold) {
        double f = threshold - x * x - y * y - z * z;
        if (f < 0.0) {
            return 0.0;
        }
        f *= f;
        return f * f * dot(GRADIENT[hash], x, y, z);
    }

    /** 2D simplex noise (used by the game's 2D value query). */
    public double getValue(double x, double z) {
        double s = (x + z) * F2;
        int i = floor(x + s);
        int j = floor(z + s);
        double t = (i + j) * G2;
        double x0 = x - (i - t);
        double z0 = z - (j - t);

        int i1;
        int j1;
        if (x0 > z0) {
            i1 = 1;
            j1 = 0;
        } else {
            i1 = 0;
            j1 = 1;
        }
        double x1 = x0 - i1 + G2;
        double z1 = z0 - j1 + G2;
        double x2 = x0 - 1.0 + 2.0 * G2;
        double z2 = z0 - 1.0 + 2.0 * G2;

        int ii = i & 255;
        int jj = j & 255;

        int hash0 = this.p(ii + this.p(jj)) % 12;
        int hash1 = this.p(ii + i1 + this.p(jj + j1)) % 12;
        int hash2 = this.p(ii + 1 + this.p(jj + 1)) % 12;

        return 70.0 * (getCornerNoise3D(hash0, x0, z0, 0.0, 0.5)
                + getCornerNoise3D(hash1, x1, z1, 0.0, 0.5)
                + getCornerNoise3D(hash2, x2, z2, 0.0, 0.5));
    }

    /** 3D simplex noise. */
    public double getValue(double x, double y, double z) {
        double s = (x + y + z) * F3;
        int i = floor(x + s);
        int j = floor(y + s);
        int k = floor(z + s);
        double t = (i + j + k) * G3;
        double x0 = x - (i - t);
        double y0 = y - (j - t);
        double z0 = z - (k - t);

        int i1;
        int j1;
        int k1;
        int i2;
        int j2;
        int k2;
        if (x0 >= y0) {
            if (y0 >= z0) {
                i1 = 1; j1 = 0; k1 = 0; i2 = 1; j2 = 1; k2 = 0;
            } else if (x0 >= z0) {
                i1 = 1; j1 = 0; k1 = 0; i2 = 1; j2 = 0; k2 = 1;
            } else {
                i1 = 0; j1 = 0; k1 = 1; i2 = 1; j2 = 0; k2 = 1;
            }
        } else {
            if (y0 < z0) {
                i1 = 0; j1 = 0; k1 = 1; i2 = 0; j2 = 1; k2 = 1;
            } else if (x0 < z0) {
                i1 = 0; j1 = 1; k1 = 0; i2 = 0; j2 = 1; k2 = 1;
            } else {
                i1 = 0; j1 = 1; k1 = 0; i2 = 1; j2 = 1; k2 = 0;
            }
        }

        double x1 = x0 - i1 + G3;
        double y1 = y0 - j1 + G3;
        double z1 = z0 - k1 + G3;
        double x2 = x0 - i2 + 2.0 * G3;
        double y2 = y0 - j2 + 2.0 * G3;
        double z2 = z0 - k2 + 2.0 * G3;
        double x3 = x0 - 1.0 + 3.0 * G3;
        double y3 = y0 - 1.0 + 3.0 * G3;
        double z3 = z0 - 1.0 + 3.0 * G3;

        int ii = i & 255;
        int jj = j & 255;
        int kk = k & 255;

        int hash0 = this.p(ii + this.p(jj + this.p(kk))) % 12;
        int hash1 = this.p(ii + i1 + this.p(jj + j1 + this.p(kk + k1))) % 12;
        int hash2 = this.p(ii + i2 + this.p(jj + j2 + this.p(kk + k2))) % 12;
        int hash3 = this.p(ii + 1 + this.p(jj + 1 + this.p(kk + 1))) % 12;

        return 32.0 * (getCornerNoise3D(hash0, x0, y0, z0, 0.6)
                + getCornerNoise3D(hash1, x1, y1, z1, 0.6)
                + getCornerNoise3D(hash2, x2, y2, z2, 0.6)
                + getCornerNoise3D(hash3, x3, y3, z3, 0.6));
    }
}
