package cn.trialfinder.sim.noise;

import cn.trialfinder.sim.math.Mth;
import cn.trialfinder.sim.random.RandomSource;

/**
 * 复刻 {@code net.minecraft.world.level.levelgen.synth.ImprovedNoise}（26.2 语义）。
 * 构造与采样路径均按反编译字节码逐一对齐：
 * <ul>
 *   <li>构造：{@code xo/yo/zo = random.nextDouble()*256.0}，p 表 Fisher-Yates 洗牌
 *       {@code j = random.nextInt(256 - i)}，交换 p[i] 与 p[i+j]。</li>
 *   <li>{@code noise(x,y,z,yScale,yMax)}：m 仅在 yScale≠0 时按
 *       {@code floor(min(yMax,yf)/yScale + 1E-7)*yScale} 拉伸；气候噪声 yScale=0 → m=0。</li>
 *   <li>{@code sampleAndLerp(x,y,z,d,e,f,g)}：d,e,f 同时用于 8 个 gradDot 与三个 smoothstep
 *       权重（{@code lerp3(smoothstep(d), smoothstep(e), smoothstep(f), d0..d7)}）；g 不使用。</li>
 * </ul>
 */
public final class ImprovedNoise {
    private final byte[] p;
    public final double xo;
    public final double yo;
    public final double zo;

    public ImprovedNoise(RandomSource random) {
        this.xo = random.nextDouble() * 256.0D;
        this.yo = random.nextDouble() * 256.0D;
        this.zo = random.nextDouble() * 256.0D;
        this.p = new byte[256];
        for (int i = 0; i < 256; i++) {
            this.p[i] = (byte) i;
        }
        for (int i = 0; i < 256; i++) {
            int j = random.nextInt(256 - i);
            byte b = this.p[i];
            this.p[i] = this.p[i + j];
            this.p[i + j] = b;
        }
    }

    public double noise(double x, double y, double z) {
        return this.noise(x, y, z, 0.0D, 0.0D);
    }

    public double noise(double x, double y, double z, double yScale, double yMax) {
        double d = x + this.xo;
        double e = y + this.yo;
        double f = z + this.zo;
        int i = Mth.floor(d);
        int j = Mth.floor(e);
        int k = Mth.floor(f);
        double xf = d - (double) i;
        double yf = e - (double) j;
        double zf = f - (double) k;
        double m;
        if (yScale == 0.0D) {
            m = 0.0D;
        } else {
            double clamped = yMax >= 0.0D && yMax < yf ? yMax : yf;
            m = (double) Mth.floor(clamped / yScale + 1.0E-7D) * yScale;
        }
        return this.sampleAndLerp(i, j, k, xf, yf - m, zf, yf);
    }

    private static double gradDot(int hash, double x, double y, double z) {
        return SimplexNoise.dot(SimplexNoise.gradient(hash), x, y, z);
    }

    private int p(int index) {
        return (int) (this.p[index & 255] & 255);
    }

    private double sampleAndLerp(int x, int y, int z, double d, double e, double f, double g) {
        int i = this.p(x);
        int j = this.p(x + 1);
        int k = this.p(i + y);
        int l = this.p(i + y + 1);
        int m = this.p(j + y);
        int n = this.p(j + y + 1);
        double d0 = gradDot(this.p(k + z), d, e, f);
        double d1 = gradDot(this.p(m + z), d - 1.0D, e, f);
        double d2 = gradDot(this.p(l + z), d, e - 1.0D, f);
        double d3 = gradDot(this.p(n + z), d - 1.0D, e - 1.0D, f);
        double d4 = gradDot(this.p(k + z + 1), d, e, f - 1.0D);
        double d5 = gradDot(this.p(m + z + 1), d - 1.0D, e, f - 1.0D);
        double d6 = gradDot(this.p(l + z + 1), d, e - 1.0D, f - 1.0D);
        double d7 = gradDot(this.p(n + z + 1), d - 1.0D, e - 1.0D, f - 1.0D);
        return Mth.lerp3(Mth.smoothstep(d), Mth.smoothstep(e), Mth.smoothstep(f),
                d0, d1, d2, d3, d4, d5, d6, d7);
    }
}
