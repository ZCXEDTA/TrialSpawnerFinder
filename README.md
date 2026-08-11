# TrialSpawnerFinder

在 **Minecraft Java 版 1.21.11** 世界种子中查找试炼密室密集区域，并统计圆形半径内实际生成的试炼刷怪笼。

这是一个 **CUDA 加速的独立命令行工具**，通过复刻游戏服务端的试炼密室生成算法（34×34 chunk 网格定位 + Jigsaw 拼接 + 怪物别名解析），在纯 Java + GPU 下高速扫描大范围世界。

- **版本**：1.0.0-beta.2
- **JDK 21 必需**
- **GPU**：可选。只需 NVIDIA 驱动（内核已预编译为 cubin 打包进 JAR，**无需 CUDA Toolkit**）；GPU 不可用时自动回退纯 CPU

---

## 快速开始

```bash
# Windows
run-cli.bat --seed 188188 --search-radius 10000

# Linux / macOS
./run-cli.sh --seed 188188 --search-radius 10000
```

结果写入当前目录 `results-<时间戳>.csv`（Excel 可直接打开）与 `.txt`（对齐阅读版）。

**不想手动调参？** 直接省略 `--cluster-radius`/`--grid-size`/`--top-k`，`--auto-tune`（默认开启）会按 `--search-radius` 自动计算合理值。

---

## 参数

### 搜索范围

| 参数 | 默认 | 说明 |
|---|---|---|
| `--seed` | 必填 | 世界种子（也可在 finder.properties 里设） |
| `--search-radius` | 10000 | 以 (0,0) 为圆心的搜索半径（方块）；`--full-world` 时忽略 |
| `--full-world` | false | 分片流式扫描完整 6000 万 × 6000 万 世界正方形 |
| `--tile-size` | 100000 | `--full-world` 分片边长（方块） |
| `--tile-overlap` | 1000 | `--full-world` 相邻分片重叠量（方块） |

### 聚类与筛选

| 参数 | 默认 | 说明 |
|---|---|---|
| `--cluster-radius` | 1000 | 密度聚类半径（方块）。**不要小于密室间距（~544 块）**，否则聚不出聚类、结果可能为空；100w 格内 160 格半径的三联密室已很少，一般 256 格配 `min-structures 3`、128 格配 `min-structures 2` |
| `--min-structures` | 3 | 一个聚类内至少的密室数量 |
| `--min-spawners` | 20 | 密度圆内至少的试炼刷怪笼数量 |
| `--top-k` | auto | 粗筛 top-K 聚类数上限（0=关闭全量 B 流，超大半径极慢）。越大召回越高但越慢 |
| `--cluster-method` | density | 粗聚类方法：`density`（密度峰值 + KD-tree）或 `legacy`（并查集） |
| `--max-cluster-size` | 0 | 密度聚类拆分阈值（0=自动） |
| `--prefilter-mode` | cluster | 初筛方法：`cluster`（默认）或 `grid`（GPU 网格聚合 + top-K，更快但近似） |
| `--grid-size` | 0 | 网格边长（方块），`--prefilter-mode grid` 用（0=自动 `2*cluster-radius`） |
| `--min-candidates-per-tile` | 0 | 稀疏分片预筛阈值：分片密度幸存候选数低于此值则跳过粗聚类（0=自动=`--min-structures`） |

### 输出与行为

| 参数 | 默认 | 说明 |
|---|---|---|
| `--output-prefix` | `results-<时间戳>` | 输出文件前缀 |
| `--threads` | 4 | B 流（Jigsaw 拼接）CPU 线程数（不要超过逻辑核心数） |
| `--debug` | false | 打印进度与耗时（含 Top-K 各阶段日志） |
| `--quiet` | false | 关闭所有进度条/阶段输出（只保留结果摘要） |
| `--no-gpu` | false | 强制纯 CPU 路径 |
| `--biome-check` | false | 生物群系过滤（**近似可用**，排除海洋/深海底密室，见下） |
| `--cache` | false | 启用 B 流磁盘缓存（默认禁用，见下） |
| `--cache-dir` | ./cache | 缓存目录（仅 `--cache` 时使用） |
| `--jigsaw-depth` | 0 | 浅层 Jigsaw 拼接深度（0=原版 20；调小加速但可能丢刷怪笼） |
| `--auto-tune` / `--no-auto-tune` | 启用 | 自动计算未显式指定的 `--cluster-radius`/`--grid-size`/`--top-k` |

---

## 配置优先级：命令行 > finder.properties > 默认值

工作目录存在 `finder.properties` 时自动读取，作为**默认参数**；**命令行参数优先**，配置没有的用默认值。

```properties
# 示例
seed=188188
search-radius-blocks=10000
cluster-radius-blocks=256
min-structures=3
min-spawners=20
threads=8
```

命令行：`run-cli.bat --seed 114514` → seed 用 114514，其余用配置。

---

## 自动调参（`--auto-tune`）

`--cluster-radius`、`--grid-size`、`--top-k` 未显式指定时，按 `--search-radius` 自动计算：

```
cluster-radius = max(64, min(256, searchRadius / 200))
grid-size      = 2 × cluster-radius
top-k          = max(50, min(5000, searchRadius / 100))
```

- 半径 > 100,000 时自动切换到 GPU 网格预筛（`--prefilter-mode grid`）
- `--debug` 下输出 `[auto-tune]` 日志
- 配置里显式设的值不会被 auto-tune 覆盖

---

## 性能优化概览

### B 流（Jigsaw 拼接）— 14 核 ~74 座/秒

