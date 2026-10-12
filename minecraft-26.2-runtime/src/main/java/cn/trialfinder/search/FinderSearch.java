package cn.trialfinder.search;

import cn.minecraftfinder.core.BlockPoint;
import cn.minecraftfinder.core.ProgressReporter;
import cn.minecraftfinder.core.ProgressUpdate;
import cn.trialfinder.config.FinderConfig;
import cn.trialfinder.io.ResultWriter;
import cn.trialfinder.model.SearchResult;
import cn.trialfinder.model.SpawnerPoint;
import cn.trialfinder.model.TrialResultAccumulator;
import cn.trialfinder.sim.pool.PoolRegistry;
import cn.trialfinder.sim.template.StructureTemplateManager;
import cn.trialfinder.sim.world.SimStructureConfig;
import cn.trialfinder.sim.world.TrialChamberPredictor;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

public final class FinderSearch {
    private static final int IN_FLIGHT_TASKS_PER_THREAD = 4;
    private static final int STRUCTURE_TASK_BATCH_SIZE = 16;

    public enum RunOutcome {
        COMPLETED,
        PAUSED,
        CANCELLED
    }

    private final FinderConfig config;
    private final PoolRegistry pools;
    private final StructureTemplateManager templates;
    private Path output;
    private final ProgressReporter progress;
    private final TrialResultAccumulator accumulatedResults = new TrialResultAccumulator();
    private final Map<SearchResult, List<BlockPoint>> resultSources = new HashMap<>();
    private TrialSearchCheckpoint checkpoint;
    private int checkTop;
    private Function<List<SearchResult>, List<ResultWriter.CheckResult>> checkTopChecker;
    private boolean saveResultsOnComplete = true;

    private volatile boolean pauseRequested;
    private volatile boolean cancelRequested;

    public FinderSearch(FinderConfig config, Path output, ProgressReporter progress,
                        PoolRegistry pools, StructureTemplateManager templates) {
        this.config = config;
        this.output = output;
        this.progress = progress;
        this.pools = pools;
        this.templates = templates;
    }

    /** GUI 等场景可关闭完成后自动写 CSV/TXT，改为由调用方手动导出。 */
    public FinderSearch setSaveResultsOnComplete(boolean saveResultsOnComplete) {
        this.saveResultsOnComplete = saveResultsOnComplete;
        return this;
    }

    /** 启用 check-top：统计前 N 个结果的快/慢刷怪笼与宝库数，追加到输出。 */
    public void enableCheckTop(int checkTop,
                               Function<List<SearchResult>, List<ResultWriter.CheckResult>> checker) {
        this.checkTop = checkTop;
        this.checkTopChecker = checker;
    }

    /** 请求在下一分片边界暂停并退出 {@link #run()}，保留检查点。 */
    public void pause() {
        pauseRequested = true;
    }

    /** 请求在下一分片边界停止并退出；由调用方删除检查点。 */
    public void cancel() {
        cancelRequested = true;
    }

    public void resetControl() {
        pauseRequested = false;
        cancelRequested = false;
    }

    public List<SearchResult> results() {
        return accumulatedResults.results();
    }

    public Path outputPath() {
        return output;
    }

    /**
     * 删除与当前配置对应的检查点（停止时清空进度）。
     *
     * @param outputOrDirectory 结果文件路径或其父目录，检查点位于旁的
     *                          {@code trial-spawner-checkpoints/}
     */
    public static void discardCheckpoint(FinderConfig config, Path outputOrDirectory)
            throws IOException {
        TrialSearchCheckpoint.deleteFor(config, outputOrDirectory);
    }

    public RunOutcome run() throws IOException {
        resetControl();
        Instant started = Instant.now();
        System.out.println("使用有界分片流水线扫描并预测试炼密室...");
        System.out.println(ResultWriter.parameterLine(config));
        System.out.println("试炼密室自动分片：边长 %,d 方块；共 %,d 片。".formatted(
                ShardedClusterScanner.processingShardSizeBlocks(config),
                ShardedClusterScanner.processingShardCount(config)));
        try {
            SearchStatistics statistics = execute();
            if (saveResultsOnComplete) {
                save();
            }
            checkpoint.delete();
            System.out.println((
                    "搜索完成：%d 个达标结果；扫描候选 %,d；预测 %,d 座/%,d 组；"
                            + "严格裁剪 %,d 组；耗时 %s。")
                    .formatted(accumulatedResults.results().size(), statistics.scannedCandidates(),
                            statistics.predictedStructures(), statistics.predictedClusters(),
                            statistics.prunedClusters(), elapsed(started)));
            if (saveResultsOnComplete) {
                System.out.println("结果文件：" + output.toAbsolutePath());
                System.out.println("对齐文本：" + ResultWriter.textPath(output).toAbsolutePath());
            }
            return RunOutcome.COMPLETED;
        } catch (SearchPausedException e) {
            System.out.println("搜索已暂停：进度已写入检查点。");
            return RunOutcome.PAUSED;
        } catch (SearchCancelledException e) {
            System.out.println("搜索已停止。");
            return RunOutcome.CANCELLED;
        }
    }

