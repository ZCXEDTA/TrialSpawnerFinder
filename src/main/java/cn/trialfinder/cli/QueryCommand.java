package cn.trialfinder.cli;

import cn.trialfinder.accel.Accelerator;
import cn.trialfinder.model.BlockPoint;
import cn.trialfinder.sim.SimChamberGenerator;
import com.google.gson.GsonBuilder;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.Callable;

/**
 * Point query subcommand: instead of a full search, lists every trial chamber within {@code radius}
 * of each user-supplied query point, along with each chamber's spawner positions and mob types.
 * Chambers are generated through the B-flow pipeline (cached via {@link SpawnerCache}).
 *
 * <pre>
 *   run-cli.bat query --seed 188188 --coords 544,166 1000,-2000 --radius 1000
 *   run-cli.bat query --seed 188188 --file coords.txt --radius 1000 --output json
 *   run-cli.bat query --seed 188188 --file results-20260810-161409.csv --radius 1000 --output csv
 * </pre>
 */
@Command(name = "query", mixinStandardHelpOptions = true,
        description = "Query trial-chamber details (spawners + mobs) near given coordinates.")
public final class QueryCommand implements Callable<Integer> {

    /** One output spawner row with detailed parameters. */
    public record SpawnerOut(int x, int y, int z, String mob, String config, String entity,
                             int weight, int ticksBetweenSpawn, double simultaneousMobs,
                             double simultaneousMobsPerPlayer, double totalMobs,
                             double totalMobsPerPlayer) {
        public SpawnerOut(int x, int y, int z, String mob) {
            this(x, y, z, mob, null, null, 0, 0, 0.0, 0.0, 0.0, 0.0);
        }
    }

    /** One output vault row. */
    public record VaultOut(int x, int y, int z, boolean ominous) {
    }

    /** One chamber found near a query point. */
    public record ChamberOut(int x, int z, List<SpawnerOut> spawners, List<VaultOut> vaults) {
        public ChamberOut(int x, int z, List<SpawnerOut> spawners) {
            this(x, z, spawners, List.of());
        }

        public ChamberOut {
            spawners = List.copyOf(spawners);
            vaults = List.copyOf(vaults);
        }

        public int spawnerCount() {
            return this.spawners.size();
        }
    }

    /** Aggregated result for one query point. */
    public record QueryResult(int x, int z, int chamberCount, int spawnerCount,
                              List<String> mobs, List<ChamberOut> chambers) {
        public QueryResult {
            mobs = List.copyOf(mobs);
            chambers = List.copyOf(chambers);
        }
    }

    @Option(names = "--seed", required = true, description = "World seed")
    long seed;

    @Option(names = "--coords", arity = "1..*", description = "Query point(s) as 'x,z' (repeatable, or space separated)")
    List<String> coords = new ArrayList<>();

    @Option(names = "--file", description = "File of query points: lines 'x z', or a results CSV (中心X/中心Z columns)")
    String file;

    @Option(names = "--radius", defaultValue = "1000", description = "Query radius in blocks")
    int radius;

    @Option(names = "--output", defaultValue = "table", description = "Output format: table, json or csv")
    String output;

    @Option(names = "--cache-dir", defaultValue = "./cache", description = "B-flow chamber cache directory")
    String cacheDir;

    @Option(names = "--cache", defaultValue = "false",
            description = "Enable the on-disk B-flow chamber cache (default: disabled)")
    boolean cacheEnabled;

    @Option(names = "--threads", defaultValue = "4", description = "CPU threads (reserved; the per-point loop is currently sequential)")
    int threads;

    @Option(names = "--no-gpu", defaultValue = "false", description = "Force the pure-CPU path")
    boolean noGpu;

    @Option(names = "--debug", defaultValue = "false", description = "Print progress and cache hit/miss logs")
    boolean debug;

