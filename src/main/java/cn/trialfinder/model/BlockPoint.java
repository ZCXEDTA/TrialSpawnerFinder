package cn.trialfinder.model;

public record BlockPoint(int x, int z) implements Comparable<BlockPoint> {
    public long distanceSquared(BlockPoint other) {
        long dx = (long) x - other.x;
        long dz = (long) z - other.z;
        return dx * dx + dz * dz;
    }

    @Override
    public int compareTo(BlockPoint other) {
        int byX = Integer.compare(x, other.x);
        return byX != 0 ? byX : Integer.compare(z, other.z);
    }
}
