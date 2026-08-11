package cn.trialfinder.sim.structure.placement;

import cn.trialfinder.sim.random.RandomSource;
import cn.trialfinder.sim.util.StringRepresentable;

/**
 * Port of net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType (1.21.11).
 * trial_chambers uses LINEAR.
 */
public enum RandomSpreadType implements StringRepresentable {
    LINEAR("linear"),
    TRIANGULAR("triangular");

    private final String id;

    RandomSpreadType(String id) {
        this.id = id;
    }

    @Override
    public String getSerializedName() {
        return this.id;
    }

    public int evaluate(RandomSource random, int bound) {
        return switch (this) {
            case LINEAR -> random.nextInt(bound);
            case TRIANGULAR -> (random.nextInt(bound) + random.nextInt(bound)) / 2;
        };
    }
}
