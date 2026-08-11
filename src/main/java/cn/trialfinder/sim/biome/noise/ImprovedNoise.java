package cn.trialfinder.sim.biome.noise;

import cn.trialfinder.sim.random.RandomSource;

/**
 * Port of net.minecraft.world.level.levelgen.synth.ImprovedNoise (1.21.11).
 * Ken Perlin 3D noise with the game's permutation seeding, xo/yo/zo offsets and gradient table.
 * Bit-exact with the vanilla implementation.
 */
public final class ImprovedNoise {
    private final byte[] p;
    public final double xo;
    public final double yo;
    public final double zo;

    public ImprovedNoise(RandomSource random) {
        this.xo = random.nextDouble() * 256.0;
        this.yo = random.nextDouble() * 256.0;
        this.zo = random.nextDouble() * 256.0;
        this.p = new byte[256];
        for (int i = 0; i < 256; i++) {
            this.p[i] = (byte) i;
        }
        for (int i = 0; i < 256; i++) {
            int swap = random.nextInt(256 - i);
            byte tmp = this.p[i];
            this.p[i] = this.p[i + swap];
            this.p[i + swap] = tmp;
        }
    }

    public double noise(double x, double y, double z) {
        return this.noise(x, y, z, 0.0, 0.0);
    }

    public double noise(double x, double y, double z, double yScale, double yMax) {
        x += this.xo;
        y += this.yo;
        z += this.zo;
        int xi = Mth.floor(x);
        int yi = Mth.floor(y);
        int zi = Mth.floor(z);
        double xf = x - xi;
        double yf = y - yi;
        double zf = z - zi;
        double yLerp;
        if (yScale != 0.0) {
            double clamped = yMax >= 0.0 && yMax < yf ? yMax : yf;
            yLerp = Mth.floor(clamped / yScale + 1.0E-7) * yScale;
        } else {
            yLerp = 0.0;
        }
        return this.sampleAndLerp(xi, yi, zi, xf, yf - yLerp, zf, yf);
    }

    private int p(int index) {
        return this.p[index & 255] & 255;
    }

    private static double gradDot(int hash, double x, double y, double z) {
        return SimplexNoise.dot(SimplexNoise.GRADIENT[hash & 15], x, y, z);
    }

    private double sampleAndLerp(int x, int y, int z, double xf, double yf, double zf, double yLerp) {
        int i = this.p(x);
        int j = this.p(x + 1);
        int k = this.p(i + y);
        int l = this.p(i + y + 1);
        int m = this.p(j + y);
        int n = this.p(j + y + 1);

        double v000 = gradDot(this.p(k + z), xf, yf, zf);
        double v100 = gradDot(this.p(m + z), xf - 1.0, yf, zf);
        double v010 = gradDot(this.p(l + z), xf, yf - 1.0, zf);
        double v110 = gradDot(this.p(n + z), xf - 1.0, yf - 1.0, zf);
        double v001 = gradDot(this.p(k + z + 1), xf, yf, zf - 1.0);
        double v101 = gradDot(this.p(m + z + 1), xf - 1.0, yf, zf - 1.0);
        double v011 = gradDot(this.p(l + z + 1), xf, yf - 1.0, zf - 1.0);
        double v111 = gradDot(this.p(n + z + 1), xf - 1.0, yf - 1.0, zf - 1.0);

        double sx = Mth.smoothstep(xf);
        double sy = Mth.smoothstep(yLerp);
        double sz = Mth.smoothstep(zf);
        return Mth.lerp3(sx, sy, sz, v000, v100, v010, v110, v001, v101, v011, v111);
    }
}
