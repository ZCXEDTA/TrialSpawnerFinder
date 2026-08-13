package cn.trialfinder.sim.random;

/**
 * 复刻 {@code net.minecraft.util.BitRandomSource}（26.2 语义）——
 * 暴露 {@link #next(int)} 的随机源。
 */
public interface BitRandomSource extends RandomSource {

    int next(int bits);
}
