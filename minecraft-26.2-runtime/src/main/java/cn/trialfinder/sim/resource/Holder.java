package cn.trialfinder.sim.resource;

import java.util.Optional;

/**
 * 复刻 {@code net.minecraft.core.Holder}（26.2 语义）—— 简化实现。
 * 引用态只带键；直连态带值（可能同时带键）。
 */
public record Holder<T>(Optional<ResourceKey<T>> key, T value) {

    public static <T> Holder<T> direct(T value) {
        return new Holder<>(Optional.empty(), value);
    }

    public static <T> Holder<T> direct(ResourceKey<T> key, T value) {
        return new Holder<>(Optional.of(key), value);
    }

    public static <T> Holder<T> reference(ResourceKey<T> key) {
        return new Holder<>(Optional.of(key), null);
    }

    public Optional<ResourceKey<T>> unwrapKey() {
        return this.key;
    }

    public boolean isReference() {
        return this.value == null;
    }

    public T value() {
        if (this.value == null) {
            throw new IllegalStateException("Holder 处于引用态，没有值: " + this.key);
        }
        return this.value;
    }

    /** 把引用态解析为直连态。 */
    public Holder<T> resolve(T resolvedValue) {
        return new Holder<>(this.key, resolvedValue);
    }
}