    private SearchStatistics execute() throws IOException {
        checkpoint = TrialSearchCheckpoint.open(config, output);
        output = checkpoint.output();
        checkpoint.results().forEach(accumulatedResults::accept);
        resultSources.putAll(checkpoint.resultSources());
        TrialSearchCheckpoint.Statistics restoredStatistics = checkpoint.statistics();
        if (checkpoint.completedCount() > 0) {
            System.out.println("已恢复检查点：完成 %,d 个分片；已有 %d 条保留结果。".formatted(
                    checkpoint.completedCount(), accumulatedResults.results().size()));
        }
        System.out.println("搜索模式：精确预测排名 + 剪枝");

        int threadCount = searchThreadCount(config);
        int maxInFlight = threadCount * IN_FLIGHT_TASKS_PER_THREAD;
        ThreadLocal<TrialChamberPredictor> predictors =
                ThreadLocal.withInitial(() -> new TrialChamberPredictor(
                        config.seed(), SimStructureConfig.trialChambers(), pools, templates));
        PredictionState predictionState = new PredictionState(restoredStatistics);
        int shardCount = ShardedClusterScanner.processingShardCount(config);
        long estimatedCandidates = ShardedClusterScanner.estimatedCandidateCount(config);

        try (ExecutorService executor = Executors.newFixedThreadPool(threadCount)) {
            try {
                progress.report(
                        ProgressUpdate.estimated(
                                "总进度", checkpoint.completedCount(),
                                shardCount, "个", predictionState.scannedCandidates,
                                estimatedCandidates),
                        status(predictionState, estimatedCandidates));
                ShardedClusterScanner.scanBatches(
                        config,
                        progress,
                        shardIndex -> !checkpoint.isCompleted(shardIndex),
                        batch -> {
                            awaitControl();
                            predictionState.scannedCandidates += batch.candidateCount();
                            predictionState.predictedClusters += batch.clusters().size();
                            BatchResults batchResults = processBatch(
                                    batch, predictors, predictionState,
                                    executor, maxInFlight);
                            mergeBatchResults(batchResults);
                            try {
                                checkpoint.commit(
                                        batch.shardIndex(), accumulatedResults.results(),
                                        resultSources,
                                        statistics(predictionState));
                            } catch (IOException e) {
                                throw new UncheckedIOException(e);
                            }
                            progress.report(
                                    ProgressUpdate.estimated(
                                            "总进度", checkpoint.completedCount(),
                                            batch.shardCount(), "个",
                                            predictionState.scannedCandidates,
                                            estimatedCandidates),
                                    status(predictionState, estimatedCandidates));
                            awaitControl();
                        });
            } catch (SearchPausedException | SearchCancelledException e) {
                throw e;
            } catch (UncheckedIOException e) {
                throw e.getCause();
            }
        }
        awaitControl();
        return new SearchStatistics(
                predictionState.scannedCandidates,
                predictionState.predictedStructures,
                predictionState.predictedClusters,
                predictionState.prunedClusters);
    }

    private void awaitControl() {
        if (cancelRequested) {
            throw new SearchCancelledException();
        }
        if (pauseRequested) {
            throw new SearchPausedException();
        }
    }

    private static String status(PredictionState state, long estimatedCandidates) {
        return formatStatus(
                state.scannedCandidates, estimatedCandidates,
                state.predictedClusters, state.prunedClusters);
    }

    /**
     * 状态栏细节：候选用预估总数；聚类/裁剪按候选进度同比例外推总数，
     * 格式均为 {@code 标签：已完成/总数=百分比%}。
     */
    static String formatStatus(
            long scannedCandidates, long estimatedCandidates,
            long predictedClusters, long prunedClusters) {
        long candidateTotal = Math.max(estimatedCandidates, scannedCandidates);
        long clusterTotal = projectTotal(
                predictedClusters, scannedCandidates, candidateTotal);
        long pruneTotal = projectTotal(
                prunedClusters, scannedCandidates, candidateTotal);
        return formatStatusMetric("候选", scannedCandidates, candidateTotal)
                + "；"
                + formatStatusMetric("聚类", predictedClusters, clusterTotal)
                + "；"
                + formatStatusMetric("裁剪", prunedClusters, pruneTotal);
    }

