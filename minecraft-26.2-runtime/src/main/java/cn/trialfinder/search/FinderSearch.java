package cn.trialfinder.search;

import cn.minecraftfinder.core.BlockPoint;
import cn.minecraftfinder.core.ProgressReporter;
import cn.minecraftfinder.core.ProgressUpdate;
import cn.trialfinder.config.FinderConfig;
import cn.trialfinder.config.TrialSearchMode;
import cn.trialfinder.io.ResultWriter;
import cn.trialfinder.model.SearchResult;
import cn.trialfinder.model.SpawnerPoint;
import cn.trialfinder.model.TrialResultAccumulator;
import cn.trialfinder.world.TrialChamberGenerator;
import cn.trialfinder.world.HandTrialChamberPredictor;
import net.minecraft.server.level.ServerLevel;

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
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class FinderSearch {
    private static final int IN_FLIGHT_TASKS_PER_THREAD = 4;
    private static final int STRUCTURE_TASK_BATCH_SIZE = 16;
    private static final int PRUNED_AUDIT_INTERVAL = 10_000;

    private final FinderConfig config;
    private Path output;
    private final ProgressReporter progress;
    private final TrialResultAccumulator accumulatedResults = new TrialResultAccumulator();
    private final Map<SearchResult, List<BlockPoint>> resultSources = new HashMap<>();
    private TrialSearchCheckpoint checkpoint;

    public FinderSearch(FinderConfig config, Path output, ProgressReporter progress) {
        this.config = config;
        this.output = output;
        this.progress = progress;
    }

    public void run(ServerLevel world) throws IOException {
        Instant started = Instant.now();
        System.out.println("使用有界分片流水线扫描并验证试炼密室...");
        System.out.println("JVM 可用逻辑处理器：%d；快速扫描线程：%d；精细生成线程：%d".formatted(
                Runtime.getRuntime().availableProcessors(), config.scanThreads(),
                fineThreadCount(Runtime.getRuntime().availableProcessors())));
        System.out.println("试炼密室自动分片：边长 %,d 方块；共 %,d 片。".formatted(
                ShardedClusterScanner.processingShardSizeBlocks(config),
                ShardedClusterScanner.processingShardCount(config)));
        boolean usePrediction = config.searchMode() == TrialSearchMode.AUTO;
        SearchStatistics statistics;
        try {
            statistics = execute(world, usePrediction);
        } catch (PredictionMismatchException e) {
            System.err.println("快速布局与原版不一致，自动切换到有界全量验证：" + e.getMessage());
            checkpoint.delete();
            accumulatedResults.clear();
            resultSources.clear();
            statistics = execute(world, false);
        }

        save();
        checkpoint.delete();
        System.out.println((
                "搜索完成：%d 个达标结果；扫描候选 %,d；预测 %,d 座/%,d 组；"
                        + "严格裁剪 %,d 组；原版生成 %,d 座；耗时 %s。")
                .formatted(accumulatedResults.results().size(), statistics.scannedCandidates(),
                        statistics.predictedStructures(), statistics.predictedClusters(),
                        statistics.prunedClusters(), statistics.verifiedStructures(), elapsed(started)));
        System.out.println("结果文件：" + output.toAbsolutePath());
        System.out.println("对齐文本：" + ResultWriter.textPath(output).toAbsolutePath());
    }

    private SearchStatistics execute(ServerLevel world, boolean usePrediction) throws IOException {
        checkpoint = TrialSearchCheckpoint.open(config, output, usePrediction);
        output = checkpoint.output();
        usePrediction = checkpoint.predictionEnabled();
        checkpoint.results().forEach(accumulatedResults::accept);
        resultSources.putAll(checkpoint.resultSources());
        TrialSearchCheckpoint.Statistics restoredStatistics = checkpoint.statistics();
        if (checkpoint.completedCount() > 0) {
            System.out.println("已恢复检查点：完成 %,d 个分片；已有 %d 条保留结果。".formatted(
                    checkpoint.completedCount(), accumulatedResults.results().size()));
        }
        System.out.println(usePrediction
                ? "搜索模式：auto（精确预测排名 + 最终榜单原版复核）"
                : "搜索模式：exact（有界全量原版验证）");

        int threadCount = fineThreadCount(Runtime.getRuntime().availableProcessors());
        int maxInFlight = threadCount * IN_FLIGHT_TASKS_PER_THREAD;
        ThreadLocal<TrialChamberGenerator> generators =
                ThreadLocal.withInitial(() -> new TrialChamberGenerator(world));
        ThreadLocal<HandTrialChamberPredictor> predictors = usePrediction
                ? ThreadLocal.withInitial(() -> new HandTrialChamberPredictor(world)) : null;
        PredictionState predictionState = new PredictionState(
                restoredStatistics, config.predictionCalibrationStructures());
        long[] verifiedStructures = {restoredStatistics.verifiedStructures()};
        int shardCount = ShardedClusterScanner.processingShardCount(config);
        long estimatedCandidates = ShardedClusterScanner.estimatedCandidateCount(config);

        try (ExecutorService executor = Executors.newFixedThreadPool(threadCount)) {
            try {
                progress.report(
                        ProgressUpdate.estimated(
                                "总进度", checkpoint.completedCount(),
                                shardCount, "个", predictionState.scannedCandidates,
                                estimatedCandidates),
                        status(predictionState, verifiedStructures[0]));
                ShardedClusterScanner.scanBatches(
                        config,
                        progress,
                        shardIndex -> !checkpoint.isCompleted(shardIndex),
                        batch -> {
                            predictionState.scannedCandidates += batch.candidateCount();
                            if (predictors != null) {
                                predictionState.predictedClusters += batch.clusters().size();
                            }
                            BatchResults batchResults = processBatch(
                                    batch, generators, predictors, predictionState,
                                    executor, maxInFlight, verifiedStructures);
                            mergeBatchResults(batchResults);
                            try {
                                checkpoint.commit(
                                        batch.shardIndex(), accumulatedResults.results(),
                                        resultSources,
                                        statistics(predictionState, verifiedStructures[0]));
                            } catch (IOException e) {
                                throw new UncheckedIOException(e);
                            }
                            progress.report(
                                    ProgressUpdate.estimated(
                                            "总进度", checkpoint.completedCount(),
                                            batch.shardCount(), "个",
                                            predictionState.scannedCandidates,
                                            estimatedCandidates),
                                    status(predictionState, verifiedStructures[0]));
                        });
                if (predictors != null) {
                    verifyFinalResults(
                            generators, executor, maxInFlight, verifiedStructures);
                }
            } catch (UncheckedIOException e) {
                throw e.getCause();
            }
        }
        return new SearchStatistics(
                predictionState.scannedCandidates,
                predictionState.predictedStructures,
                predictionState.predictedClusters,
                predictionState.prunedClusters,
                verifiedStructures[0]);
    }

    private static String status(PredictionState state, long verifiedStructures) {
        return "候选 %,d；聚类 %,d；裁剪 %,d；原版验证 %,d 座"
                .formatted(state.scannedCandidates, state.predictedClusters,
                        state.prunedClusters, verifiedStructures);
    }

    private static TrialSearchCheckpoint.Statistics statistics(
            PredictionState state, long verifiedStructures) {
        return new TrialSearchCheckpoint.Statistics(
                state.scannedCandidates, state.predictedStructures,
                state.predictedClusters, state.prunedClusters, verifiedStructures);
    }

    private BatchResults processBatch(
            ShardedClusterScanner.ClusterBatch batch,
            ThreadLocal<TrialChamberGenerator> generators,
            ThreadLocal<HandTrialChamberPredictor> predictors,
            PredictionState predictionState,
            ExecutorService executor,
            int maxInFlight,
            long[] verifiedStructures) {
        Set<BlockPoint> requiredStructures = new TreeSet<>();
        batch.clusters().forEach(cluster -> requiredStructures.addAll(cluster.structures()));
        if (predictors == null) {
            Map<BlockPoint, TrialChamberGenerator.GeneratedChamber> generated = generateBounded(
                    requiredStructures, generators, executor, maxInFlight);
            verifiedStructures[0] += requiredStructures.size();
            TrialResultAccumulator batchResults = new TrialResultAccumulator();
            List<SearchResult> evaluated = evaluateClustersBounded(
                    batch.clusters(),
                    cluster -> evaluateCluster(
                            cluster.structures(), point -> generatedSpawners(generated, point)),
                    executor, maxInFlight);
            for (SearchResult result : evaluated) {
                if (result != null) batchResults.accept(result);
            }
            return new BatchResults(batchResults.results(), Map.of());
        }

        Map<BlockPoint, HandTrialChamberPredictor.Prediction> predictions =
                predictBounded(requiredStructures, predictors, executor, maxInFlight);
        predictionState.predictedStructures += predictions.size();

        List<CircleClusters.StructureCluster> clustersToRank = new ArrayList<>();
        Set<BlockPoint> structuresToAudit = new TreeSet<>();
        addCalibrationStructures(requiredStructures, structuresToAudit, predictionState);
        for (CircleClusters.StructureCluster cluster : batch.clusters()) {
            if (requiresRankingEvaluation(cluster, predictions)) {
                clustersToRank.add(cluster);
            } else {
                predictionState.prunedClusters++;
                if (predictionState.prunedClusters % PRUNED_AUDIT_INTERVAL == 0) {
                    structuresToAudit.addAll(cluster.structures());
                }
            }
        }

        Map<BlockPoint, TrialChamberGenerator.GeneratedChamber> audited = generateBounded(
                structuresToAudit, generators, executor, maxInFlight);
        verifiedStructures[0] += structuresToAudit.size();
        comparePredictions(predictions, audited, predictionState);

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
            Map<BlockPoint, HandTrialChamberPredictor.Prediction> predictions) {
        long upperBound = 0;
        for (BlockPoint point : cluster.structures()) {
            HandTrialChamberPredictor.Prediction prediction = predictions.get(point);
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

    private void verifyFinalResults(
            ThreadLocal<TrialChamberGenerator> generators,
            ExecutorService executor,
            int maxInFlight,
            long[] verifiedStructures) {
        List<SearchResult> expected = accumulatedResults.results();
        Set<BlockPoint> requiredStructures = new TreeSet<>();
        for (SearchResult result : expected) {
            List<BlockPoint> source = resultSources.get(result);
            if (source == null) {
                throw new PredictionMismatchException("临时榜单缺少完整候选结构列表");
            }
            requiredStructures.addAll(source);
        }

        System.out.println("正在用原版复核最终榜单：%,d 条结果，%,d 座候选密室。".formatted(
                expected.size(), requiredStructures.size()));
        int totalWork = requiredStructures.size() + expected.size();
        int[] generatedCount = {0};
        int[] rankedCount = {0};
        reportFinalVerificationProgress(
                generatedCount[0], requiredStructures.size(),
                rankedCount[0], expected.size(), totalWork);
        Map<BlockPoint, TrialChamberGenerator.GeneratedChamber> generated = generateBounded(
                requiredStructures, generators, executor, maxInFlight, completed -> {
                    generatedCount[0] += completed;
                    reportFinalVerificationProgress(
                            generatedCount[0], requiredStructures.size(),
                            rankedCount[0], expected.size(), totalWork);
                });
        verifiedStructures[0] += requiredStructures.size();

        TrialResultAccumulator verified = new TrialResultAccumulator();
        for (SearchResult predicted : expected) {
            SearchResult actual = evaluateCluster(
                    resultSources.get(predicted), point -> generatedSpawners(generated, point));
            if (!predicted.equals(actual)) {
                throw new PredictionMismatchException(
                        "最终榜单候选的原版结果与快速布局不一致: " + predicted.structures());
            }
            verified.accept(actual);
            rankedCount[0]++;
            reportFinalVerificationProgress(
                    generatedCount[0], requiredStructures.size(),
                    rankedCount[0], expected.size(), totalWork);
        }
        if (!expected.equals(verified.results())) {
            throw new PredictionMismatchException("最终榜单的原版排名与快速排名不一致");
        }
        accumulatedResults.clear();
        verified.results().forEach(accumulatedResults::accept);
        resultSources.clear();
    }

    private void reportFinalVerificationProgress(
            int generated, int structureTotal, int ranked, int resultTotal, int totalWork) {
        progress.report(
                ProgressUpdate.phase(
                        "最终复核", generated + ranked, totalWork, "项"),
                "原版生成 %,d/%,d；榜单验证 %,d/%,d"
                        .formatted(generated, structureTotal, ranked, resultTotal));
    }

    private static void addCalibrationStructures(
            Set<BlockPoint> requiredStructures,
            Set<BlockPoint> structuresToAudit,
            PredictionState state) {
        long remaining = state.calibrationLimit - state.comparedStructures;
        if (remaining <= 0) return;
        for (BlockPoint point : requiredStructures) {
            structuresToAudit.add(point);
            if (--remaining == 0) return;
        }
    }

    private static List<SpawnerPoint> predictedSpawners(
            Map<BlockPoint, HandTrialChamberPredictor.Prediction> predictions,
            BlockPoint point) {
        HandTrialChamberPredictor.Prediction prediction = predictions.get(point);
        if (prediction == null) throw new PredictionMismatchException(point);
        return prediction.actualSpawners();
    }

    private static List<SpawnerPoint> generatedSpawners(
            Map<BlockPoint, TrialChamberGenerator.GeneratedChamber> generated,
            BlockPoint point) {
        TrialChamberGenerator.GeneratedChamber chamber = generated.get(point);
        if (chamber == null) {
            throw new IllegalStateException("缺少密室原版生成结果: " + point);
        }
        return chamber.spawners();
    }

    private static List<BlockPoint> mergePoints(
            List<BlockPoint> first, List<BlockPoint> second) {
        TreeSet<BlockPoint> merged = new TreeSet<>(first);
        merged.addAll(second);
        return List.copyOf(merged);
    }

    private static void comparePredictions(
            Map<BlockPoint, HandTrialChamberPredictor.Prediction> predictions,
            Map<BlockPoint, TrialChamberGenerator.GeneratedChamber> generated,
            PredictionState state) {
        for (Map.Entry<BlockPoint, TrialChamberGenerator.GeneratedChamber> entry : generated.entrySet()) {
            HandTrialChamberPredictor.Prediction prediction = predictions.get(entry.getKey());
            TrialChamberGenerator.GeneratedChamber actual = entry.getValue();
            if (prediction == null
                    || prediction.exists() != actual.exists()
                    || !prediction.actualSpawners().equals(actual.spawners())) {
                throw new PredictionMismatchException(entry.getKey());
            }
            state.comparedStructures++;
        }
    }

    private static Map<BlockPoint, HandTrialChamberPredictor.Prediction> predictBounded(
            Set<BlockPoint> requiredStructures,
            ThreadLocal<HandTrialChamberPredictor> predictors,
            ExecutorService executor,
            int maxInFlight) {
        Map<BlockPoint, HandTrialChamberPredictor.Prediction> predictions =
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
            throw new PredictionMismatchException("试炼密室快速布局任务异常退出");
        }
    }

    private static Map<BlockPoint, TrialChamberGenerator.GeneratedChamber> generateBounded(
            Set<BlockPoint> requiredStructures,
            ThreadLocal<TrialChamberGenerator> generators,
            ExecutorService executor,
            int maxInFlight) {
        return generateBounded(
                requiredStructures, generators, executor, maxInFlight, ignored -> { });
    }

    private static Map<BlockPoint, TrialChamberGenerator.GeneratedChamber> generateBounded(
            Set<BlockPoint> requiredStructures,
            ThreadLocal<TrialChamberGenerator> generators,
            ExecutorService executor,
            int maxInFlight,
            IntConsumer completed) {
        Map<BlockPoint, TrialChamberGenerator.GeneratedChamber> generated =
                new HashMap<>(requiredStructures.size());
        List<BlockPoint> failed = new ArrayList<>();
        CompletionService<GenerationBatchOutcome> completion =
                new ExecutorCompletionService<>(executor);
        Iterator<BlockPoint> points = requiredStructures.iterator();
        int running = 0;

        try {
            while (points.hasNext() || running > 0) {
                while (points.hasNext() && running < maxInFlight) {
                    List<BlockPoint> batch = nextBatch(points);
                    completion.submit(() -> generateBatch(batch, generators));
                    running++;
                }
                GenerationBatchOutcome outcome = completion.take().get();
                running--;
                int successful = 0;
                for (GenerationOutcome generation : outcome.generations()) {
                    if (generation.failure() == null) {
                        generated.put(generation.point(), generation.chamber());
                        successful++;
                    } else {
                        failed.add(generation.point());
                    }
                }
                if (successful > 0) completed.accept(successful);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("试炼密室生成被中断", e);
        } catch (java.util.concurrent.ExecutionException e) {
            throw new IllegalStateException("试炼密室生成任务异常退出", e.getCause());
        }

        if (!failed.isEmpty()) {
            System.out.println("检测到 %d 座密室发生并发生成异常，正在单线程重试...".formatted(
                    failed.size()));
            TrialChamberGenerator retryGenerator = generators.get();
            for (BlockPoint point : failed) {
                try {
                    generated.put(point, retryGenerator.generate(point));
                    completed.accept(1);
                } catch (RuntimeException e) {
                    throw new IllegalStateException(
                            "密室 %d,%d 串行重试仍然失败".formatted(point.x(), point.z()), e);
                }
            }
        }
        return generated;
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
            ThreadLocal<HandTrialChamberPredictor> predictors) {
        List<PredictionOutcome> predictions = new ArrayList<>(points.size());
        HandTrialChamberPredictor predictor = predictors.get();
        for (BlockPoint point : points) {
            try {
                predictions.add(new PredictionOutcome(point, predictor.predict(point)));
            } catch (RuntimeException e) {
                return new PredictionBatchOutcome(
                        List.of(), new PredictionMismatchException(
                                "密室 %d,%d 快速布局失败: %s".formatted(
                                        point.x(), point.z(), e.getMessage())));
            }
        }
        return new PredictionBatchOutcome(List.copyOf(predictions), null);
    }

    private static GenerationBatchOutcome generateBatch(
            List<BlockPoint> points,
            ThreadLocal<TrialChamberGenerator> generators) {
        List<GenerationOutcome> generations = new ArrayList<>(points.size());
        TrialChamberGenerator generator = generators.get();
        for (BlockPoint point : points) {
            generations.add(generate(generator, point));
        }
        return new GenerationBatchOutcome(List.copyOf(generations));
    }

    private static GenerationOutcome generate(
            TrialChamberGenerator generator, BlockPoint point) {
        try {
            return new GenerationOutcome(point, generator.generate(point), null);
        } catch (RuntimeException e) {
            return new GenerationOutcome(point, null, e);
        }
    }

    public synchronized void save() throws IOException {
        ResultWriter.write(output, accumulatedResults.results());
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

    private record GenerationOutcome(
            BlockPoint point,
            TrialChamberGenerator.GeneratedChamber chamber,
            RuntimeException failure) {
    }

    private record PredictionOutcome(
            BlockPoint point,
            HandTrialChamberPredictor.Prediction prediction) {
    }

    private record PredictionBatchOutcome(
            List<PredictionOutcome> predictions, RuntimeException failure) {
    }

    private record GenerationBatchOutcome(List<GenerationOutcome> generations) {
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
        private long comparedStructures;
        private long prunedClusters;
        private final long calibrationLimit;

        private PredictionState(
                TrialSearchCheckpoint.Statistics statistics, long calibrationLimit) {
            scannedCandidates = statistics.scannedCandidates();
            predictedStructures = statistics.predictedStructures();
            predictedClusters = statistics.predictedClusters();
            prunedClusters = statistics.prunedClusters();
            this.calibrationLimit = calibrationLimit;
        }
    }

    private record SearchStatistics(
            long scannedCandidates, long predictedStructures, long predictedClusters,
            long prunedClusters, long verifiedStructures) {
    }

    private static final class PredictionMismatchException extends RuntimeException {
        private PredictionMismatchException(BlockPoint point) {
            this("密室 %d,%d 的存在性或刷怪笼坐标不一致".formatted(
                    point.x(), point.z()));
        }

        private PredictionMismatchException(String message) {
            super(message);
        }
    }
}
