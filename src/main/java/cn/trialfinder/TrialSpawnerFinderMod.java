package cn.trialfinder;

import cn.trialfinder.config.FinderConfig;
import cn.trialfinder.io.ResultWriter;
import cn.trialfinder.search.FinderSearch;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;

import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class TrialSpawnerFinderMod implements DedicatedServerModInitializer {
    private static final DateTimeFormatter OUTPUT_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    @Override
    public void onInitializeServer() {
        ServerLifecycleEvents.SERVER_STARTED.register(this::runSearch);
    }

    private void runSearch(MinecraftServer server) {
        Path configPath = Path.of("finder.properties");
        Path outputPath = createOutputPath();
        Path failurePath = Path.of("search.failed");
        FinderSearch[] active = new FinderSearch[1];
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (active[0] != null) {
                try {
                    active[0].save();
                } catch (Exception e) {
                    System.err.println("中止时保存结果失败: " + e.getMessage());
                }
            }
        }, "trial-finder-save"));

        try {
            Files.deleteIfExists(failurePath);
            FinderConfig config = FinderConfig.load(configPath);
            if (server.overworld().getSeed() != config.seed()) {
                throw new IllegalStateException("服务端世界种子与 finder.properties 不一致");
            }
            FinderSearch search = new FinderSearch(config, outputPath);
            active[0] = search;
            search.run(server.overworld());
            active[0] = null;
        } catch (Exception e) {
            System.err.println("TrialSpawnerFinder 搜索失败：" + e.getMessage());
            e.printStackTrace(System.err);
            try {
                Files.writeString(failurePath, e.toString(), StandardCharsets.UTF_8);
            } catch (Exception markerError) {
                System.err.println("写入失败标记失败：" + markerError.getMessage());
            }
        } finally {
            server.halt(false);
        }
    }

    private static Path createOutputPath() {
        String configured = System.getProperty("trialfinder.output");
        Path requested = configured == null || configured.isBlank()
                ? Path.of("..").resolve("results-" + OUTPUT_TIMESTAMP.format(LocalDateTime.now()) + ".csv")
                : Path.of(configured);
        requested = requested.normalize();
        if (!outputExists(requested)) {
            return requested;
        }

        String fileName = requested.getFileName().toString();
        int extension = fileName.toLowerCase().endsWith(".csv") ? fileName.length() - 4 : fileName.length();
        String base = fileName.substring(0, extension);
        String suffix = fileName.substring(extension);
        for (int index = 2; ; index++) {
            Path candidate = requested.resolveSibling(base + "-" + index + suffix);
            if (!outputExists(candidate)) {
                return candidate;
            }
        }
    }

    private static boolean outputExists(Path csvPath) {
        return Files.exists(csvPath) || Files.exists(ResultWriter.textPath(csvPath));
    }
}
