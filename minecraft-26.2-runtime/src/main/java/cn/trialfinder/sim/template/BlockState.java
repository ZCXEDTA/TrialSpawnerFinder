package cn.trialfinder.sim.template;

import cn.trialfinder.sim.math.FrontAndTop;
import cn.trialfinder.sim.math.Rotation;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 简化方块状态：只有方块 id 字符串，以及（对于 jigsaw 方块）朝向属性值（如 "north_up"）。
 * 关注的方块（jigsaw、trial_spawner）的位置/变换不依赖其它属性。
 */
public final class BlockState {
    private final String name;
    private final String orientation;
    private final FrontAndTop frontAndTop;
    private static final ConcurrentHashMap<String, BlockState> ROTATION_CACHE = new ConcurrentHashMap<>();

    public BlockState(String name, String orientation) {
        this.name = name;
        this.orientation = orientation;
        this.frontAndTop = orientation != null ? FrontAndTop.parse(orientation) : null;
    }

    public static BlockState of(String name) {
        return new BlockState(name, null);
    }

    public String name() {
        return this.name;
    }

    public String orientation() {
        return this.orientation;
    }

    public boolean isJigsaw() {
        return this.name.equals("minecraft:jigsaw");
    }

    public boolean isTrialSpawner() {
        return this.name.equals("minecraft:trial_spawner");
    }

    /** 已解析的朝向（非 jigsaw 方块为 null）。O(1) 字段读取。 */
    public FrontAndTop frontAndTop() {
        return this.frontAndTop;
    }

    public BlockState rotate(Rotation rotation) {
        if (this.orientation == null || rotation == Rotation.NONE) {
            return this;
        }
        String cacheKey = this.orientation + '|' + rotation.getIndex();
        return ROTATION_CACHE.computeIfAbsent(cacheKey,
                ignored -> new BlockState(this.name, this.frontAndTop.rotate(rotation).getSerializedName()));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof BlockState that)) {
            return false;
        }
        return this.name.equals(that.name) && Objects.equals(this.orientation, that.orientation);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.name, this.orientation);
    }

    @Override
    public String toString() {
        return this.orientation == null ? this.name : this.name + "[" + this.orientation + "]";
    }
}