    /**
     * 按参考进度比例外推总数。参考尚未开始时取已完成数；
     * 外推结果若小于已完成数则抬到已完成数（比例不超过 100%）。
     */
    static long projectTotal(long completed, long referenceCompleted, long referenceTotal) {
        if (referenceCompleted <= 0 || referenceTotal < 0) {
            return Math.max(0, completed);
        }
        long projected = Math.round(
                completed * (double) referenceTotal / (double) referenceCompleted);
        return Math.max(completed, projected);
    }

    /**
     * 状态栏单项：{@code 标签：已完成/总数=百分比%}；
     * {@code total < 0} 表示未知，保持 {@code 标签 已完成}。
     * 已完成超过总数时，把总数抬到已完成数，保证比例不超过 100%。
     */
    static String formatStatusMetric(String label, long completed, long total) {
        if (total < 0) {
            return "%s %,d".formatted(label, completed);
        }
        long effectiveTotal = Math.max(total, completed);
        if (effectiveTotal <= 0) {
            return "%s：0/0=0%%".formatted(label);
        }
        long percent = Math.min(100L, completed * 100L / effectiveTotal);
        return "%s：%,d/%,d=%d%%".formatted(label, completed, effectiveTotal, percent);
    }

    private static TrialSearchCheckpoint.Statistics statistics(
            PredictionState state) {
        return new TrialSearchCheckpoint.Statistics(
                state.scannedCandidates, state.predictedStructures,
                state.predictedClusters, state.prunedClusters);
    }

    private BatchResults processBatch(
            ShardedClusterScanner.ClusterBatch batch,
            ThreadLocal<TrialChamberPredictor> predictors,
            PredictionState predictionState,
            ExecutorService executor,
            int maxInFlight) {
        Set<BlockPoint> requiredStructures = new TreeSet<>();
        batch.clusters().forEach(cluster -> requiredStructures.addAll(cluster.structures()));

        Map<BlockPoint, TrialChamberPredictor.Prediction> predictions =
                predictBounded(requiredStructures, predictors, executor, maxInFlight);
        predictionState.predictedStructures += predictions.size();

        List<CircleClusters.StructureCluster> clustersToRank = new ArrayList<>();
        for (CircleClusters.StructureCluster cluster : batch.clusters()) {
            if (requiresRankingEvaluation(cluster, predictions)) {
                clustersToRank.add(cluster);
            } else {
                predictionState.prunedClusters++;
            }
        }

        TrialResultAccumulator batchResults = new TrialResultAccumulator();
        Map<SearchResult, List<BlockPoint>> sources = new HashMap<>();
        List<SearchResult> evaluated = evaluateClustersBounded(
                clustersToRank,
                cluster -> evaluateCluster(
                        cluster.structures(), point -> predictedSpawners(predictions, point)),
                executor, maxInFlight);
        for (int index = 0; index < evaluated.size(); index++) {
            SearchResult result = evaluated.get(index);
            if (result == null) continue;
            batchResults.accept(result);
            sources.merge(
                    result, clustersToRank.get(index).structures(), FinderSearch::mergePoints);
        }
        return new BatchResults(batchResults.results(), sources);
    }

    private static List<SearchResult> evaluateClustersBounded(
            List<CircleClusters.StructureCluster> clusters,
            Function<CircleClusters.StructureCluster, SearchResult> evaluator,
            ExecutorService executor,
            int maxInFlight) {
        if (clusters.size() <= 1) {
            return clusters.stream().map(evaluator).toList();
        }

        List<SearchResult> results = new ArrayList<>(clusters.size());
        for (int index = 0; index < clusters.size(); index++) {
            results.add(null);
        }
        CompletionService<ClusterEvaluationBatchOutcome> completion =
                new ExecutorCompletionService<>(executor);
        int nextIndex = 0;
        int running = 0;
        try {
            while (nextIndex < clusters.size() || running > 0) {
                while (nextIndex < clusters.size() && running < maxInFlight) {
                    int start = nextIndex;
                    int end = Math.min(clusters.size(), start + STRUCTURE_TASK_BATCH_SIZE);
                    nextIndex = end;
                    completion.submit(() -> evaluateClusterBatch(
                            clusters, start, end, evaluator));
                    running++;
                }
                ClusterEvaluationBatchOutcome batch = completion.take().get();
                running--;
                if (batch.failure() != null) throw batch.failure();
                for (ClusterEvaluationOutcome outcome : batch.results()) {
                    results.set(outcome.index(), outcome.result());
                }
            }
            return results;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("试炼密室聚类评估被中断", e);
        } catch (java.util.concurrent.ExecutionException e) {
            throw new IllegalStateException("试炼密室聚类评估任务异常退出", e.getCause());
        }
    }

