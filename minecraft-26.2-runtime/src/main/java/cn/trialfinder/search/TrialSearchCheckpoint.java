package cn.trialfinder.search;

import cn.minecraftfinder.core.BlockPoint;
import cn.trialfinder.config.FinderConfig;
import cn.trialfinder.model.SearchResult;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

final class TrialSearchCheckpoint {
    private static final int MAGIC = 0x54534631;
    private static final int FORMAT_VERSION = 5;
    private static final String TEMPLATE_FINGERPRINT = "vanilla-trial-chambers-26.2";

    private final Path path;
    private final String fingerprint;
    private final Path output;
    private final boolean predictionEnabled;
    private final BitSet completedShards;
    private List<SearchResult> results;
    private Map<SearchResult, List<BlockPoint>> resultSources;
    private Statistics statistics;

    private TrialSearchCheckpoint(
            Path path, String fingerprint, Path output,
            boolean predictionEnabled, BitSet completedShards,
            List<SearchResult> results,
            Map<SearchResult, List<BlockPoint>> resultSources,
            Statistics statistics) {
        this.path = path;
        this.fingerprint = fingerprint;
        this.output = output;
        this.predictionEnabled = predictionEnabled;
        this.completedShards = completedShards;
        this.results = List.copyOf(results);
        this.resultSources = immutableSources(resultSources);
        this.statistics = statistics;
    }

    static TrialSearchCheckpoint open(
            FinderConfig config, Path requestedOutput, boolean requestedPrediction) throws IOException {
        return open(config, requestedOutput, requestedPrediction,
                Path.of("run", "checkpoints", "trial-spawner"));
    }

    static TrialSearchCheckpoint open(
            FinderConfig config, Path requestedOutput, boolean requestedPrediction,
            Path checkpointDirectory) throws IOException {
        String fingerprint = fingerprint(config);
        Path path = checkpointDirectory.resolve(fingerprint + ".bin");
        if (!Files.exists(path)) {
            return new TrialSearchCheckpoint(
                    path, fingerprint, requestedOutput, requestedPrediction,
                    new BitSet(), List.of(), Map.of(), Statistics.EMPTY);
        }
        return read(path, fingerprint);
    }

    Path output() {
        return output;
    }

    List<SearchResult> results() {
        return results;
    }

    Map<SearchResult, List<BlockPoint>> resultSources() {
        return resultSources;
    }

    boolean predictionEnabled() {
        return predictionEnabled;
    }

    Statistics statistics() {
        return statistics;
    }

    boolean isCompleted(int shardIndex) {
        return completedShards.get(shardIndex);
    }

    int completedCount() {
        return completedShards.cardinality();
    }

    synchronized void commit(
            int shardIndex, List<SearchResult> currentResults,
            Map<SearchResult, List<BlockPoint>> currentResultSources,
            Statistics currentStatistics) throws IOException {
        completedShards.set(shardIndex);
        results = List.copyOf(currentResults);
        resultSources = immutableSources(currentResultSources);
        statistics = currentStatistics;
        write();
    }

    void delete() throws IOException {
        Files.deleteIfExists(path);
    }

