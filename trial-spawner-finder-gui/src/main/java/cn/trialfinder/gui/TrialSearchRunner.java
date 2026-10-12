package cn.trialfinder.gui;

import cn.minecraftfinder.core.AreaShape;
import cn.minecraftfinder.core.ProgressReporter;
import cn.minecraftfinder.core.ProgressUpdate;
import cn.minecraftfinder.core.ResultFiles;
import cn.trialfinder.config.FinderConfig;
import cn.trialfinder.io.ResultWriter;
import cn.trialfinder.model.SearchResult;
import cn.trialfinder.query.CheckTopChecker;
import cn.trialfinder.search.FinderSearch;
import cn.trialfinder.sim.pool.PoolRegistry;
import cn.trialfinder.sim.template.StructureTemplateManager;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * 后台搜索编排：构造 {@link FinderConfig}、调用 {@link FinderSearch}、格式化右侧输出。
 */
public final class TrialSearchRunner {
    public static final String MC_VERSION_LABEL = "1.21.2~26.3";
    public static final String[] SUPPORTED_MC_VERSIONS = {MC_VERSION_LABEL};
    public static final int DEFAULT_SHARD_SIZE_BLOCKS = 262_144;
    public static final int OUTPUT_TOP_N = 100;

    public enum Outcome {
        COMPLETED,
        PAUSED,
        CANCELLED,
        FAILED
    }

    public record SearchBoundsInput(
            boolean squareMode,
            boolean circleMode,
            int minX,
            int maxX,
            int minZ,
            int maxZ,
            int squareSide,
            int circleRadius) {
    }

    public record TrialParams(
            long seed,
            SearchBoundsInput bounds,
            int scanThreads,
            int clusterRadiusBlocks,
            boolean trialCircle,
            int minStructures,
            int minSpawners,
            double slowDiscountRatio) {
    }

    public record FormattedHit(
            long centerX,
            long centerZ,
            int fast,
            int slow,
            double discount,
            int structureCount,
            int vaults) {
        public String toLine() {
            int total = fast + slow;
            return "/tp %d 0 %d %d+%d=%d discount=%.2f structure-count=%d vault=%d"
                    .formatted(centerX, centerZ, fast, slow, total, discount, structureCount, vaults);
        }
    }

    private final AtomicReference<FinderSearch> activeSearch = new AtomicReference<>();
    private final AtomicReference<FinderConfig> activeConfig = new AtomicReference<>();
    private final AtomicReference<Path> activeWorkingDirectory = new AtomicReference<>();
    private volatile boolean running;
    private List<SearchResult> completedResults = List.of();

    private StructureTemplateManager templates;
    private PoolRegistry pools;

    public synchronized void ensureEnvironment() {
        if (templates != null) {
            return;
        }
        templates = new StructureTemplateManager();
        pools = new PoolRegistry(templates);
        pools.loadAll();
    }

    public boolean isRunning() {
        return running;
    }

    public void pause() {
        FinderSearch search = activeSearch.get();
        if (search != null) {
            search.pause();
        }
    }

    public void cancel() {
        FinderSearch search = activeSearch.get();
        if (search != null) {
            search.cancel();
        }
    }

    /** 请求停止当前搜索（检查点由 {@link #discardActiveCheckpoint()} 在结束后清除）。 */
    public void requestStop() {
        cancel();
    }

    /** 删除当前/最近一次配置对应的检查点。 */
    public void discardActiveCheckpoint() {
        FinderConfig config = activeConfig.get();
        Path workingDirectory = activeWorkingDirectory.get();
        if (config != null && workingDirectory != null) {
            try {
                FinderSearch.discardCheckpoint(config, workingDirectory);
            } catch (IOException ignored) {
                // best-effort
            }
        }
    }

    /** @deprecated 使用 {@link #requestStop()} + {@link #discardActiveCheckpoint()} */
    @Deprecated
    public void stopAndDiscardProgress() {
        requestStop();
        discardActiveCheckpoint();
    }

    public List<SearchResult> takeCompletedResults() {
        List<SearchResult> copy = completedResults;
        completedResults = List.of();
        return copy;
    }

