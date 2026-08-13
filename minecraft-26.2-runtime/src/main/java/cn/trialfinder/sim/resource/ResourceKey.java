package cn.trialfinder.sim.resource;

/**
 * 复刻 {@code net.minecraft.resources.ResourceKey}（26.2 语义）—— 注册表键。
 * 简化实现：只承载 {@link Identifier}，不做注册表校验。
 */
public record ResourceKey<T>(Identifier location) {

    public static <T> ResourceKey<T> create(Identifier location) {
        return new ResourceKey<>(location);
    }

    public static <T> ResourceKey<T> create(String pathWithDefaultNamespace) {
        return new ResourceKey<>(Identifier.withDefaultNamespace(pathWithDefaultNamespace));
    }

    public Identifier location() {
        return this.location;
    }

    @Override
    public String toString() {
        return this.location.toString();
    }
}
