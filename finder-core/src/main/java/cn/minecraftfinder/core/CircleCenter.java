package cn.minecraftfinder.core;

public record CircleCenter(double x, double z) {
    public long roundedX() {
        return Math.round(x);
    }

    public long roundedZ() {
        return Math.round(z);
    }
}
