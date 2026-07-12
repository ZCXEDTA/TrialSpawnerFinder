package cn.trialfinder.config;

import java.util.Locale;

public enum AreaShape {
    CIRCLE,
    SQUARE;

    public static AreaShape parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("缺少配置项: area-shape");
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("area-shape 只能是 circle 或 square: " + value, e);
        }
    }

    public boolean contains(long centerX, long centerZ, long x, long z, long radius) {
        long dx = x - centerX;
        long dz = z - centerZ;
        return switch (this) {
            case CIRCLE -> dx * dx + dz * dz <= radius * radius;
            case SQUARE -> Math.abs(dx) <= radius && Math.abs(dz) <= radius;
        };
    }
}
