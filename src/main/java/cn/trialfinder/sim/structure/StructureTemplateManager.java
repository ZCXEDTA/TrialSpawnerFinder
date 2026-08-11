package cn.trialfinder.sim.structure;

import cn.trialfinder.sim.resources.Identifier;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Port of net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager
 * (1.21.11), simplified: loads .nbt templates either from a resource-pack-style directory
 * ({@code <root>/data/<namespace>/structure/<path>.nbt}) or from the classpath
 * ({@code /data/<namespace>/structure/<path>.nbt}).
 */
public class StructureTemplateManager {
    private final Map<Identifier, Optional<StructureTemplate>> cache = new ConcurrentHashMap<>();
    private final Path baseDir;
    private final boolean classpathEnabled;

    public StructureTemplateManager(Path baseDir) {
        this.baseDir = baseDir;
        this.classpathEnabled = true;
    }

    public Optional<StructureTemplate> get(Identifier id) {
        return this.cache.computeIfAbsent(id, this::tryLoad);
    }

    public StructureTemplate getOrCreate(Identifier id) {
        return this.get(id).orElseGet(StructureTemplate::new);
    }

    private Optional<StructureTemplate> tryLoad(Identifier id) {
        Path file = this.baseDir.resolve("data")
                .resolve(id.getNamespace())
                .resolve("structure")
                .resolve(id.getPath() + ".nbt");
        if (Files.isRegularFile(file)) {
            try {
                return Optional.of(StructureTemplate.loadFrom(file));
            } catch (IOException e) {
                return Optional.empty();
            }
        }
        if (this.classpathEnabled) {
            String resource = "/data/" + id.getNamespace() + "/structure/" + id.getPath() + ".nbt";
            try (InputStream stream = StructureTemplateManager.class.getResourceAsStream(resource)) {
                if (stream != null) {
                    return Optional.of(StructureTemplate.loadFrom(stream));
                }
            } catch (IOException e) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }
}
