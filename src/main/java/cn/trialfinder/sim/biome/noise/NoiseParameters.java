package cn.trialfinder.sim.biome.noise;

import java.util.List;

/**
 * Port of net.minecraft.world.level.levelgen.synth.NormalNoise$NoiseParameters (1.21.11).
 */
public record NoiseParameters(int firstOctave, List<Double> amplitudes) {

    public NoiseParameters {
        amplitudes = List.copyOf(amplitudes);
    }

    public NoiseParameters(int firstOctave, double... amplitudes) {
        this(firstOctave, doubleList(amplitudes));
    }

    private static List<Double> doubleList(double[] values) {
        var list = new java.util.ArrayList<Double>(values.length);
        for (double v : values) {
            list.add(v);
        }
        return list;
    }
}
