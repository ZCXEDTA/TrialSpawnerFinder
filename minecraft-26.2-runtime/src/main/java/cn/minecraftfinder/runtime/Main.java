package cn.minecraftfinder.runtime;

import cn.minecraftfinder.core.AreaShape;
import cn.minecraftfinder.core.ProgressReporter;
import cn.minecraftfinder.core.ResultFiles;
import cn.trialfinder.config.FinderConfig;
import cn.trialfinder.config.TrialSearchMode;
import cn.trialfinder.query.CheckTopChecker;
import cn.trialfinder.query.PointQuery;
import cn.trialfinder.query.QueryRenderer;
import cn.trialfinder.search.FinderSearch;
import cn.trialfinder.sim.pool.PoolRegistry;
import cn.trialfinder.sim.template.StructureTemplateManager;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * 纯 Java 独立运行入口：
 * <ul>
 *   <li>无参数（默认）：读 {@code finder.properties} → 全量搜索 → 输出 CSV/TXT。</li>
 *   <li>命令行 {@code --key value}：覆盖 {@code finder.properties} 对应配置（全量搜索）。</li>
 *   <li>{@code --no-progress}：关闭进度协议输出（适合纯命令行/脚本环境）。</li>
 *   <li>{@code query} 子命令：定点查询指定坐标附近的试炼密室详情。</li>
 * </ul>
 * 失败时写 {@code search.failed} 标记供启动脚本读取。
 */
public final class Main {
    private static volatile Path baseDir;

    private Main() {
    }

    public static void main(String[] args) {
        int exitCode = run(args);
        System.exit(exitCode);
    }

    static int run(String[] args) {
        try {
            Files.deleteIfExists(failurePath());
            if (wantsHelp(args)) {
                printHelp();
                return 0;
            }
            boolean noProgress = containsFlag(args, "--no-progress");
            String[] cleanArgs = withoutFlag(args, "--no-progress");
            if (cleanArgs.length > 0 && "query".equalsIgnoreCase(cleanArgs[0])) {
                runQuery(cleanArgs);
            } else {
                runTrialSearch(cleanArgs, noProgress);
            }
            return 0;
        } catch (Exception e) {
            System.err.println("搜索失败：" + e.getMessage());
            e.printStackTrace(System.err);
            try {
                Files.writeString(failurePath(), String.valueOf(e), StandardCharsets.UTF_8);
            } catch (IOException markerError) {
                System.err.println("写入失败标记失败：" + markerError.getMessage());
            }
            return 1;
        }
    }

    static boolean wantsHelp(String[] args) {
        if (args == null) {
            return false;
        }
        for (String arg : args) {
            if ("--help".equals(arg) || "-h".equals(arg) || "help".equalsIgnoreCase(arg)) {
                return true;
            }
        }
        return false;
    }

    private static void printHelp() {
        System.out.println("""
                TrialSpawnerFinder - 在 Minecraft 26.2 世界种子中查找试炼密室密集区域

                用法:
                  trial.bat [--key value ...]              全量搜索（配置从 finder.properties 读取，
                                                             命令行 --key value 覆盖）
                  trial.bat query --coords x,z ...         定点查询坐标附近的密室详情
                  trial.bat --help | -h                   显示本帮助

                全量搜索参数（覆盖 finder.properties，键名与配置一致）:
                  --seed <long>                           世界种子
                  --search-center-x <int>                 搜索中心 X
                  --search-center-z <int>                 搜索中心 Z
                  --search-radius-blocks <int>            搜索半径（方块）
                  --full-world <true|false>               扫描完整世界
                  --search-area-shape <circle|square>     搜索区域形状
                  --trial-cluster-radius-blocks <int>     聚类统计半径（方块）
                  --trial-area-shape <circle|square>      统计范围形状
                  --trial-min-structures <int>            最低密室数量
                  --trial-min-spawners <int>              最低刷怪笼数量
                  --scan-threads <int>                    并行扫描线程数
                  --scan-shard-size-blocks <int>          扫描分片边长（方块）
                  --trial-search-mode <auto|exact>        搜索模式
                  --trial-prediction-calibration-structures <int>
                                                          启动校准密室数量
                  --check-top <int>                       统计前 N 个结果的快/慢刷怪笼与宝库
                  --no-progress                           完全关闭进度条（脚本/CI 用）

                定点查询参数:
                  query --coords x,z x,z ...              查询点（逗号分隔，可多个）
                  query --file <path>                     查询点文件（每行 x z，或结果 CSV）
                  query --radius <int>                    查询半径（默认 1000）
                  query --seed <long>                     世界种子（默认读 finder.properties）
                  query --output <table|csv>              输出格式（默认 table）

                示例:
                  trial.bat --seed 123 --search-radius-blocks 5000
                  trial.bat query --coords 0,0 --radius 1000
                  trial.bat query --seed 123 --coords 0,0 --radius 1000
                """);
    }

