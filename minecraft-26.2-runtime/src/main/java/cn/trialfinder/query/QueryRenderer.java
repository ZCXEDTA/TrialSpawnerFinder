package cn.trialfinder.query;

import cn.minecraftfinder.core.ResultFiles;
import cn.minecraftfinder.core.ResultTableWriter;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 定点查询结果渲染，参照旧项目 QueryCommand 的格式：
 * <ul>
 *   <li>table：每查询点汇总 + 每密室明细（种类统计 + 每个刷怪笼的参数表）。</li>
 *   <li>csv：每个刷怪笼一行，含怪物/实体/权重/间隔等参数。</li>
 * </ul>
 */
public final class QueryRenderer {
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private QueryRenderer() {
    }

    /** 渲染到控制台（table 格式）。 */
    public static void renderTable(List<PointQuery.QueryResult> results, int radius) {
        for (PointQuery.QueryResult result : results) {
            System.out.printf("== 查询点 (%,d, %,d)  radius=%d  密室 %d 个  刷怪笼 %d 个 ==%n",
                    result.x(), result.z(), radius, result.chamberCount(), result.spawnerCount());
            if (result.chambers().isEmpty()) {
                System.out.println("   （无密室）");
                System.out.println();
                continue;
            }
            int chamberIdx = 0;
            for (PointQuery.ChamberOut chamber : result.chambers()) {
                chamberIdx++;
                System.out.printf("  密室 #%d  坐标 (%,d, %,d)  刷怪笼 %d 个  宝库 %d 个%n",
                        chamberIdx, chamber.x(), chamber.z(), chamber.spawnerCount(), chamber.vaultCount());

                // 种类统计：实体 -> 数量
                Map<String, Integer> tally = new LinkedHashMap<>();
                for (PointQuery.SpawnerOut s : chamber.spawners()) {
                    String key = s.mob() + (s.entity() != null ? " (" + s.entity() + ")" : "");
                    tally.merge(key, 1, Integer::sum);
                }
                System.out.print("      种类: ");
                System.out.println(tally.entrySet().stream()
                        .map(e -> e.getValue() + "× " + e.getKey())
                        .collect(Collectors.joining(", ")));

                // 明细表：每个刷怪笼的位置 + 参数
                String[] detailHeaders = {"刷怪笼X", "Y", "Z", "怪物", "实体", "权重",
                        "间隔tick", "同时数", "同时+玩家", "总数", "总数+玩家"};
                int[] dw = new int[detailHeaders.length];
                for (int i = 0; i < detailHeaders.length; i++) {
                    dw[i] = displayWidth(detailHeaders[i]);
                }
                List<String[]> detailRows = new ArrayList<>();
                for (PointQuery.SpawnerOut s : chamber.spawners()) {
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

                // 宝库表
                if (!chamber.vaults().isEmpty()) {
                    String[] vaultHeaders = {"宝库X", "Y", "Z", "类型"};
                    int[] vw = new int[vaultHeaders.length];
                    for (int i = 0; i < vaultHeaders.length; i++) {
                        vw[i] = displayWidth(vaultHeaders[i]);
                    }
                    List<String[]> vaultRows = new ArrayList<>();
                    for (PointQuery.VaultOut v : chamber.vaults()) {
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

    private static void writeTableRow(String[] values, int[] widths) {
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

    /** 写 CSV 文件 {@code query-<时间戳>.csv}（含对齐 TXT），返回文件路径。每个刷怪笼一行。 */
    public static Path writeCsv(List<PointQuery.QueryResult> results) throws IOException {
        List<String> headers = List.of(
                "查询点X", "查询点Z", "密室X", "密室Z",
                "刷怪笼X", "刷怪笼Y", "刷怪笼Z", "怪物类型", "配置文件", "实体",
                "权重", "间隔tick", "同时数", "同时+玩家", "总数", "总数+玩家", "宝库");
        List<List<String>> csvRows = new ArrayList<>();
        List<List<String>> textRows = new ArrayList<>();
        for (PointQuery.QueryResult result : results) {
            for (PointQuery.ChamberOut chamber : result.chambers()) {
                if (chamber.spawners().isEmpty()) {
                    csvRows.add(csvRow(result, chamber, emptySpawner()));
                    textRows.add(textRow(result, chamber, emptySpawner()));
                    continue;
                }
                for (PointQuery.SpawnerOut spawner : chamber.spawners()) {
                    csvRows.add(csvRow(result, chamber, spawner));
                    textRows.add(textRow(result, chamber, spawner));
                }
            }
        }
        Path path = ResultFiles.next(Path.of("query-" + TIMESTAMP.format(LocalDateTime.now()) + ".csv"));
        ResultTableWriter.write(path, headers, csvRows, textRows);
        return path;
    }

    private static PointQuery.SpawnerOut emptySpawner() {
        return new PointQuery.SpawnerOut(0, 0, 0);
    }

    private static List<String> csvRow(PointQuery.QueryResult result, PointQuery.ChamberOut chamber,
                                       PointQuery.SpawnerOut s) {
        String vaultStr = chamber.vaults().stream()
                .map(v -> v.x() + "," + v.y() + "," + v.z() + (v.ominous() ? "(不祥)" : ""))
                .collect(Collectors.joining("|"));
        if (vaultStr.isEmpty()) {
            vaultStr = "-";
        }
        return List.of(
                Integer.toString(result.x()), Integer.toString(result.z()),
                Integer.toString(chamber.x()), Integer.toString(chamber.z()),
                Integer.toString(s.x()), Integer.toString(s.y()), Integer.toString(s.z()),
                s.mob(),
                s.config() != null && !s.config().isEmpty() ? s.config() : "-",
                s.entity() != null ? s.entity() : "-",
                Integer.toString(s.weight()),
                Integer.toString(s.ticksBetweenSpawn()),
                fmt(s.simultaneousMobs()), fmt(s.simultaneousMobsPerPlayer()),
                fmt(s.totalMobs()), fmt(s.totalMobsPerPlayer()),
                vaultStr);
    }

    private static List<String> textRow(PointQuery.QueryResult result, PointQuery.ChamberOut chamber,
                                        PointQuery.SpawnerOut s) {
        return csvRow(result, chamber, s);
    }

    private static int displayWidth(String value) {
        return value.codePoints().map(codePoint -> codePoint <= 0x7f ? 1 : 2).sum();
    }
}
