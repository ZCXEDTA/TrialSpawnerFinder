package cn.trialfinder.model;

public record SpawnerPoint(int x, int y, int z) implements Comparable<SpawnerPoint> {
    public long horizontalDistanceSquared(long centerX, long centerZ) {
        long dx = (long) x - centerX;
        long dz = (long) z - centerZ;
        return dx * dx + dz * dz;
    }

    @Override
    public int compareTo(SpawnerPoint other) {
        int byX = Integer.compare(x, other.x);
        if (byX != 0) return byX;
        int byY = Integer.compare(y, other.y);
        return byY != 0 ? byY : Integer.compare(z, other.z);
    }
}
