package cn.trialfinder.sim.template;

import cn.trialfinder.sim.resource.Identifier;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 结构模板管理器：从 classpath 资源 {@code data/minecraft/structure/<path>.nbt} 加载并缓存模板。
 * 开发时读取 {@code build/resources/main/data}，fat jar 里从 jar classpath 读取。
 */
public final class StructureTemplateManager {
    private final ConcurrentMap<Identifier, StructureTemplate> cache = new ConcurrentHashMap<>();

    public StructureTemplate getOrCreate(Identifier id) {
        return this.cache.computeIfAbsent(id, this::load);
    }

    private StructureTemplate load(Identifier id) {
        String path = "data/" + id.namespace() + "/structure/" + id.path() + ".nbt";
        try (InputStream stream = resourceStream(path)) {
            if (stream == null) {
                throw new IllegalStateException("缺少结构模板资源: " + path);
            }
            return StructureTemplate.loadFrom(stream);
        } catch (IOException e) {
            throw new IllegalStateException("加载结构模板失败: " + path, e);
        }
    }

    private InputStream resourceStream(String path) {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (loader == null) {
            loader = StructureTemplateManager.class.getClassLoader();
        }
        return loader.getResourceAsStream(path);
    }
}