    // ---------------------------------------------------------------- 路径定位

    /**
     * 探测基准目录（配置、输出、失败标记所在目录）：
     * exe/jar 所在目录 → 当前工作目录，取第一个存在 {@code finder.properties} 的。
     */
    static Path baseDir() {
        Path resolved = baseDir;
        if (resolved != null) {
            return resolved;
        }
        List<Path> candidates = new ArrayList<>();
        Path selfDir = selfDir();
        if (selfDir != null) {
            candidates.add(selfDir);
        }
        candidates.add(Path.of("").toAbsolutePath());
        for (Path dir : candidates) {
            if (dir != null && Files.isRegularFile(dir.resolve("finder.properties"))) {
                baseDir = dir;
                return dir;
            }
        }
        baseDir = Path.of("").toAbsolutePath();
        return baseDir;
    }

    /** exe 或 jar 所在目录；无法确定时返回 null。 */
    private static Path selfDir() {
        if (System.getProperty("org.graalvm.nativeimage.imagecode") != null) {
            // native-image：进程可执行文件路径
            return ProcessHandle.current().info().command()
                    .map(Path::of)
                    .map(Path::toAbsolutePath)
                    .map(Path::getParent)
                    .orElse(null);
        }
        try {
            Path location = Paths.get(
                    Main.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            return location.toAbsolutePath().getParent();
        } catch (URISyntaxException | IllegalArgumentException e) {
            return null;
        }
    }

    private static Path configPath() {
        return baseDir().resolve("finder.properties");
    }

    private static Path failurePath() {
        return baseDir().resolve("search.failed");
    }

    private static boolean containsFlag(String[] args, String flag) {
        for (String arg : args) {
            if (flag.equals(arg)) {
                return true;
            }
        }
        return false;
    }

    static String[] withoutFlag(String[] args, String flag) {
        List<String> result = new ArrayList<>();
        for (String arg : args) {
            if (!flag.equals(arg)) {
                result.add(arg);
            }
        }
        return result.toArray(new String[0]);
    }

    // ---------------------------------------------------------------- 全量搜索

    private static void runTrialSearch(String[] args, boolean noProgress) throws IOException {
        FinderConfig base = loadConfig();
        FinderConfig config = applyOverrides(base, args);
        int checkTop = parseCheckTop(args);
        Environment env = loadEnvironment();
        Path outputPath = createOutputPath();
        ConsoleProgressReporter console = new ConsoleProgressReporter();
        ProgressReporter progress = noProgress ? ProgressReporter.NONE : console;
        FinderSearch search = new FinderSearch(
                config, outputPath, progress, env.pools(), env.templates());
        if (checkTop > 0) {
            CheckTopChecker checker = new CheckTopChecker(
                    config.seed(), env.pools(), env.templates());
            search.enableCheckTop(checkTop, results -> checker.check(results, checkTop));
            System.out.println("已启用 check-top：统计前 " + checkTop + " 个结果的快/慢刷怪笼与宝库");
        }
        search.run();
        console.clearLine();
    }

    /** 解析 {@code --check-top N}；无则返回 0。 */
    private static int parseCheckTop(String[] args) {
        if (args == null) {
            return 0;
        }
        for (int i = 0; i < args.length - 1; i++) {
            if ("--check-top".equals(args[i])) {
                return Integer.parseInt(args[i + 1]);
            }
        }
        return 0;
    }

    /**
     * 用命令行 {@code --key value} 覆盖 {@code finder.properties} 的配置。
     * 键名与 finder.properties 一致；无覆盖项时返回 base。{@code --no-progress} 被忽略（布尔开关）。
     */
    static FinderConfig applyOverrides(FinderConfig base, String[] args) {
        if (args == null || args.length == 0) {
            return base;
        }
        long seed = base.seed();
        int searchCenterX = base.searchCenterX();
        int searchCenterZ = base.searchCenterZ();
        int searchRadiusBlocks = base.searchRadiusBlocks();
        boolean fullWorld = base.fullWorld();
        AreaShape searchAreaShape = base.searchAreaShape();
        int clusterRadiusBlocks = base.clusterRadiusBlocks();
        AreaShape areaShape = base.areaShape();
        int minStructures = base.minStructures();
        int minSpawners = base.minSpawners();
        int scanThreads = base.scanThreads();
        int scanShardSizeBlocks = base.scanShardSizeBlocks();
        TrialSearchMode searchMode = base.searchMode();
        int predictionCalibrationStructures = base.predictionCalibrationStructures();

        for (int i = 0; i < args.length; i++) {
            String key = args[i];
            if ("--no-progress".equals(key)) {
                continue;
            }
            if ("--check-top".equals(key)) {
                i++; // 跳过其值（由 parseCheckTop 处理）
                continue;
            }
            if (!key.startsWith("--")) {
                throw new IllegalArgumentException("未知参数: " + key + "（使用 --key value 形式）");
            }
            if (i + 1 >= args.length) {
                throw new IllegalArgumentException("参数缺少值: " + key);
            }
            String value = args[++i];
            switch (key) {
                case "--seed" -> seed = Long.parseLong(value);
                case "--search-center-x" -> searchCenterX = Integer.parseInt(value);
                case "--search-center-z" -> searchCenterZ = Integer.parseInt(value);
                case "--search-radius-blocks" -> searchRadiusBlocks = Integer.parseInt(value);
                case "--full-world" -> fullWorld = Boolean.parseBoolean(value);
                case "--search-area-shape" -> searchAreaShape = AreaShape.parse(value);
                case "--trial-cluster-radius-blocks" -> clusterRadiusBlocks = Integer.parseInt(value);
                case "--trial-area-shape" -> areaShape = AreaShape.parse(value);
                case "--trial-min-structures" -> minStructures = Integer.parseInt(value);
                case "--trial-min-spawners" -> minSpawners = Integer.parseInt(value);
                case "--scan-threads" -> scanThreads = Integer.parseInt(value);
                case "--scan-shard-size-blocks" -> scanShardSizeBlocks = Integer.parseInt(value);
                case "--trial-search-mode" -> searchMode = TrialSearchMode.parse(value);
                case "--trial-prediction-calibration-structures" ->
                        predictionCalibrationStructures = Integer.parseInt(value);
                default -> throw new IllegalArgumentException("未知配置项: " + key);
            }
        }
        return new FinderConfig(
                seed, searchCenterX, searchCenterZ, searchRadiusBlocks, fullWorld,
                searchAreaShape, clusterRadiusBlocks, areaShape, minStructures, minSpawners,
                scanThreads, scanShardSizeBlocks, searchMode, predictionCalibrationStructures);
    }

    // ---------------------------------------------------------------- 定点查询

    /** 解析 query 子命令参数并执行。用法：query --coords x,z x,z ... [--file path] [--radius N] [--output table|csv] */
    private static void runQuery(String[] args) throws IOException {
        List<int[]> points = new ArrayList<>();
        String file = null;
        int radius = 1000;
        String output = "table";
        Long seed = null;
        for (int i = 1; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "--coords" -> {
                    while (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                        parseCoord(args[++i], points);
                    }
                }
                case "--file" -> {
                    if (i + 1 < args.length) {
                        file = args[++i];
                    }
                }
                case "--radius" -> {
                    if (i + 1 < args.length) {
                        radius = Integer.parseInt(args[++i]);
                    }
                }
                case "--output" -> {
                    if (i + 1 < args.length) {
                        output = args[++i];
                    }
                }
                case "--seed" -> {
                    if (i + 1 < args.length) {
                        seed = Long.parseLong(args[++i]);
                    }
                }
                default -> throw new IllegalArgumentException("未知 query 参数: " + arg);
            }
        }
        if (file != null) {
            parseQueryFile(Path.of(file), points);
        }
        if (points.isEmpty()) {
            throw new IllegalArgumentException("query 需要至少一个坐标点（--coords 或 --file）");
        }
        if (radius <= 0) {
            throw new IllegalArgumentException("--radius 必须为正数");
        }

        FinderConfig config = loadConfig();
        long effectiveSeed = seed != null ? seed : config.seed();
        Environment env = loadEnvironment();
        PointQuery query = new PointQuery(effectiveSeed, radius, env.pools(), env.templates());
        System.out.println("=== 定点查询 ===");
        System.out.println("seed: " + effectiveSeed + "  radius: " + radius
                + "  查询点: " + points.size() + "  输出: " + output);
        List<PointQuery.QueryResult> results = new ArrayList<>(points.size());
        for (int[] point : points) {
            results.add(query.query(point[0], point[1]));
        }
        switch (output.toLowerCase()) {
            case "csv" -> {
                Path path = QueryRenderer.writeCsv(results);
                System.out.println("结果文件：" + path.toAbsolutePath());
            }
            case "table" -> QueryRenderer.renderTable(results, radius);
            default -> throw new IllegalArgumentException("--output 只能是 table 或 csv: " + output);
        }
    }

