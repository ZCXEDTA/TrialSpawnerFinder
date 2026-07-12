package cn.trialfinder;

import cn.trialfinder.config.FinderConfig;
import cn.trialfinder.search.FinderSearch;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;

import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;

public final class TrialSpawnerFinderMod implements DedicatedServerModInitializer {
    @Override
    public void onInitializeServer() {
        ServerLifecycleEvents.SERVER_STARTED.register(this::runSearch);
    }

    private void runSearch(MinecraftServer server) {
        Path configPath = Path.of("finder.properties");
        Path outputPath = Path.of("..").resolve("results.csv").normalize();
        Path failurePath = Path.of("search.failed");
        FinderSearch[] active = new FinderSearch[1];
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (active[0] != null) {
                try {
                    active[0].save();
                } catch (Exception e) {
                    System.err.println("中止时保存 results.csv 失败: " + e.getMessage());
                }
            }
        }, "trial-finder-save"));

        try {
            Files.deleteIfExists(failurePath);
            FinderConfig config = FinderConfig.load(configPath);
            if (server.getOverworld().getSeed() != config.seed()) {
                throw new IllegalStateException("服务端世界种子与 finder.properties 不一致");
            }
            FinderSearch search = new FinderSearch(config, outputPath);
            active[0] = search;
            search.run(server.getOverworld());
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
            server.stop(false);
        }
    }
}
