package cn.minecraftfinder.runtime;

import cn.minecraftfinder.core.ResultFiles;
import cn.trialfinder.config.FinderConfig;
import cn.trialfinder.search.FinderSearch;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;

import java.nio.file.Path;

public final class MinecraftFindersMod implements DedicatedServerModInitializer {
    private static final Path CONFIG_PATH = Path.of("finder.properties");

    @Override
    public void onInitializeServer() {
        ServerLifecycleEvents.SERVER_STARTED.register(this::runSelectedFinder);
    }

    private void runSelectedFinder(MinecraftServer server) {
        FinderExecution.run(server, "试炼刷怪笼", () -> runTrialSearch(server));
    }

    private void runTrialSearch(MinecraftServer server) throws Exception {
        Path outputPath = createOutputPath();
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

        FinderConfig config = FinderConfig.load(CONFIG_PATH);
        if (server.overworld().getSeed() != config.seed()) {
            throw new IllegalStateException("服务端世界种子与 finder.properties 不一致");
        }
        FinderSearch search = new FinderSearch(
                config, outputPath, new ConsoleProgressReporter());
        active[0] = search;
        search.run(server.overworld());
        active[0] = null;
    }

    private static Path createOutputPath() {
        String configured = System.getProperty("minecraftfinders.output");
        if (configured == null || configured.isBlank()) {
            configured = System.getProperty("trialfinder.output");
        }
        return configured == null || configured.isBlank()
                ? ResultFiles.next(Path.of(".."), "trial-spawner")
                : ResultFiles.next(Path.of(configured));
    }
}