    public Outcome runSearch(
            TrialParams params,
            Path workingDirectory,
            Consumer<ProgressUpdate> progressConsumer,
            Consumer<String> statusConsumer) {
        running = true;
        completedResults = List.of();
        try {
            ensureEnvironment();
            FinderConfig config = toConfig(params);
            activeConfig.set(config);
            activeWorkingDirectory.set(workingDirectory);
            Path output = ResultFiles.next(workingDirectory, "trial-spawner");
            ProgressReporter reporter = new ProgressReporter() {
                @Override
                public void report(ProgressUpdate update) {
                    if (progressConsumer != null) {
                        progressConsumer.accept(update);
                    }
                }

                @Override
                public void report(ProgressUpdate update, String details) {
                    report(update);
                    if (statusConsumer != null && details != null && !details.isBlank()) {
                        statusConsumer.accept(details);
                    }
                }
            };
            FinderSearch search = new FinderSearch(
                    config, output, reporter, pools, templates)
                    .setSaveResultsOnComplete(false);
            CheckTopChecker checker = new CheckTopChecker(config.seed(), pools, templates);
            int checkTop = Math.max(OUTPUT_TOP_N, 100);
            search.enableCheckTop(checkTop, results -> checker.check(results, checkTop));
            activeSearch.set(search);
            FinderSearch.RunOutcome outcome = search.run();
            if (outcome == FinderSearch.RunOutcome.COMPLETED) {
                completedResults = List.copyOf(search.results());
            }
            return switch (outcome) {
                case COMPLETED -> Outcome.COMPLETED;
                case PAUSED -> Outcome.PAUSED;
                case CANCELLED -> Outcome.CANCELLED;
            };
        } catch (Exception e) {
            if (statusConsumer != null) {
                statusConsumer.accept("搜索失败：" + e.getMessage());
            }
            e.printStackTrace();
            return Outcome.FAILED;
        } finally {
            activeSearch.set(null);
            running = false;
        }
    }

    /**
     * 将搜索结果导出为 CSV/TXT（与原先自动写出格式一致，含 check-top 列）。
     *
     * @return 写出的 CSV 路径
     */
    public Path exportResults(
            TrialParams params, List<SearchResult> results, Path csvPath) throws IOException {
        if (results == null) {
            results = List.of();
        }
        ensureEnvironment();
        FinderConfig config = toConfig(params);
        int checkTop = Math.max(OUTPUT_TOP_N, 100);
        CheckTopChecker checker = new CheckTopChecker(config.seed(), pools, templates);
        List<ResultWriter.CheckResult> checks = checker.check(results, checkTop);
        List<String> parameterLines = ResultWriter.parameterLines(config, checkTop);
        Path output = ResultFiles.next(csvPath);
        ResultWriter.write(output, results, checks, parameterLines);
        return output;
    }

    public List<FormattedHit> formatResults(TrialParams params, List<SearchResult> results) {
        if (results == null || results.isEmpty()) {
            return List.of();
        }
        ensureEnvironment();
        CheckTopChecker checker = new CheckTopChecker(params.seed(), pools, templates);
        List<ResultWriter.CheckResult> checks = checker.check(results, results.size());
        List<FormattedHit> hits = new ArrayList<>(results.size());
        for (int i = 0; i < results.size(); i++) {
            SearchResult result = results.get(i);
            ResultWriter.CheckResult check = i < checks.size()
                    ? checks.get(i)
                    : new ResultWriter.CheckResult(0, 0, 0);
            double discount = check.fastSpawners()
                    + check.slowSpawners() * params.slowDiscountRatio();
            hits.add(new FormattedHit(
                    result.centerX(),
                    result.centerZ(),
                    check.fastSpawners(),
                    check.slowSpawners(),
                    discount,
                    result.structureCount(),
                    check.vaults()));
        }
        hits.sort(Comparator
                .comparingDouble(FormattedHit::discount).reversed()
                .thenComparingLong(FormattedHit::centerX)
                .thenComparingLong(FormattedHit::centerZ));
        if (hits.size() > OUTPUT_TOP_N) {
            return List.copyOf(hits.subList(0, OUTPUT_TOP_N));
        }
        return List.copyOf(hits);
    }

