package cn.trialfinder.sim.resource;

/**
 * 复刻 {@code net.minecraft.resources.Identifier}（26.2 语义）—— 命名空间:路径 资源标识。
 */
public record Identifier(String namespace, String path) implements Comparable<Identifier> {

    public Identifier {
        if (namespace == null || namespace.isEmpty() || path == null || path.isEmpty()) {
            throw new IllegalArgumentException("无效资源标识: " + namespace + ":" + path);
        }
    }

    public static Identifier of(String namespace, String path) {
        return new Identifier(namespace, path);
    }

    public static Identifier fromString(String value) {
        int colon = value.indexOf(':');
        if (colon < 0) {
            return new Identifier("minecraft", value);
        }
        return new Identifier(value.substring(0, colon), value.substring(colon + 1));
    }

    /** 以默认命名空间 minecraft 解析 {@code namespace:path} 或 {@code path}。 */
    public static Identifier withDefaultNamespace(String value) {
        int colon = value.indexOf(':');
        return colon < 0
                ? new Identifier("minecraft", value)
                : new Identifier(value.substring(0, colon), value.substring(colon + 1));
    }

    public String getPath() {
        return this.path;
    }

    @Override
    public int compareTo(Identifier other) {
        int byNamespace = this.namespace.compareTo(other.namespace);
        return byNamespace != 0 ? byNamespace : this.path.compareTo(other.path);
    }

    @Override
    public String toString() {
        return this.namespace + ":" + this.path;
    }
}
