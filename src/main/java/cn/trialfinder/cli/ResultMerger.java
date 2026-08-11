package cn.trialfinder.cli;

import cn.trialfinder.model.SearchResult;
import cn.trialfinder.io.ResultWriter;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.function.Consumer;

/**
 * Merges the sorted per-tile temp files into the final CSV + TXT output.
 *
 * <p>Each temp file contains one {@link ResultEntry} per line (no header), already sorted by
 * {@link ResultEntry#compareTo} (spawner count descending). A {@link PriorityQueue} performs an
 * N-way merge holding only one entry per file in memory. When there are more than
 * {@link #FAN_IN} files (e.g. small tiles over the full world), a two-phase merge is used: groups
 * of files are merged into intermediate files, then those are merged — so the OS file-handle count
 * stays bounded.
 *
 * <p>The final merge keeps at most {@code topN} entries per structure-count group (default 100,
 * matching the original finder output). Temp files are deleted on success.
 */
public final class ResultMerger {

    private static final int FAN_IN = 512;

    private ResultMerger() {
    }

    /**
     * Merges {@code tempFiles} into the final CSV/TXT output.
     *
     * @param topN per structure-count group cap applied at the final merge
     */
    public static void merge(List<Path> tempFiles, Path outputCsv, int topN) throws IOException {
        if (tempFiles == null || tempFiles.isEmpty()) {
            ResultWriter.write(outputCsv, List.of(), topN);
            return;
        }

        List<Path> files = new ArrayList<>(tempFiles);
        List<Path> intermediates = new ArrayList<>();
        try {
            // Two-phase: collapse groups of files until the fan-in is small enough for one pass.
            while (files.size() > FAN_IN) {
                List<Path> next = new ArrayList<>();
                for (int i = 0; i < files.size(); i += FAN_IN) {
                    List<Path> batch = files.subList(i, Math.min(i + FAN_IN, files.size()));
                    Path intermediate = Files.createTempFile("trialfinder_merge_", ".tmp");
                    intermediates.add(intermediate);
                    mergeToFile(batch, intermediate);
                    next.add(intermediate);
                }
                files = next;
            }

            List<SearchResult> truncated = mergeCollect(files, topN);
            ResultWriter.write(outputCsv, truncated, topN);
        } finally {
            for (Path path : tempFiles) {
                deleteQuietly(path);
            }
            for (Path path : intermediates) {
                deleteQuietly(path);
            }
        }
    }

    /** Merges a small set of files into one sorted temp file (no truncation). */
    private static void mergeToFile(List<Path> inputs, Path output) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
            mergeStream(inputs, Integer.MAX_VALUE, entry -> {
                try {
                    writer.write(entry.toCsvLine());
                    writer.newLine();
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }
    }

    /** Merges a small set of files, keeping at most {@code topN} per group, into a list. */
    private static List<SearchResult> mergeCollect(List<Path> inputs, int topN) throws IOException {
        List<SearchResult> out = new ArrayList<>();
        mergeStream(inputs, topN, entry -> out.add(entry.toSearchResult()));
        return out;
    }

    /**
     * N-way priority-queue merge over sorted inputs. Emits entries in descending spawner order;
     * per structure-count group at most {@code topN} entries are emitted (the rest are consumed
     * and discarded).
     */
    private static void mergeStream(List<Path> inputs, int topN, Consumer<ResultEntry> sink)
            throws IOException {
        PriorityQueue<Head> queue = new PriorityQueue<>(
                Comparator.comparing((Head head) -> head.entry, ResultEntry::compareTo));
        Map<Integer, Integer> emittedByGroup = new HashMap<>();

        for (Path input : inputs) {
            BufferedReader reader = Files.newBufferedReader(input, StandardCharsets.UTF_8);
            String line = reader.readLine();
            if (line == null) {
                reader.close();
            } else {
                queue.add(new Head(ResultEntry.parse(line), reader));
            }
        }

        while (!queue.isEmpty()) {
            Head head = queue.poll();
            ResultEntry entry = head.entry;
            int group = entry.structureCount();
            int emitted = emittedByGroup.getOrDefault(group, 0);
            if (emitted < topN) {
                sink.accept(entry);
                emittedByGroup.put(group, emitted + 1);
            }
            String line = head.reader.readLine();
            if (line == null) {
                head.reader.close();
            } else {
                head.entry = ResultEntry.parse(line);
                queue.add(head);
            }
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // best-effort
        }
    }

    private static final class Head {
        ResultEntry entry;
        final BufferedReader reader;

        Head(ResultEntry entry, BufferedReader reader) {
            this.entry = entry;
            this.reader = reader;
        }
    }
}