    private static void parseCoord(String coord, List<int[]> points) {
        String[] parts = coord.split(",");
        if (parts.length != 2) {
            throw new IllegalArgumentException("--coords 必须是 'x,z' 格式，得到: " + coord);
        }
        points.add(new int[]{Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim())});
    }

    /**
     * 解析查询点文件：每行 {@code x z}（空格/逗号分隔，# 注释），
     * 或包含 {@code 中心X}/{@code 中心Z} 列的结果 CSV。
     */
    static void parseQueryFile(Path file, List<int[]> points) throws IOException {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        if (lines.isEmpty()) {
            return;
        }
        String header = lines.get(0);
        if (header.contains("中心X") || header.contains("CenterX")) {
            String[] columns = header.split(";|,|\\s+");
            int idxX = -1;
            int idxZ = -1;
            for (int i = 0; i < columns.length; i++) {
                String column = columns[i].trim();
                if (column.equals("中心X") || column.equals("CenterX")) {
                    idxX = i;
                }
                if (column.equals("中心Z") || column.equals("CenterZ")) {
                    idxZ = i;
                }
            }
            for (int i = 1; i < lines.size(); i++) {
                String line = lines.get(i).trim();
                if (line.isEmpty()) {
                    continue;
                }
                String[] values = line.split(";|,|\\s+");
                if (idxX >= 0 && idxZ >= 0 && values.length > Math.max(idxX, idxZ)) {
                    points.add(new int[]{Integer.parseInt(values[idxX].trim()),
                            Integer.parseInt(values[idxZ].trim())});
                }
            }
        } else {
            for (String line : lines) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                String[] parts = line.split("[,\\s]+");
                if (parts.length >= 2) {
                    points.add(new int[]{Integer.parseInt(parts[0].trim()),
                            Integer.parseInt(parts[1].trim())});
                }
            }
        }
    }

    // ---------------------------------------------------------------- 环境

    private static FinderConfig loadConfig() throws IOException {
        return FinderConfig.load(configPath());
    }

    private record Environment(PoolRegistry pools, StructureTemplateManager templates) {
    }

    private static Environment loadEnvironment() {
        StructureTemplateManager templates = new StructureTemplateManager();
        PoolRegistry pools = new PoolRegistry(templates);
        pools.loadAll();
        return new Environment(pools, templates);
    }

    private static Path createOutputPath() {
        String configured = System.getProperty("trialfinder.output");
        if (configured != null && !configured.isBlank()) {
            return ResultFiles.next(Path.of(configured));
        }
        String outputDirectory = System.getProperty("trialfinder.outputDirectory");
        Path directory = outputDirectory == null || outputDirectory.isBlank()
                ? baseDir() : Path.of(outputDirectory);
        return ResultFiles.next(directory, "trial-spawner");
    }
}
