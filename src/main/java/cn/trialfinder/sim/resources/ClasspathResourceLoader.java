package cn.trialfinder.sim.resources;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
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
 * Resolves the migrated datapack resources ({@code data/minecraft/...}: pool JSONs, structure
 * JSONs, template NBTs) from the classpath rather than from a Minecraft resource manager.
 *
 * <p>Two cases are supported:
 * <ul>
 *   <li><b>Unpacked on disk</b> (normal Gradle dev/build: {@code build/resources/main}): the
 *       {@code data} directory is a real directory on the classpath, so it can be enumerated
 *       and parsed directly from its {@link Path}.</li>
 *   <li><b>Inside a jar</b>: the same {@code data} tree is enumerated from the jar's entry
 *       list (see {@link #listResourcePaths}), and individual resources are opened by name via
 *       {@link #open} (used by {@code PoolRegistry} and {@code StructureTemplateManager}).</li>
 * </ul>
 */
public final class ClasspathResourceLoader {
    private ClasspathResourceLoader() {
    }

    /**
     * Resolves the classpath {@code data} directory to a filesystem {@link Path}, or returns
     * {@code null} when the resources are not unpacked as a directory (e.g. inside a jar).
     */
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

    /**
     * Resolves the directory that <em>contains</em> {@code data/} — i.e. the classpath root
     * suitable as the {@code baseDir} for {@code PoolRegistry}/{@code StructureTemplateManager}.
     * Falls back to {@code src/main/resources} when the classpath is not a directory.
     */
    public static Path baseDirPath() {
        Path data = dataRootPath();
        if (data != null && data.getParent() != null) {
            return data.getParent();
        }
        return Path.of("src/main/resources");
    }

    /** True when the classpath {@code data} root is enumerable as a filesystem directory. */
    public static boolean isDirectoryAvailable() {
        Path data = dataRootPath();
        return data != null && Files.isDirectory(data);
    }

    /**
     * Lists classpath resource names (e.g. {@code data/minecraft/worldgen/template_pool/...json})
     * under {@code prefix} with the given {@code suffix}. Works both when the classpath is unpacked
     * on disk (walked as a directory) and inside a jar (enumerated from the jar's entry list).
     *
     * @param prefix directory prefix relative to the classpath root, no leading/trailing slash
     * @param suffix file suffix filter, e.g. {@code ".json"}
     * @return resource names relative to the classpath root, slash-separated, in no particular order
     */
    public static List<String> listResourcePaths(String prefix, String suffix) {
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
                            .forEach(path -> result.add(root.relativize(path).toString().replace('\\', '/')));
                }
            } catch (IOException | URISyntaxException e) {
                // not enumerable on disk — fall through to the jar branch below if any
            }
        }
        if ("jar".equals(url.getProtocol())) {
            enumerateJarEntries(url, prefix, suffix, result);
        }
        return result;
    }

    private static void enumerateJarEntries(URL url, String prefix, String suffix, List<String> out) {
        // jar:file:/path/to.jar!/data/... — the jar file is the part before "!/".
        String spec = url.getPath();
        int bang = spec.indexOf("!/");
        if (bang < 0) {
            return;
        }
        try {
            String jarSpec = spec.substring(0, bang);
            try (JarFile jar = new JarFile(Paths.get(new URI(jarSpec)).toFile())) {
                String dir = prefix + "/";
                jar.stream()
                        .map(JarEntry::getName)
                        .filter(name -> name.startsWith(dir))
                        .filter(name -> name.endsWith(suffix))
                        .forEach(out::add);
            }
        } catch (IOException | URISyntaxException e) {
            // cannot open the jar — caller falls back to whatever it already has
        }
    }

    /** Lists all regular files under {@code root} (recursively) with the given suffix. */
    public static List<Path> listFiles(Path root, String suffix) {
        List<Path> result = new ArrayList<>();
        if (root == null || !Files.isDirectory(root)) {
            return result;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(suffix))
                    .forEach(result::add);
        } catch (IOException e) {
            // not enumerable — return what we have
        }
        return result;
    }

    /** Counts regular files under {@code root} (recursively) with the given suffix. */
    public static long countFiles(Path root, String suffix) {
        return listFiles(root, suffix).size();
    }

    /**
     * Opens a classpath resource by name (e.g. {@code data/minecraft/structure/foo.nbt}).
     * Works whether the resources are unpacked or inside a jar.
     */
    public static InputStream open(String resource) {
        String normalized = resource.startsWith("/") ? resource.substring(1) : resource;
        return ClasspathResourceLoader.class.getClassLoader().getResourceAsStream(normalized);
    }
}