    private static ClusterEvaluationBatchOutcome evaluateClusterBatch(
            List<CircleClusters.StructureCluster> clusters,
            int start,
            int end,
            Function<CircleClusters.StructureCluster, SearchResult> evaluator) {
        List<ClusterEvaluationOutcome> results = new ArrayList<>(end - start);
        for (int index = start; index < end; index++) {
            try {
                results.add(new ClusterEvaluationOutcome(index, evaluator.apply(clusters.get(index))));
            } catch (RuntimeException e) {
                return new ClusterEvaluationBatchOutcome(List.of(), e);
            }
        }
        return new ClusterEvaluationBatchOutcome(List.copyOf(results), null);
    }

    private boolean requiresRankingEvaluation(
            CircleClusters.StructureCluster cluster,
            Map<BlockPoint, TrialChamberPredictor.Prediction> predictions) {
        long upperBound = 0;
        for (BlockPoint point : cluster.structures()) {
            TrialChamberPredictor.Prediction prediction = predictions.get(point);
            if (prediction == null) return true;
            upperBound = Math.min(Integer.MAX_VALUE,
                    upperBound + prediction.theoreticalSpawners().size());
        }
        if (upperBound < config.minSpawners()) return false;
        return !accumulatedResults.canDiscardUpperBound(
                (int) upperBound, config.minStructures(), cluster.structures().size());
    }

    SearchResult evaluateCluster(
            List<BlockPoint> candidates,
            Function<BlockPoint, List<SpawnerPoint>> spawnerLookup) {
        List<BlockPoint> structures = new ArrayList<>();
        Set<SpawnerPoint> spawners = new TreeSet<>();
        for (BlockPoint candidate : candidates) {
            List<SpawnerPoint> chamberSpawners = spawnerLookup.apply(candidate);
            if (chamberSpawners.isEmpty()) continue;
            structures.add(candidate);
            spawners.addAll(chamberSpawners);
        }
        if (structures.size() < config.minStructures()) return null;
        structures.sort(BlockPoint::compareTo);
        ExactCenterOptimizer.CenterScore score = ExactCenterOptimizer.find(
                config.areaShape(), config.clusterRadiusBlocks(), structures, spawners);
        if (score.spawners() < config.minSpawners()) return null;
        return new SearchResult(
                score.x(), score.z(), structures.size(), score.spawners(), structures);
    }

    private void mergeBatchResults(BatchResults batch) {
        for (SearchResult result : batch.results()) {
            accumulatedResults.accept(result);
            List<BlockPoint> source = batch.sources().get(result);
            if (source != null) {
                resultSources.merge(result, source, FinderSearch::mergePoints);
            }
        }
        Set<SearchResult> retained = new HashSet<>(accumulatedResults.results());
        resultSources.keySet().retainAll(retained);
    }

    private static List<SpawnerPoint> predictedSpawners(
            Map<BlockPoint, TrialChamberPredictor.Prediction> predictions,
            BlockPoint point) {
        TrialChamberPredictor.Prediction prediction = predictions.get(point);
        if (prediction == null) {
            throw new IllegalStateException("缺少密室快速布局结果: " + point);
        }
        return prediction.actualSpawners();
    }

    private static List<BlockPoint> mergePoints(
            List<BlockPoint> first, List<BlockPoint> second) {
        TreeSet<BlockPoint> merged = new TreeSet<>(first);
        merged.addAll(second);
        return List.copyOf(merged);
    }

