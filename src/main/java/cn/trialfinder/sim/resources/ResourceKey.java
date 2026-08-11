package cn.trialfinder.sim.resources;

import java.util.Objects;

/**
 * Port of net.minecraft.resources.ResourceKey (1.21.11) — minimal. Identifies a registry entry
 * by its location (e.g. minecraft:trial_chambers/spawner/ranged/skeleton).
 */
public final class ResourceKey<T> {
    private final ResourceKey<?> parent;
    private final Identifier location;

    private ResourceKey(ResourceKey<?> parent, Identifier location) {
        this.parent = parent;
        this.location = location;
    }

    public static <T> ResourceKey<T> create(Identifier location) {
        return new ResourceKey<>(null, location);
    }

    public static <T> ResourceKey<T> create(String namespace, String path) {
        return create(Identifier.of(namespace, path));
    }

    public static <T> ResourceKey<T> create(String pathWithDefaultNamespace) {
        return create(Identifier.parse(pathWithDefaultNamespace));
    }

    public Identifier identifier() {
        return this.location;
    }

    public ResourceKey<?> parent() {
        return this.parent;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ResourceKey<?> resourceKey)) {
            return false;
        }
        return Objects.equals(this.parent, resourceKey.parent) && Objects.equals(this.location, resourceKey.location);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.parent, this.location);
    }

    @Override
    public String toString() {
        return this.location.toString();
    }
}
