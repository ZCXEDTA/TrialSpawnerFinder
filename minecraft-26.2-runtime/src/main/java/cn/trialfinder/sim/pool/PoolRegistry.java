package cn.trialfinder.sim.pool;

import cn.trialfinder.sim.json.Json;
import cn.trialfinder.sim.resource.ClasspathResourceLoader;
import cn.trialfinder.sim.resource.Holder;
import cn.trialfinder.sim.resource.Identifier;
import cn.trialfinder.sim.resource.ResourceKey;
import cn.trialfinder.sim.template.StructureTemplateManager;
import cn.trialfinder.sim.util.Pair;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 从数据驱动 JSON（{@code data/<namespace>/worldgen/template_pool/**}.json）加载结构模板池，
 * 解析 fallback 与 {@code minecraft:empty} 池。镜像 Minecraft 数据包加载器为 26.2
 * trial_chambers 产生的内容。
 */
public class PoolRegistry {
    private static final String POOL_DIR = "worldgen/template_pool";

    private final StructureTemplateManager templateManager;
    private final Map<ResourceKey<StructureTemplatePool>, StructureTemplatePool> pools = new HashMap<>();

    public PoolRegistry(StructureTemplateManager templateManager) {
        this.templateManager = templateManager;
    }

    /** 从 classpath 加载所有 pool JSON（磁盘目录或 jar 内部均可）。 */
    public void loadAll() {
        registerEmpty();
        loadFromClasspath();
        resolveFallbacks();
    }

    private void loadFromClasspath() {
        String prefix = "data/minecraft/" + POOL_DIR;
        for (String resource : ClasspathResourceLoader.listResourcePaths(prefix, ".json")) {
            Identifier id = resourceToIdentifier(prefix, resource);
            if (id == null) {
                continue;
            }
            try (InputStream stream = ClasspathResourceLoader.open(resource)) {
                registerPool(id, stream);
            } catch (IOException | RuntimeException e) {
                // 跳过损坏的 pool
            }
        }
    }

    private static Identifier resourceToIdentifier(String prefix, String resource) {
        String relative = resource.substring(prefix.length() + 1);
        if (!relative.endsWith(".json")) {
            return null;
        }
        return Identifier.of("minecraft", relative.substring(0, relative.length() - ".json".length()));
    }

    public Optional<StructureTemplatePool> get(ResourceKey<StructureTemplatePool> key) {
        return Optional.ofNullable(this.pools.get(key));
    }

    /** {@code minecraft:empty} 池——多数试炼密室 pool 的哨兵 fallback。 */
    public StructureTemplatePool emptyPool() {
        return this.pools.get(StructureTemplatePool.EMPTY_KEY);
    }

    public boolean isEmptyPool(StructureTemplatePool pool) {
        return pool == this.pools.get(StructureTemplatePool.EMPTY_KEY);
    }

    public StructureTemplatePool getOrThrow(ResourceKey<StructureTemplatePool> key) {
        StructureTemplatePool pool = this.pools.get(key);
        if (pool == null) {
            throw new IllegalStateException("池未注册: " + key);
        }
        return pool;
    }

    private void registerEmpty() {
        StructureTemplatePool empty = new StructureTemplatePool(
                Holder.reference(StructureTemplatePool.EMPTY_KEY), List.of());
        empty.resolveFallback(empty);
        this.pools.put(StructureTemplatePool.EMPTY_KEY, empty);
    }

    private void registerPool(Identifier id, InputStream stream) throws IOException {
        String text = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        Json.Object root = (Json.Object) Json.parse(text);
        ResourceKey<StructureTemplatePool> key = ResourceKey.create(id);
        if (this.pools.containsKey(key)) {
            return;
        }
        List<Pair<StructurePoolElement, Integer>> templates = new ArrayList<>();
        Json.Array elements = root.getArray("elements");
        for (Json.JsonValue elementEntry : elements.elements()) {
            Json.Object entry = (Json.Object) elementEntry;
            int weight = entry.getInt("weight");
            StructurePoolElement element = parseElement((Json.Object) entry.get("element"));
            templates.add(Pair.of(element, weight));
        }
        Identifier fallbackId = Identifier.fromString(root.getString("fallback"));
        Holder<StructureTemplatePool> fallback = Holder.reference(ResourceKey.create(fallbackId));
        this.pools.put(key, new StructureTemplatePool(fallback, templates));
    }

    private StructurePoolElement parseElement(Json.Object element) {
        String type = element.getString("element_type");
        return switch (type) {
            case "minecraft:empty_pool_element" -> EmptyPoolElement.INSTANCE;
            case "minecraft:single_pool_element" -> {
                Identifier location = Identifier.fromString(element.getString("location"));
                Projection projection = Projection.byName(element.getString("projection"));
                yield new SinglePoolElement(location, projection);
            }
            case "minecraft:list_pool_element" -> {
                Json.Array subElements = element.getArray("elements");
                List<StructurePoolElement> children = new ArrayList<>();
                for (Json.JsonValue sub : subElements.elements()) {
                    children.add(parseElement((Json.Object) sub));
                }
                Projection projection = Projection.byName(element.getString("projection"));
                yield new ListPoolElement(children, projection);
            }
            default -> throw new IllegalStateException("不支持的 pool 元素类型: " + type);
        };
    }

    private void resolveFallbacks() {
        for (Map.Entry<ResourceKey<StructureTemplatePool>, StructureTemplatePool> entry
                : this.pools.entrySet()) {
            Holder<StructureTemplatePool> fallback = entry.getValue().getFallback();
            if (fallback.isReference()) {
                StructureTemplatePool target = fallback.unwrapKey()
                        .map(this.pools::get)
                        .orElse(null);
                entry.getValue().resolveFallback(
                        target != null ? target : this.pools.get(StructureTemplatePool.EMPTY_KEY));
            }
        }
    }

    public Map<ResourceKey<StructureTemplatePool>, StructureTemplatePool> pools() {
        return this.pools;
    }

    public StructureTemplateManager templateManager() {
        return this.templateManager;
    }
}