    public static FinderConfig toConfig(TrialParams params) {
        SearchBoundsInput bounds = params.bounds();
        int centerX;
        int centerZ;
        int radius;
        AreaShape searchShape;
        if (bounds.circleMode()) {
            centerX = 0;
            centerZ = 0;
            radius = bounds.circleRadius();
            searchShape = AreaShape.CIRCLE;
        } else if (bounds.squareMode()) {
            centerX = 0;
            centerZ = 0;
            radius = Math.max(1, bounds.squareSide() / 2);
            searchShape = AreaShape.SQUARE;
        } else {
            centerX = (int) Math.floorDiv((long) bounds.minX() + bounds.maxX(), 2);
            centerZ = (int) Math.floorDiv((long) bounds.minZ() + bounds.maxZ(), 2);
            long dx = Math.max(Math.abs((long) centerX - bounds.minX()),
                    Math.abs((long) bounds.maxX() - centerX));
            long dz = Math.max(Math.abs((long) centerZ - bounds.minZ()),
                    Math.abs((long) bounds.maxZ() - centerZ));
            radius = (int) Math.max(1, Math.max(dx, dz));
            searchShape = AreaShape.SQUARE;
        }
        AreaShape trialShape = params.trialCircle() ? AreaShape.CIRCLE : AreaShape.SQUARE;
        return new FinderConfig(
                params.seed(),
                centerX,
                centerZ,
                radius,
                false,
                searchShape,
                params.clusterRadiusBlocks(),
                trialShape,
                params.minStructures(),
                params.minSpawners(),
                Math.max(1, Math.min(64, params.scanThreads())),
                DEFAULT_SHARD_SIZE_BLOCKS);
    }

    public static double parseRatio(String text) {
        String raw = text == null ? "" : text.trim();
        if (raw.isEmpty()) {
            throw new IllegalArgumentException("折算比例不能为空");
        }
        if (raw.contains("/")) {
            String[] parts = raw.split("/", 2);
            double num = Double.parseDouble(parts[0].trim());
            double den = Double.parseDouble(parts[1].trim());
            if (den == 0) {
                throw new IllegalArgumentException("折算比例分母不能为 0");
            }
            return num / den;
        }
        return Double.parseDouble(raw.replace(',', '.'));
    }

    public static void validateRatio(double ratio) {
        if (ratio < 0.0 || ratio > 1.0) {
            throw new IllegalArgumentException("慢速笼折算比例必须在 0~1（含）");
        }
    }

    public static String formatProgress(ProgressUpdate update) {
        if (update.total() <= 0) {
            return update.phase() + " …";
        }
        double pct = 100.0 * update.completed() / update.total();
        return String.format(Locale.ROOT, "%s %d/%d (%.1f%%)",
                update.phase(), update.completed(), update.total(), pct);
    }

    /**
     * 结果文件与 {@code trial-spawner-checkpoints/} 所在目录。
     * 优先可写的当前工作目录；若落在 System32 等不可用位置，则回退到 jar 目录或用户主目录。
     */
    public static Path resolveWorkDirectory() {
        Path cwd = Path.of("").toAbsolutePath().normalize();
        if (isUsableWorkDirectory(cwd)) {
            return cwd;
        }
        Path self = applicationDirectory();
        if (isUsableWorkDirectory(self)) {
            return self;
        }
        return Path.of(System.getProperty("user.home", ".")).toAbsolutePath().normalize();
    }

    private static Path applicationDirectory() {
        try {
            var codeSource = TrialSearchRunner.class.getProtectionDomain().getCodeSource();
            if (codeSource == null || codeSource.getLocation() == null) {
                return null;
            }
            Path location = Path.of(codeSource.getLocation().toURI()).toAbsolutePath().normalize();
            return Files.isRegularFile(location) ? location.getParent() : location;
        } catch (URISyntaxException | IllegalArgumentException e) {
            return null;
        }
    }

    private static boolean isUsableWorkDirectory(Path directory) {
        if (directory == null || !Files.isDirectory(directory)) {
            return false;
        }
        String path = directory.toString().replace('/', '\\').toLowerCase(Locale.ROOT);
        if (path.endsWith("\\windows\\system32") || path.endsWith("\\windows\\syswow64")) {
            return false;
        }
        return Files.isWritable(directory);
    }
}
