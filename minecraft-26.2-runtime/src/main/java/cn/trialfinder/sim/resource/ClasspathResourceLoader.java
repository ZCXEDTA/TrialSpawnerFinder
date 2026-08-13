package cn.trialfinder.sim.resource;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/**
 * 从 classpath 解析迁移后的 datapack 资源（{@code data/minecraft/...}：pool JSON、structure JSON、
 * 模板 NBT），而不是从 Minecraft 资源管理器。
 *
 * <p>三种情况：
 * <ul>
 *   <li><b>资源索引</b>（native-image / jar）：构建期生成的 {@code data/resources.list} 列出全部
 *       {@code data/...} 路径，{@link #listResourcePaths} 优先读它（native-image 镜像内无法枚举目录）。</li>
 *   <li><b>磁盘目录</b>（Gradle 开发/构建：{@code build/resources/main}）：{@code data} 是真实目录，
 *       可直接枚举。</li>
 *   <li><b>jar 内部</b>：从 jar 条目列表枚举（见 {@link #enumerateJarEntries}），按名打开（{@link #open}）。</li>
 * </ul>
 */
public final class ClasspathResourceLoader {
    private static final String RESOURCE_INDEX = "data/resources.list";

    private ClasspathResourceLoader() {
    }

    /** 从构建期生成的资源索引读取全部 {@code data/...} 路径；索引不存在时返回空列表。 */
    private static List<String> readResourceIndex() {
        try (InputStream stream = open(RESOURCE_INDEX)) {
            if (stream == null) {
                return List.of();
            }
            List<String> paths = new ArrayList<>();
            for (String line : new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                    .split("\\R")) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) {
                    paths.add(trimmed);
                }
            }
            return paths;
        } catch (IOException e) {
            return List.of();
        }
    }

    /** 解析 classpath {@code data} 目录为文件系统路径；资源未解压成目录（如在 jar 里）时返回 null。 */
    public static Path dataRootPath() {
        URL url = ClasspathResourceLoader.class.getClassLoader().getResource("data");
        if (url == null || !url.getProtocol().equals("file")) {
            return null;
        }
        try {
            return Paths.get(url.toURI());
        } catch (URISyntaxException e) {
            return null;
        }
    }

    /** 解析包含 {@code data/} 的目录（即 classpath 根）。classpath 不是目录时回退 {@code src/main/resources}。 */
    public static Path baseDirPath() {
        Path data = dataRootPath();
        if (data != null && data.getParent() != null) {
            return data.getParent();
        }
        return Path.of("src/main/resources");
    }

    /**
     * 列出 {@code prefix} 下、带 {@code suffix} 后缀的 classpath 资源名。
     * 优先读构建期资源索引（native-image / jar 可用），否则回退磁盘目录遍历或 jar 条目枚举。
     */
    public static List<String> listResourcePaths(String prefix, String suffix) {
        List<String> fromIndex = listFromIndex(prefix, suffix);
        if (!fromIndex.isEmpty()) {
            return fromIndex;
        }
        List<String> result = new ArrayList<>();
        URL url = ClasspathResourceLoader.class.getClassLoader().getResource(prefix);
        if (url == null) {
            return result;
        }
        if (url.getProtocol().equals("file")) {
            try {
                Path root = Paths.get(url.toURI());
                try (Stream<Path> walk = Files.walk(root)) {
                    walk.filter(Files::isRegularFile)
                            .filter(path -> path.toString().endsWith(suffix))
                            .forEach(path -> result.add(prefix + "/"
                                    + root.relativize(path).toString().replace('\\', '/')));
                }
            } catch (IOException | URISyntaxException e) {
                // 磁盘不可枚举——走 jar 分支
            }
        }
        if ("jar".equals(url.getProtocol())) {
            enumerateJarEntries(url, prefix, suffix, result);
        }
        return result;
    }

    private static List<String> listFromIndex(String prefix, String suffix) {
        List<String> result = new ArrayList<>();
        for (String path : readResourceIndex()) {
            if (path.startsWith(prefix + "/") && path.endsWith(suffix)) {
                result.add(path);
            }
        }
        return result;
    }

    private static void enumerateJarEntries(URL url, String prefix, String suffix, List<String> out) {
        String spec = url.getPath();
        int bang = spec.indexOf("!/");
        if (bang < 0) {
            return;
        }
        try {
            String jarSpec = spec.substring(0, bang);
            try (JarFile jar = new JarFile(Paths.get(new java.net.URI(jarSpec)).toFile())) {
                String dir = prefix + "/";
                jar.stream()
                        .map(JarEntry::getName)
                        .filter(name -> name.startsWith(dir))
                        .filter(name -> name.endsWith(suffix))
                        .forEach(out::add);
            }
        } catch (IOException | URISyntaxException e) {
            // 无法打开 jar——调用方回退到已有结果
        }
    }

    /** 按名打开 classpath 资源（如 {@code data/minecraft/structure/foo.nbt}）。 */
    public static InputStream open(String resource) {
        String normalized = resource.startsWith("/") ? resource.substring(1) : resource;
        return ClasspathResourceLoader.class.getClassLoader().getResourceAsStream(normalized);
    }
}
