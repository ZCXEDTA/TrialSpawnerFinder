package cn.trialfinder.sim.resources;

import java.util.Objects;

/**
 * Port of net.minecraft.resources.Identifier (1.21.11; the class was renamed from ResourceLocation
 * in this version). Minimal: parsing, namespace/path access, string rendering.
 */
public record Identifier(String namespace, String path) {

    public static final String DEFAULT_NAMESPACE = "minecraft";

    public Identifier {
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(path, "path");
        if (namespace.isEmpty()) {
            throw new IllegalArgumentException("Namespace cannot be empty");
        }
        if (path.isEmpty()) {
            throw new IllegalArgumentException("Path cannot be empty");
        }
    }

    public static Identifier of(String namespace, String path) {
        return new Identifier(namespace, path);
    }

    public static Identifier parse(String string) {
        int i = string.indexOf(':');
        if (i < 0) {
            return new Identifier(DEFAULT_NAMESPACE, string);
        }
        return new Identifier(string.substring(0, i), string.substring(i + 1));
    }

    public static Identifier withDefaultNamespace(String path) {
        return new Identifier(DEFAULT_NAMESPACE, path);
    }

    public String getPath() {
        return this.path;
    }

    public String getNamespace() {
        return this.namespace;
    }

    public String toString() {
        return this.namespace + ":" + this.path;
    }
}
