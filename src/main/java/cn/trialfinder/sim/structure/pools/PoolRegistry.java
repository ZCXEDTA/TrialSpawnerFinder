package cn.trialfinder.sim.structure.pools;

import cn.trialfinder.sim.resources.ClasspathResourceLoader;
import cn.trialfinder.sim.resources.Holder;
import cn.trialfinder.sim.resources.Identifier;
import cn.trialfinder.sim.resources.ResourceKey;
import cn.trialfinder.sim.structure.StructureTemplateManager;
import cn.trialfinder.sim.util.Pair;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Loads structure-template pools from the data-driven JSON files
 * ({@code data/<namespace>/worldgen/template_pool/**}.json), resolving fallbacks and the
 * {@code minecraft:empty} pool. Mirrors what Minecraft's datapack loader produces for
 * trial_chambers in 1.21.11.
 */
public class PoolRegistry {
    private static final String POOL_DIR = "worldgen/template_pool";

    private final Path baseDir;
    private final StructureTemplateManager templateManager;
    private final Map<ResourceKey<StructureTemplatePool>, StructureTemplatePool> pools = new HashMap<>();

    public PoolRegistry(Path baseDir, StructureTemplateManager templateManager) {
        this.baseDir = baseDir;
        this.templateManager = templateManager;
    }

    /** Loads all pool JSONs reachable under the base dir / classpath. */
    public void loadAll() {
        registerEmpty();
        loadFromDir(this.baseDir.resolve("data"));
        loadFromClasspath();
        resolveFallbacks();
    }

    /**
     * Loads pool JSONs from the classpath only. In the dev/build case the classpath
     * {@code data} directory is unpacked on disk (build/resources/main), so it is enumerated
     * directly; inside a jar the same resources are enumerated from the jar's entry list.
     */
    public void loadFromClasspath() {
        registerEmpty();
        Path dataRoot = ClasspathResourceLoader.dataRootPath();
        if (dataRoot != null) {
            loadFromDir(dataRoot);
        } else {
            loadFromClasspathJar();
        }
        resolveFallbacks();
    }

    /** Enumerates {@code data/minecraft/&lt;POOL_DIR&gt;/**} from inside the running jar. */
    private void loadFromClasspathJar() {
        String poolPrefix = "data/minecraft/" + POOL_DIR;
        for (String resource : ClasspathResourceLoader.listResourcePaths(poolPrefix, ".json")) {
            Identifier id = resourceToIdentifier(poolPrefix, resource);
            if (id == null) {
                continue;
            }
            try (InputStream stream = ClasspathResourceLoader.open(resource)) {
                registerPool(id, stream);
            } catch (IOException | RuntimeException e) {
                // skip malformed pool
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

    /** The {@code minecraft:empty} pool — the sentinel fallback for most trial-chamber pools. */
    public StructureTemplatePool emptyPool() {
        return this.pools.get(StructureTemplatePool.EMPTY_KEY);
    }

    public boolean isEmptyPool(StructureTemplatePool pool) {
        return pool == this.pools.get(StructureTemplatePool.EMPTY_KEY);
    }

    public StructureTemplatePool getOrThrow(ResourceKey<StructureTemplatePool> key) {
        StructureTemplatePool pool = this.pools.get(key);
        if (pool == null) {
            throw new IllegalStateException("Pool not registered: " + key);
        }
        return pool;
    }

    private void registerEmpty() {
        StructureTemplatePool empty = new StructureTemplatePool(Holder.reference(StructureTemplatePool.EMPTY_KEY),
                List.of(), Projection.RIGID);
        empty.resolveFallback(empty);
        this.pools.put(StructureTemplatePool.EMPTY_KEY, empty);
    }

    private void loadFromDir(Path dataDir) {
        Path poolRoot = dataDir.resolve("minecraft").resolve(POOL_DIR);
        if (!Files.isDirectory(poolRoot)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(poolRoot)) {
            walk.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".json"))
                    .forEach(path -> {
                        Identifier id = fileToIdentifier(poolRoot, path);
                        if (id != null) {
                            try (InputStream stream = Files.newInputStream(path)) {
                                registerPool(id, stream);
                            } catch (IOException e) {
                                // skip malformed pool
                            }
                        }
                    });
        } catch (IOException e) {
            // ignore
        }
    }

    private void registerPool(Identifier id, InputStream stream) throws IOException {
        JsonObject root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        ResourceKey<StructureTemplatePool> key = ResourceKey.create(id);
        if (this.pools.containsKey(key)) {
            return;
        }
        List<Pair<StructurePoolElement, Integer>> templates = new ArrayList<>();
        JsonArray elements = root.getAsJsonArray("elements");
        for (JsonElement elementEntry : elements) {
            JsonObject entry = elementEntry.getAsJsonObject();
            int weight = entry.get("weight").getAsInt();
            StructurePoolElement element = parseElement(entry.get("element"));
            templates.add(Pair.of(element, weight));
        }
        Identifier fallbackId = Identifier.parse(root.get("fallback").getAsString());
        Holder<StructureTemplatePool> fallback = Holder.reference(ResourceKey.create(fallbackId));
        this.pools.put(key, new StructureTemplatePool(fallback, templates));
    }

    private StructurePoolElement parseElement(JsonElement elementJson) {
        JsonObject element = elementJson.getAsJsonObject();
        String type = element.get("element_type").getAsString();
        return switch (type) {
            case "minecraft:empty_pool_element" -> EmptyPoolElement.INSTANCE;
            case "minecraft:single_pool_element" -> {
                Identifier location = Identifier.parse(element.get("location").getAsString());
                Projection projection = Projection.byName(element.get("projection").getAsString());
                yield new SinglePoolElement(location, StructurePoolElement.emptyProcessors(), projection, Optional.empty());
            }
            case "minecraft:legacy_single_pool_element" -> {
                Identifier location = Identifier.parse(element.get("location").getAsString());
                Projection projection = Projection.byName(element.get("projection").getAsString());
                yield new LegacySinglePoolElement(location, StructurePoolElement.emptyProcessors(), projection, Optional.empty());
            }
            case "minecraft:list_pool_element" -> {
                JsonArray subElements = element.getAsJsonArray("elements");
                List<StructurePoolElement> children = new ArrayList<>();
                for (JsonElement sub : subElements) {
                    children.add(parseElement(sub));
                }
                Projection projection = Projection.byName(element.get("projection").getAsString());
                yield new ListPoolElement(children, projection);
            }
            default -> throw new IllegalStateException("Unsupported pool element type: " + type);
        };
    }

    private void resolveFallbacks() {
        for (Map.Entry<ResourceKey<StructureTemplatePool>, StructureTemplatePool> entry : this.pools.entrySet()) {
            Holder<StructureTemplatePool> fallback = entry.getValue().getFallback();
            if (fallback.isReference()) {
                StructureTemplatePool target = this.pools.get(fallback.unwrapKey());
                entry.getValue().resolveFallback(target != null ? target : this.pools.get(StructureTemplatePool.EMPTY_KEY));
            }
        }
    }

    private static Identifier fileToIdentifier(Path root, Path file) {
        String relative = root.relativize(file).toString().replace('\\', '/');
        if (!relative.endsWith(".json")) {
            return null;
        }
        return Identifier.of("minecraft", relative.substring(0, relative.length() - ".json".length()));
    }

    public Map<ResourceKey<StructureTemplatePool>, StructureTemplatePool> pools() {
        return this.pools;
    }

    public StructureTemplateManager templateManager() {
        return this.templateManager;
    }
}