    private void write() throws IOException {
        Files.createDirectories(path.getParent());
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        try (DataOutputStream outputStream = new DataOutputStream(
                new BufferedOutputStream(Files.newOutputStream(temporary)))) {
            outputStream.writeInt(MAGIC);
            outputStream.writeInt(FORMAT_VERSION);
            outputStream.writeUTF(fingerprint);
            outputStream.writeUTF(output.toString());
            outputStream.writeBoolean(predictionEnabled);
            long[] completed = completedShards.toLongArray();
            outputStream.writeInt(completed.length);
            for (long value : completed) outputStream.writeLong(value);
            outputStream.writeInt(results.size());
            for (SearchResult result : results) {
                writeResult(outputStream, result);
                writePoints(outputStream, resultSources.getOrDefault(result, List.of()));
            }
            outputStream.writeLong(statistics.scannedCandidates());
            outputStream.writeLong(statistics.predictedStructures());
            outputStream.writeLong(statistics.predictedClusters());
            outputStream.writeLong(statistics.prunedClusters());
            outputStream.writeLong(statistics.verifiedStructures());
        }
        try {
            Files.move(temporary, path,
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static TrialSearchCheckpoint read(Path path, String expectedFingerprint) throws IOException {
        try (DataInputStream input = new DataInputStream(
                new BufferedInputStream(Files.newInputStream(path)))) {
            if (input.readInt() != MAGIC || input.readInt() != FORMAT_VERSION) {
                throw new IOException("试炼刷怪笼检查点格式不受支持: " + path);
            }
            String fingerprint = input.readUTF();
            if (!fingerprint.equals(expectedFingerprint)) {
                throw new IOException("试炼刷怪笼检查点配置指纹不匹配: " + path);
            }
            Path output = Path.of(input.readUTF());
            boolean predictionEnabled = input.readBoolean();
            int wordCount = input.readInt();
            if (wordCount < 0 || wordCount > 1_000_000) {
                throw new IOException("试炼刷怪笼检查点分片数据损坏: " + path);
            }
            long[] completed = new long[wordCount];
            for (int index = 0; index < wordCount; index++) completed[index] = input.readLong();
            int resultCount = input.readInt();
            if (resultCount < 0 || resultCount > 1_000_000) {
                throw new IOException("试炼刷怪笼检查点结果数据损坏: " + path);
            }
            List<SearchResult> results = new ArrayList<>(resultCount);
            Map<SearchResult, List<BlockPoint>> resultSources = new HashMap<>();
            for (int index = 0; index < resultCount; index++) {
                SearchResult result = readResult(input);
                results.add(result);
                List<BlockPoint> source = readPoints(input, "候选结构坐标");
                if (!source.isEmpty()) resultSources.put(result, source);
            }
            Statistics statistics = new Statistics(
                    input.readLong(), input.readLong(), input.readLong(),
                    input.readLong(), input.readLong());
            return new TrialSearchCheckpoint(
                    path, fingerprint, output, predictionEnabled,
                    BitSet.valueOf(completed), results, resultSources, statistics);
        }
    }

    private static void writeResult(DataOutputStream output, SearchResult result) throws IOException {
        output.writeLong(result.centerX());
        output.writeLong(result.centerZ());
        output.writeInt(result.structureCount());
        output.writeInt(result.spawnerCount());
        output.writeInt(result.structures().size());
        for (BlockPoint structure : result.structures()) {
            output.writeInt(structure.x());
            output.writeInt(structure.z());
        }
    }

    private static void writePoints(DataOutputStream output, List<BlockPoint> points)
            throws IOException {
        output.writeInt(points.size());
        for (BlockPoint point : points) {
            output.writeInt(point.x());
            output.writeInt(point.z());
        }
    }

    private static SearchResult readResult(DataInputStream input) throws IOException {
        long centerX = input.readLong();
        long centerZ = input.readLong();
        int structureCount = input.readInt();
        int spawnerCount = input.readInt();
        List<BlockPoint> structures = readPoints(input, "结构坐标");
        if (structureCount != structures.size()) {
            throw new IOException("试炼刷怪笼检查点结构数量不一致");
        }
        return new SearchResult(centerX, centerZ, structureCount, spawnerCount, structures);
    }

    private static List<BlockPoint> readPoints(DataInputStream input, String label)
            throws IOException {
        int positionCount = input.readInt();
        if (positionCount < 0 || positionCount > 1_000_000) {
            throw new IOException("试炼刷怪笼检查点" + label + "数据损坏");
        }
        List<BlockPoint> points = new ArrayList<>(positionCount);
        for (int index = 0; index < positionCount; index++) {
            points.add(new BlockPoint(input.readInt(), input.readInt()));
        }
        return List.copyOf(points);
    }

    private static Map<SearchResult, List<BlockPoint>> immutableSources(
            Map<SearchResult, List<BlockPoint>> sources) {
        Map<SearchResult, List<BlockPoint>> copy = new HashMap<>();
        sources.forEach((result, points) -> copy.put(result, List.copyOf(points)));
        return Map.copyOf(copy);
    }

    static String fingerprint(FinderConfig config) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(("minecraft=26.2\n"
                    + "templates=" + TEMPLATE_FINGERPRINT + "\n"
                    + "checkpoint-format=" + FORMAT_VERSION + "\n"
                    + config)
                    .getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("当前 Java 不支持 SHA-256", e);
        }
    }

    record Statistics(
            long scannedCandidates,
            long predictedStructures,
            long predictedClusters,
            long prunedClusters,
            long verifiedStructures) {
        private static final Statistics EMPTY = new Statistics(0, 0, 0, 0, 0);

        Statistics {
            if (scannedCandidates < 0 || predictedStructures < 0 || predictedClusters < 0
                    || prunedClusters < 0 || verifiedStructures < 0) {
                throw new IllegalArgumentException("检查点统计不能为负数");
            }
        }
    }
}