    private static Map<BlockPoint, TrialChamberPredictor.Prediction> predictBounded(
            Set<BlockPoint> requiredStructures,
            ThreadLocal<TrialChamberPredictor> predictors,
            ExecutorService executor,
            int maxInFlight) {
        Map<BlockPoint, TrialChamberPredictor.Prediction> predictions =
                new HashMap<>(requiredStructures.size());
        CompletionService<PredictionBatchOutcome> completion =
                new ExecutorCompletionService<>(executor);
        Iterator<BlockPoint> points = requiredStructures.iterator();
        int running = 0;
        try {
            while (points.hasNext() || running > 0) {
                while (points.hasNext() && running < maxInFlight) {
                    List<BlockPoint> batch = nextBatch(points);
                    completion.submit(() -> predictBatch(batch, predictors));
                    running++;
                }
                PredictionBatchOutcome outcome = completion.take().get();
                running--;
                if (outcome.failure() != null) {
                    throw outcome.failure();
                }
                for (PredictionOutcome prediction : outcome.predictions()) {
                    predictions.put(prediction.point(), prediction.prediction());
                }
            }
            return predictions;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("试炼密室快速布局被中断", e);
        } catch (java.util.concurrent.ExecutionException e) {
            throw new IllegalStateException("试炼密室快速布局任务异常退出", e.getCause());
        }
    }

    private static List<BlockPoint> nextBatch(Iterator<BlockPoint> points) {
        List<BlockPoint> batch = new ArrayList<>(STRUCTURE_TASK_BATCH_SIZE);
        while (points.hasNext() && batch.size() < STRUCTURE_TASK_BATCH_SIZE) {
            batch.add(points.next());
        }
        return List.copyOf(batch);
    }

    private static PredictionBatchOutcome predictBatch(
            List<BlockPoint> points,
            ThreadLocal<TrialChamberPredictor> predictors) {
        List<PredictionOutcome> predictions = new ArrayList<>(points.size());
        TrialChamberPredictor predictor = predictors.get();
        for (BlockPoint point : points) {
            try {
                predictions.add(new PredictionOutcome(point, predictor.predict(point)));
            } catch (RuntimeException e) {
                return new PredictionBatchOutcome(
                        List.of(), new IllegalStateException(
                                "密室 %d,%d 快速布局失败: %s".formatted(
                                        point.x(), point.z(), e.getMessage())));
            }
        }
        return new PredictionBatchOutcome(List.copyOf(predictions), null);
    }

    public synchronized void save() throws IOException {
        List<SearchResult> results = accumulatedResults.results();
        List<String> parameterLines = ResultWriter.parameterLines(config, this.checkTop);
        if (this.checkTop > 0 && this.checkTopChecker != null) {
            List<ResultWriter.CheckResult> checks = this.checkTopChecker.apply(results);
            ResultWriter.write(output, results, checks, parameterLines);
        } else {
            ResultWriter.write(output, results, null, parameterLines);
        }
    }

    static int searchThreadCount(FinderConfig config) {
        int configured = config.scanThreads();
        return Math.max(1, Math.min(64, configured));
    }

    static int fineThreadCount(int availableProcessors) {
        return Math.max(1, availableProcessors - 2);
    }

    static int maxInFlightTasks(int availableProcessors) {
        return fineThreadCount(availableProcessors) * IN_FLIGHT_TASKS_PER_THREAD;
    }

    private static String elapsed(Instant started) {
        Duration duration = Duration.between(started, Instant.now());
        return "%02d:%02d:%02d".formatted(
                duration.toHours(), duration.toMinutesPart(), duration.toSecondsPart());
    }

    private record BatchResults(
            List<SearchResult> results,
            Map<SearchResult, List<BlockPoint>> sources) {
    }

    private record PredictionOutcome(
            BlockPoint point,
            TrialChamberPredictor.Prediction prediction) {
    }

    private record PredictionBatchOutcome(
            List<PredictionOutcome> predictions, RuntimeException failure) {
    }

    private record ClusterEvaluationOutcome(
            int index, SearchResult result) {
    }

    private record ClusterEvaluationBatchOutcome(
            List<ClusterEvaluationOutcome> results, RuntimeException failure) {
    }

    private static final class PredictionState {
        private long scannedCandidates;
        private long predictedStructures;
        private long predictedClusters;
        private long prunedClusters;

        private PredictionState(TrialSearchCheckpoint.Statistics statistics) {
            scannedCandidates = statistics.scannedCandidates();
            predictedStructures = statistics.predictedStructures();
            predictedClusters = statistics.predictedClusters();
            prunedClusters = statistics.prunedClusters();
        }
    }

    private record SearchStatistics(
            long scannedCandidates, long predictedStructures, long predictedClusters,
            long prunedClusters) {
    }

    /** 暂停退出：检查点已提交，不删除。 */
    public static final class SearchPausedException extends RuntimeException {
        public SearchPausedException() {
            super("搜索已暂停");
        }
    }

    /** 停止退出：由调用方删除检查点。 */
    public static final class SearchCancelledException extends RuntimeException {
        public SearchCancelledException() {
            super("搜索已停止");
        }
    }
}