根因诊断：每密室 ~15MB 对象分配导致的 GC 风暴。优化后单线程每密室 177→31ms（5.7×），14 线程 ~74 座/秒（2.7×）：

| 优化 | 效果 |
|---|---|
| 旋转/模板缓存（按 rotation 预计算 jigsaw/bbox） | 单线程 5.7× |
| `FrontAndTop`/`BlockState` 缓存 | 消除 parse/rotate 分配 |
| `get()` 快路径替代 `computeIfAbsent` | 14 线程 97→173 座/秒 |
| 模板 NBT 预加载 | 消除并行首次加载竞争 |
| `VoxelShape` 空间哈希 | 重叠检测 O(n²)→O(n) |

### A 流（枚举 + 密度预筛）— GPU 直通

诊断：GPU 枚举内核实际仅 ~2ms（GPU 已跑满）；瓶颈是 Java 侧千万级 `BlockPoint` 对象构造 + GC。GPU 直通把「枚举→密度→网格聚合→top-K」全在 GPU 完成，只回传少量候选。

### 超大半径保护（自动分片）

半径 100 万+ 时候选可达上千万到上亿，程序自动：
- **自动分片**：每片 ~500 万候选自适应切片，逐片枚举 + grid 预筛 + 合并（内存有界）
- **候选预估 + WARN**：超 5000 万候选时启动打印建议
- **分片进度 + ETA**：`[grid 自动分片] tile x/n ... ETA=...`
- **密度网格自适应**：网格超限时无损放大格长
- **召回率优化**：重叠分片 + 重叠 cell + 无损密度预筛

| 场景 | 耗时 |
|---|---|
| 10k 半径精确 | ~14s |
| 100k 半径 grid | ~6s |
| 1M 半径（自动分片） | ~7.5s |
| 10M 半径 | ~1.5-4 分钟 |
| 30M 半径（世界极限） | ~10+ 分钟 |

---

## 定点查询（`query` 子命令）

不进行全量搜索，只查询指定坐标附近的密室详情：

```bash
# 坐标
run-cli.bat query --seed 188188 --coords 544,166 1000,-2000 --radius 1000

# 坐标文件（每行 "x z"）
./run-cli.sh query --seed 188188 --file coords.txt --radius 1000 --output json

# 从之前生成的 results CSV 读取中心坐标
./run-cli.sh query --seed 188188 --file results-20260810-161409.csv --radius 1000 --output csv
```

参数：`--seed`、`--coords`（x,z 可多个）、`--file`、`--radius`（默认 1000）、`--output`（`table`/`json`/`csv`）、`--cache`/`--cache-dir`、`--threads`、`--no-gpu`、`--debug`。

输出每个刷怪笼的**详细参数**：位置、怪物类型、配置文件 id、实际实体（如毒骷髅→`minecraft:bogged`）、权重、生成间隔（tick）、同时生成数、每玩家加成。

---

## B 流缓存（`--cache`，默认禁用）

默认禁用是有意的：低命中率时磁盘 I/O 反而拖慢。重复搜索同一种子 / 重叠查询点才建议开启。每个密室（seed+chunk）缓存为 `spawners_<seed>_<chunkX>_<chunkZ>.json`，含刷怪笼坐标 + 怪物类型 + 配置。

---

## `--biome-check`（近似可用）

按生物群系过滤候选，排除**海洋/深海底/沙滩**（不生成试炼密室）等，保留陆地。**不是逐点精确**：温度/湿度维度精确，大陆度/侵蚀/深度/离岸度用确定性近似（`TerrainProvider` 样条表未移植）。实测 10k 半径 1058→716 候选通过。陆地坐标可能解析为略有差异的陆地生物群系，但对"排除海底密室"足够。

---

## 示例

```bash
# 快速验证（约 6 秒）
run-cli.bat --seed 188188 --search-radius 1000000 --cluster-radius 256 --threads 14

# 常规搜索（找密集区）
run-cli.bat --seed 188188 --search-radius 100000 --cluster-radius 1000 --min-structures 3 --min-spawners 20 --top-k 200 --threads 14

# 全图流式扫描（推荐 Top-K 截断）
run-cli.bat --seed 188188 --full-world --tile-size 100000 --tile-overlap 1000 --top-k 100000 --threads 14

# 超大半径（自动分片，内存有界）
run-cli.bat --seed -6523988883445283364 --search-radius 300000 --cluster-radius 256 --min-structures 2 --min-spawners 40 --threads 14

# 生物群系过滤
run-cli.bat --seed 188188 --search-radius 10000 --biome-check

# 定点查询
run-cli.bat query --seed 188188 --coords 544,166 1000,-2000 --radius 1000
```

---

## 从源码构建

```bash
git clone <repo>
cd TrialSpawnerFinder
./gradlew clean test shadowJar
# 独立 fat JAR: build/libs/trialfinder-1.0.0-beta.2.jar
# 可选: 重新生成预编译 cubin (需 nvcc + MSVC)
./gradlew compileCubin
```

- **`shadowJar`**：独立 CLI fat JAR（主类 `cn.trialfinder.cli.TrialFinderCLI`）
- **`remapJar`**：Fabric Mod（另有 `world`/`mixin` 包）
- **测试**：124 个 JUnit 用例（GPU/CPU 逐位一致由测试保证）

---

## 已知限制

- `--biome-check` 为近似（见上）
- `--prefilter-mode grid` 是近似模式（按网格总密度而非真实刷怪笼排序）
- GPU 原生库仅随附 Windows x86_64；Linux/macOS 用 `--no-gpu`
- `--search-radius 10,000,000+` 候选达十亿级，即使自动分片也需数分钟到数小时；建议用 `--full-world --top-k` 或减小半径