    @Override
    public Integer call() throws Exception {
        List<int[]> points = parseQueryPoints();
        if (points.isEmpty()) {
            System.err.println("No query points given: use --coords or --file.");
            return 2;
        }
        if (this.radius <= 0) {
            System.err.println("--radius must be positive.");
            return 2;
        }

        Accelerator acc = TrialFinderCLI.selectAccelerator(this.noGpu);
        SimChamberGenerator generator = SimChamberGenerator.fromClasspath();
        if (this.cacheEnabled) {
            generator.setCache(new SpawnerCache(Path.of(this.cacheDir), true, this.debug));
        }

        System.out.println("=== Trial Chambers Query ===");
        System.out.println("accelerator : " + acc.name());
        System.out.println("seed        : " + seed);
        System.out.printf("query points: %,d  radius=%,d  output=%s%n", points.size(), radius, output);

        long started = System.nanoTime();
        List<QueryResult> results = new ArrayList<>(points.size());
        for (int[] point : points) {
            results.add(queryPoint(generator, acc, point[0], point[1]));
        }
        if (this.debug) {
            System.out.printf("[query] %d points in %.1fs%n", points.size(), (System.nanoTime() - started) / 1e9);
        }
        render(results);
        return 0;
    }

    // ---------------------------------------------------------------- per-point query

    private QueryResult queryPoint(SimChamberGenerator generator, Accelerator acc, int qx, int qz) {
        long radiusSq = (long) this.radius * this.radius;
        List<BlockPoint> candidates = acc.findChunks(
                this.seed,
                qx - this.radius, qx + this.radius,
                qz - this.radius, qz + this.radius,
                true, qx, qz, radiusSq);

        List<ChamberOut> chambers = new ArrayList<>();
        TreeSet<String> mobs = new TreeSet<>();
        int totalSpawners = 0;
        for (BlockPoint candidate : candidates) {
            int chunkX = Math.floorDiv(candidate.x(), 16);
            int chunkZ = Math.floorDiv(candidate.z(), 16);
            SimChamberGenerator.ChamberResult result =
                    generator.generateChamber(this.seed, chunkX, chunkZ).orElse(null);
            if (result == null) {
                continue;
            }
            List<SpawnerOut> spawners = result.spawnerInfos().stream()
                    .map(info -> {
                        SpawnerConfig.Config cfg = SpawnerConfig.load(info.config());
                        String entity = cfg != null ? cfg.primaryEntity() : null;
                        int weight = cfg != null && !cfg.potentials().isEmpty()
                                ? cfg.potentials().get(0).weight() : 0;
                        int ticks = cfg != null ? cfg.ticksBetweenSpawn() : 0;
                        double sim = cfg != null ? cfg.simultaneousMobs() : 0.0;
                        double simPer = cfg != null ? cfg.simultaneousMobsPerPlayer() : 0.0;
                        double total = cfg != null ? cfg.totalMobs() : 0.0;
                        double totalPer = cfg != null ? cfg.totalMobsPerPlayer() : 0.0;
                        return new SpawnerOut(
                                info.pos().getX(), info.pos().getY(), info.pos().getZ(),
                                info.mob(), info.config(), entity, weight, ticks, sim, simPer, total, totalPer);
                    })
                    .toList();
            for (SpawnerOut spawner : spawners) {
                mobs.add(spawner.mob());
            }
            totalSpawners += spawners.size();
            List<VaultOut> vaults = result.vaultInfos().stream()
                    .map(v -> new VaultOut(v.pos().getX(), v.pos().getY(), v.pos().getZ(), v.ominous()))
                    .toList();
            chambers.add(new ChamberOut(candidate.x(), candidate.z(), spawners, vaults));
        }
        chambers.sort((a, b) -> {
            int byX = Integer.compare(a.x(), b.x());
            return byX != 0 ? byX : Integer.compare(a.z(), b.z());
        });
        return new QueryResult(qx, qz, chambers.size(), totalSpawners, List.copyOf(mobs), chambers);
    }

    // ---------------------------------------------------------------- input parsing

