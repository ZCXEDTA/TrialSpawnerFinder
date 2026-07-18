package cn.minecraftfinder.runtime;

import net.minecraft.server.MinecraftServer;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class FinderExecution {
    private static final Path FAILURE_PATH = Path.of("search.failed");

    private FinderExecution() {
    }

    public static void run(
            MinecraftServer server, String finderName, FinderAction action) {
        try {
            Files.deleteIfExists(FAILURE_PATH);
            action.run();
        } catch (Exception e) {
            System.err.println(finderName + "搜索失败：" + e.getMessage());
            e.printStackTrace(System.err);
            try {
                Files.writeString(FAILURE_PATH, e.toString(), StandardCharsets.UTF_8);
            } catch (Exception markerError) {
                System.err.println("写入失败标记失败：" + markerError.getMessage());
            }
        } finally {
            server.halt(false);
        }
    }

    @FunctionalInterface
    public interface FinderAction {
        void run() throws Exception;
    }
}
