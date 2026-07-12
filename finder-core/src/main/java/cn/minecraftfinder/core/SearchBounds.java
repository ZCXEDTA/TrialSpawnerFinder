package cn.minecraftfinder.core;

public record SearchBounds(long minX, long maxX, long minZ, long maxZ) {
    public SearchBounds {
        if (minX > maxX || minZ > maxZ) {
            throw new IllegalArgumentException("搜索边界的最小值不能大于最大值");
        }
    }

    public static SearchBounds fullWorld(int worldLimit) {
        return new SearchBounds(-worldLimit, worldLimit, -worldLimit, worldLimit);
    }

    public static SearchBounds around(
            int centerX, int centerZ, int radius, int worldLimit) {
        return new SearchBounds(
                Math.max(-worldLimit, (long) centerX - radius),
                Math.min(worldLimit, (long) centerX + radius),
                Math.max(-worldLimit, (long) centerZ - radius),
                Math.min(worldLimit, (long) centerZ + radius));
    }

    public boolean contains(long x, long z) {
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }
}