    private List<int[]> parseQueryPoints() throws IOException {
        List<int[]> points = new ArrayList<>();
        if (this.coords != null) {
            for (String coord : this.coords) {
                String[] parts = coord.split(",");
                if (parts.length != 2) {
                    throw new IllegalArgumentException("--coords must be 'x,z', got: " + coord);
                }
                points.add(new int[]{Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim())});
            }
        }
        if (this.file != null) {
            parseQueryFile(Path.of(this.file), points);
        }
        return points;
    }

    /**
     * Parses a query-point file. Two layouts are supported:
     * <ul>
     *   <li>plain text: one {@code x z} (whitespace or comma separated) per line, {@code #} comments skipped;</li>
     *   <li>a results CSV: a header containing {@code 中心X}/{@code 中心Z} (or {@code CenterX}/{@code CenterZ})
     *       whose columns are read per line.</li>
     * </ul>
     */
    static void parseQueryFile(Path file, List<int[]> out) throws IOException {
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
                    out.add(new int[]{Integer.parseInt(values[idxX].trim()), Integer.parseInt(values[idxZ].trim())});
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
                    out.add(new int[]{Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim())});
                }
            }
        }
    }

    // ---------------------------------------------------------------- output

    private void render(List<QueryResult> results) {
        switch (this.output.toLowerCase()) {
            case "json" -> renderJson(results);
            case "csv" -> renderCsv(results);
            default -> renderTable(results);
        }
    }

    private void renderTable(List<QueryResult> results) {
        // Per query point: summary + per-chamber breakdown (chamber #, spawner kind tally, details).
        for (QueryResult result : results) {
            System.out.printf("== 查询点 (%,d, %,d)  radius=%d  密室 %d 个  刷怪笼 %d 个 ==%n",
                    result.x(), result.z(), this.radius, result.chamberCount(), result.spawnerCount());
            if (result.chambers().isEmpty()) {
                System.out.println("   （无密室）");
                System.out.println();
                continue;
            }
            int chamberIdx = 0;
            for (ChamberOut chamber : result.chambers()) {
                chamberIdx++;
                System.out.printf("  密室 #%d  坐标 (%,d, %,d)  刷怪笼 %d 个  宝库 %d 个%n",
                        chamberIdx, chamber.x(), chamber.z(), chamber.spawners().size(), chamber.vaults().size());

                // Kind tally: entity -> count.
                java.util.LinkedHashMap<String, Integer> tally = new java.util.LinkedHashMap<>();
                for (SpawnerOut s : chamber.spawners()) {
                    String key = s.mob() + (s.entity() != null ? " (" + s.entity() + ")" : "");
                    tally.merge(key, 1, Integer::sum);
                }
                System.out.print("      种类: ");
                System.out.println(tally.entrySet().stream()
                        .map(e -> e.getValue() + "× " + e.getKey())
                        .collect(java.util.stream.Collectors.joining(", ")));

                // Detail rows: each spawner with its position + parameters (entity/weight/interval).
                String[] detailHeaders = {"刷怪笼X", "Y", "Z", "怪物", "实体", "权重",
                        "间隔tick", "同时数", "同时+玩家", "总数", "总数+玩家"};
                int[] dw = new int[detailHeaders.length];
                for (int i = 0; i < detailHeaders.length; i++) {
                    dw[i] = displayWidth(detailHeaders[i]);
                }
                List<String[]> detailRows = new ArrayList<>();
                for (SpawnerOut s : chamber.spawners()) {
                    String[] row = {
                            Integer.toString(s.x()), Integer.toString(s.y()), Integer.toString(s.z()),
                            s.mob(),
                            s.entity() != null ? s.entity() : "-",
                            Integer.toString(s.weight()),
                            Integer.toString(s.ticksBetweenSpawn()),
                            fmt(s.simultaneousMobs()),
                            fmt(s.simultaneousMobsPerPlayer()),
                            fmt(s.totalMobs()),
                            fmt(s.totalMobsPerPlayer())
                    };
                    for (int i = 0; i < row.length; i++) {
                        dw[i] = Math.max(dw[i], displayWidth(row[i]));
                    }
                    detailRows.add(row);
                }
                writeTableRow(detailHeaders, dw);
                for (String[] row : detailRows) {
                    writeTableRow(row, dw);
                }

                // Vault rows: each vault with its position + ominous flag.
                if (!chamber.vaults().isEmpty()) {
                    String[] vaultHeaders = {"宝库X", "Y", "Z", "类型"};
                    int[] vw = new int[vaultHeaders.length];
                    for (int i = 0; i < vaultHeaders.length; i++) {
                        vw[i] = displayWidth(vaultHeaders[i]);
                    }
                    List<String[]> vaultRows = new ArrayList<>();
                    for (VaultOut v : chamber.vaults()) {
                        String[] row = {
                                Integer.toString(v.x()), Integer.toString(v.y()), Integer.toString(v.z()),
                                v.ominous() ? "不祥宝库" : "普通宝库"
                        };
                        for (int i = 0; i < row.length; i++) {
                            vw[i] = Math.max(vw[i], displayWidth(row[i]));
                        }
                        vaultRows.add(row);
                    }
                    writeTableRow(vaultHeaders, vw);
                    for (String[] row : vaultRows) {
                        writeTableRow(row, vw);
                    }
                }
                System.out.println();
            }
            System.out.println();
        }
    }

    private static String fmt(double v) {
        return v == Math.rint(v) ? Long.toString((long) v) : Double.toString(v);
    }

    private void writeTableRow(String[] values, int[] widths) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                sb.append("  ");
            }
            boolean numeric = i < values.length - 1;
            String value = values[i];
            if (numeric) {
                sb.append(" ".repeat(Math.max(0, widths[i] - displayWidth(value)))).append(value);
            } else {
                sb.append(value).append(" ".repeat(Math.max(0, widths[i] - displayWidth(value))));
            }
        }
        System.out.println(sb);
    }

    private void renderJson(List<QueryResult> results) {
        System.out.println(new GsonBuilder().setPrettyPrinting().create().toJson(results));
    }

    private void renderCsv(List<QueryResult> results) {
        System.out.println("查询点X;查询点Z;密室X;密室Z;刷怪笼X;刷怪笼Y;刷怪笼Z;怪物类型;配置文件;实体;权重;间隔tick;同时数;同时+玩家;总数;总数+玩家;宝库");
        for (QueryResult result : results) {
            for (ChamberOut chamber : result.chambers()) {
                String vaultStr = chamber.vaults().stream()
                        .map(v -> v.x() + "," + v.y() + "," + v.z() + (v.ominous() ? "(不祥)" : ""))
                        .collect(java.util.stream.Collectors.joining("|"));
                if (vaultStr.isEmpty()) {
                    vaultStr = "-";
                }
                if (chamber.spawners().isEmpty()) {
                    System.out.printf("%d;%d;%d;%d;;;;;;;-;;;%s%n",
                            result.x(), result.z(), chamber.x(), chamber.z(), vaultStr);
                    continue;
                }
                for (SpawnerOut spawner : chamber.spawners()) {
                    System.out.printf("%d;%d;%d;%d;%d;%d;%d;%s;%s;%s;%d;%d;%s;%s;%s;%s;%s%n",
                            result.x(), result.z(), chamber.x(), chamber.z(),
                            spawner.x(), spawner.y(), spawner.z(), spawner.mob(),
                            spawner.config() != null ? spawner.config() : "-",
                            spawner.entity() != null ? spawner.entity() : "-",
                            spawner.weight(), spawner.ticksBetweenSpawn(),
                            fmt(spawner.simultaneousMobs()), fmt(spawner.simultaneousMobsPerPlayer()),
                            fmt(spawner.totalMobs()), fmt(spawner.totalMobsPerPlayer()),
                            vaultStr);
                }
            }
        }
    }

    private static int displayWidth(String value) {
        return value.codePoints().map(codePoint -> codePoint <= 0x7f ? 1 : 2).sum();
    }
}
