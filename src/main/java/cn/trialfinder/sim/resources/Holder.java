package cn.trialfinder.sim.resources;

/**
 * Port of net.minecraft.core.Holder (1.21.11) — minimal. A direct holder carries a value;
 * a reference holder carries a key that a registry resolves.
 */
public final class Holder<T> {
    private final ResourceKey<T> key;
    private final T value;

    private Holder(ResourceKey<T> key, T value) {
        this.key = key;
        this.value = value;
    }

    public static <T> Holder<T> direct(T value) {
        return new Holder<>(null, value);
    }

    public static <T> Holder<T> reference(ResourceKey<T> key) {
        return new Holder<>(key, null);
    }

    public T value() {
        return this.value;
    }

    public boolean isReference() {
        return this.value == null;
    }

    public ResourceKey<T> unwrapKey() {
        return this.key;
    }

    public boolean is(ResourceKey<T> key) {
        return this.key != null && this.key.equals(key);
    }
}
