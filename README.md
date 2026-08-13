# TrialSpawnerFinder

在 **Minecraft Java 版 1.21.11** 世界种子中查找试炼密室密集区域，并统计圆形半径内实际生成的试炼刷怪笼。

这是一个 **CUDA 加速的独立命令行工具**，通过复刻游戏服务端的试炼密室生成算法（34×34 chunk 网格定位 + Jigsaw 拼接 + 怪物别名解析），在纯 Java + GPU + CPU 下高速扫描大范围世界,调用近似算法初步筛选结果,减少CPU运算的压力,牺牲部分精度换取高速度。

- **版本**：1.4.0
- **GPU**：可选。需 NVIDIA 驱动（内核已预编译为 cubin 打包进 JAR，**无需 CUDA Toolkit**）,不支持旧gpu ；GPU 不可用时自动回退纯 CPU

### 📦 发布版本（三种，按需下载）

命名规范：`trialfinder-<版本>-<变体>.zip`——`with-runtime-graalvm`（GraalVM 带环境）/ `with-runtime-jre`（标准带环境）/ `without-runtime`（不带环境）。

| 版本               | 文件 | 体积 | 环境要求 | 性能 |
|--------------------|---|---|---|---|
| **GraalVM 性能版** | `trialfinder-1.4.0-with-runtime-graalvm.zip` | ~60MB | **无需安装 JDK**（内含 GraalVM JRE） | 🚀 **最快**（JIT 约快 20%） |
| **标准带环境版**   | `trialfinder-1.4.0-with-runtime-jre.zip` | ~42MB | **无需安装 JDK**（内含精简 JRE） | 标准 |
| **不带环境版**     | `trialfinder-1.4.0-without-runtime.zip` | ~11MB | 需自备 JDK 21+（或 GraalVM） | 取决于系统 |

> **自动适配**：`run-cli.bat`/`run-cli.sh` 自动选择 Java 运行时——优先 `GRAALVM_HOME`/`JAVA_HOME` 环境变量 → `finder.properties` 的 `java-home`/`graalvm-home` → **捆绑的 `runtime-graalvm\`（GraalVM 性能版）或 `runtime\`（标准版）** → 系统 java。装了好 JDK 会优先用，没有就用捆绑的。

---

## 快速开始

**带环境版解压即用**（Windows）：下载 `trialfinder-1.4.0-with-runtime-jre.zip`（标准）或 `trialfinder-1.4.0-with-runtime-graalvm.zip`（性能）→ 解压 → 双击/命令行运行 `run-cli.bat`，无需安装任何 Java。

**不带环境版**：需系统已装 JDK 21+（或 GraalVM），`run-cli.bat` 会自动检测。

```bash
# Windows
.\run-cli.bat --seed 188188 --search-radius 10000

# Linux / macOS（不带环境）
./run-cli.sh --seed 188188 --search-radius 10000
```

**示例**
```powershell
 .\run-cli.bat --seed -6523988883445283364 --search-radius 1000000 --cluster-radius 128  --min-structures 2 --min-spawners 40 --threads 14 --check-top 100 
```
大范围可以使用缓存(中断后快速恢复) 同种子通用,会产生cache文件夹,cache文件较大,需要手动清理
```powershell
.\run-cli.bat --seed -6523988883445283364 --search-radius 1000000 --cluster-radius 160 --min-structures 2 --min-spawners 40 --threads 14 --check-top 100 --cache
```
定点查询
```powershell
.\run-cli.bat query --seed -6523988883445283364 --coords -89589,42331 --radius 160
```
参数建议：
- `--cluster-radius`：根据情况取从模拟距离到 256 之间，128 推荐查大范围二联、256 查三联
- `--min-structures`：初筛相连密室数量，根据半径调整
- `--min-spawners`：最低笼子数量，建议为密室数量 × 20
- `--threads`：调用 CPU 逻辑核心数

结果写入当前目录 `results-<时间戳>.csv`（Excel 可直接打开）与 `.txt`（对齐阅读版）。

**不想手动调参？** 直接省略 `--cluster-radius`/`--grid-size`，`--auto-tune`（默认开启）会按 `--search-radius` 自动计算合理值。

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
| `--top-k` | 0 | 粗筛 top-K 聚类数上限（**默认 0=关闭**，所有粗聚类进 B 流，最高精度）。隐藏参数，仅高级用户显式启用截断 |
| `--prefilter-mode` | cluster | 初筛方法：`cluster`（默认，先聚类）或 `grid`（GPU 网格聚合 + 无损密度剪枝，仅显式指定时启用） |
| `--grid-size` | 0 | 网格边长（方块），`--prefilter-mode grid` 用（0=自动 `2*cluster-radius`） |
| `--min-candidates-per-tile` | 0 | 稀疏分片预筛阈值：分片密度幸存候选数低于此值则跳过粗聚类（0=自动=`--min-structures`） |

### 输出与行为

| 参数 | 默认 | 说明 |
|---|---|---|
| `--output-prefix` | `results-<时间戳>` | 输出文件前缀 |
| `--threads` | 4 | B 流（Jigsaw 拼接）CPU 线程数（不要超过逻辑核心数） |
| `--debug` | false | 打印进度与耗时 |
| `--quiet` | false | 关闭所有进度条/阶段输出（只保留结果摘要） |
| `--no-gpu` | false | 强制纯 CPU 路径 |
| `--biome-check` | false | 生物群系过滤（**近似可用**，排除海洋/深海底密室，见下） |
| `--cache` | false | 启用 B 流磁盘缓存（默认禁用，见下） |
| `--cache-dir` | ./cache | 缓存目录（仅 `--cache` 时使用） |
| `--jigsaw-depth` | 0 | 浅层 Jigsaw 拼接深度（0=原版 20；调小加速但可能丢刷怪笼） |
| `--predict-depth` | 0 | 浅层预测深度（0=禁用；拼接跑到此深度时按浅层刷怪笼数判断） |
| `--predict-gate` | 0 | 浅层刷怪笼门限，低于此数的密室在完整 B 流前丢弃（需 `--predict-depth > 0`） |
| `--check-top` | 0 | 检查前 N 个结果的快速/慢速刷怪笼与宝库数量，追加到 CSV/TXT（0=不检查） |
| `--auto-tune` / `--no-auto-tune` | 启用 | 自动计算未显式指定的 `--cluster-radius`/`--grid-size`（`--top-k` 保持 0=关闭） |

### `--check-top`：检查前 N 个结果

对最终输出的**前 N 个结果**逐个重新生成其密室，统计每种密室的**快速/慢速刷怪笼**和**宝库**数量，并追加到 CSV/TXT 输出的末尾列。

- **快速刷怪笼**：`ticks_between_spawn = 20`（非 `slow_ranged` 分类）
- **慢速刷怪笼**：`ticks_between_spawn = 160`（`slow_ranged` 分类，如骷髅/流浪者）
- **宝库**：该结果所有密室中的宝库总数

```bash
# 对前 10 个结果做检查
run-cli.bat --seed 188188 --search-radius 100000 --cluster-radius 256 --check-top 10
```

控制台输出：`check-top : 3 results | fast=123 slow=40 vaults=241`

CSV 追加 3 列：
```
排名;中心X;中心Z;密室数量;试炼刷怪笼数量;密室位置;快速刷怪笼;慢速刷怪笼;宝库数量
1;-1056;3600;3;51;-1288,3608|...;44;12;79
```

TXT 对齐输出同样追加这 3 列。默认 `--check-top 0` 不检查（输出格式与之前一致）。

---

## 配置优先级：命令行 > finder.properties > 默认值

工作目录存在 `finder.properties` 时自动读取，作为**默认参数**；**命令行参数优先**，配置没有的用默认值。

```properties
# 示例（键名见 TrialFinderCLI 属性映射；线程数用 scan-threads）
seed=188188
search-radius-blocks=10000
cluster-radius-blocks=256
min-structures=3
min-spawners=20
scan-threads=8
```

命令行：`run-cli.bat --seed 114514` → seed 用 114514，其余用配置。

---

## 自动调参（`--auto-tune`）

`--cluster-radius`、`--grid-size` 未显式指定时，按 `--search-radius` 自动计算（`--top-k` 保持 0=关闭，所有粗聚类进 B 流，最高精度）：

```
cluster-radius = max(64, min(256, searchRadius / 200))
grid-size      = 2 × cluster-radius
top-k          = 0（不截断，全部粗聚类进 B 流）
```

- 默认始终使用 `cluster` 预筛（grid 不会自动启用）；大半径如需要更快可显式指定 `--prefilter-mode grid`
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
| 模板按名缓存（palette 预建块名索引） | 刷怪笼/宝库扫描 O(块数)→O(命中数)，消除空扫描分配 |
| **手写轻量布局**（`LightJigsawPlacement`：LightPiece + ConnectorBuffer + ElementMetadata 缓存） | 预测器单密室 15ms→8ms（~2×），RNG 逐位一致 |

### A 流（枚举 + 密度预筛）— GPU 直通

诊断：GPU 枚举内核实际仅 ~2ms（GPU 已跑满）；瓶颈是 Java 侧千万级 `BlockPoint` 对象构造 + GC。GPU 枚举把候选生成放在 GPU 侧完成，再交回密度预筛。

### 无损密度预筛（`--prefilter-mode grid` 关键）

grid 模式在 tile 并集上补了与 cluster 模式一致的 `pruneByDensity`（`score >= minStructures`）：候选的 2R 邻居数不足 `min-structures` 则不可能成为合格聚类成员，**剪掉它不改变结果**。大半径下这是主要提速——R=300000、CR=128、MS=2 时从 95.5 万候选剪到 2.58 万（37 倍），B 流从 50 分钟降到 ~2 分钟。

### 超大半径保护（自动分片）

半径 100 万+ 时候选可达上千万到上亿，程序自动：
- **自动分片**：逐片枚举 + grid 预筛 + 合并（内存有界）
- **候选预估 + WARN**：超 5000 万候选时启动打印建议
- **分片进度 + ETA**：`[grid 自动分片] tile x/n ... ETA=...`
- **密度网格自适应**：网格超限时无损放大格长
- **召回率优化**：重叠分片 + 无损密度预筛


---

## 定点查询（`query` 子命令）

**不进行全量搜索**，只查询指定坐标附近 radius 内的所有密室，列出每个密室的刷怪笼详细参数。适合：确认某片区域的密室密度、查看特定位置的刷怪笼构成、分析 results 结果附近的生成情况。

### 基本用法

```bash
# 查询一个坐标附近 1000 格内的密室
run-cli.bat query --seed 188188 --coords 544,166 --radius 1000

# 查询多个坐标（空格分隔）
run-cli.bat query --seed 188188 --coords 544,166 1000,-2000 --radius 1000
```

### 三种输入方式

| 方式 | 命令 | 适用 |
|---|---|---|
| **坐标** | `--coords 544,166 1000,-2000` | 少量点，直接命令行 |
| **坐标文件** | `--file coords.txt` | 多行，每行 `x z`（`#` 注释） |
| **results CSV** | `--file results-20260810-161409.csv` | 直接复用之前搜索的结果中心坐标 |

```bash
# 坐标文件示例（coords.txt）
544 166
1000 -2000
-500 300

# 用法
./run-cli.sh query --seed 188188 --file coords.txt --radius 1000
```

> **注意**：`--file` 用**绝对路径**，或先 `cd` 到脚本所在目录再给相对路径（脚本 cwd 是自身目录）。

### 三种输出格式

**`table`**（默认，先汇总再展开每个刷怪笼）：

```
查询点X  查询点Z  密室数  刷怪笼总数  怪物类型（去重）
  544      166       8      145     baby_zombie,breeze,cave_spider,...

查询点X  查询点Z  密室X  密室Z  刷怪笼X  Y  Z   怪物      实体            权重  间隔tick  同时数  同时+玩家  总数  总数+玩家
  544    166   24   696    19   -24  697  skeleton  minecraft:skeleton  1     20      3     0.5      0    0
  544    166   24   696   -17   -19  697  breeze    minecraft:breeze    1     20      1     0.5      2    1
```

**`json`**（结构化，适合程序处理）：

```bash
./run-cli.sh query --seed 188188 --coords 544,166 --radius 600 --output json
```

```json
{
  "x": 544, "z": 166, "chamberCount": 8, "spawnerCount": 145,
  "chambers": [
    { "x": 24, "z": 696, "spawners": [
      { "x": 183, "y": -36, "z": -254, "mob": "poison_skeleton",
        "config": "minecraft:trial_chamber/ranged/poison_skeleton/normal",
        "entity": "minecraft:bogged", "weight": 1,
        "ticksBetweenSpawn": 20, "simultaneousMobs": 3.0, "..." } ] }
  ]
}
```

**`csv`**（每刷怪笼一行，含全部列）：

```bash
./run-cli.sh query --seed 188188 --file coords.csv --radius 1000 --output csv
```

### 每个刷怪笼的字段

| 字段 | 含义 |
|---|---|
| `mob` | 怪物类型（如 `skeleton`、`breeze`、`poison_skeleton`） |
| `entity` | 实际生成实体 id（如毒骷髅→`minecraft:bogged`） |
| `config` | 配置文件 id（如 `.../ranged/skeleton/normal`） |
| `weight` | 生成权重 |
| `ticksBetweenSpawn` | 生成间隔（tick，20 tick = 1 秒） |
| `simultaneousMobs` | 同时生成数 |
| `simultaneousMobsPerPlayer` | 每玩家同时加成 |
| `totalMobs` / `totalMobsPerPlayer` | 总生成数 / 每玩家加成 |

### 宝库（Vault）查找

每个密室除了刷怪笼，还列出其中的**宝库**（Vault）位置与类型。宝库是试炼密室的通关奖励入口：玩家用**试炼钥匙**（普通宝库）或**不祥试炼钥匙**（不祥宝库）开启，消耗钥匙换取宝库内的奖励。

#### 三种输出格式

**`table`**（默认）：每个密室下方多出 `宝库X Y Z 类型` 明细表，密室汇总行也显示宝库总数：

```
  密室 #3  坐标 (840, 104)  刷怪笼 17 个  宝库 17 个
     种类: 4× breeze (minecraft:breeze), 6× stray (minecraft:stray), ...

刷怪笼X  Y    Z   怪物      实体  ...          ← 刷怪笼明细
  ...                                          ← （原有，见上）

宝库X    Y    Z   类型                          ← 宝库明细
  851  -45   80  不祥宝库
  866  -27  125  普通宝库
  855  -23  100  普通宝库
  ...
```

**`json`**：每个密室带 `vaults` 数组，每项 `{ "x", "y", "z", "ominous" }`（`ominous: true` = 不祥宝库，`false` = 普通宝库）：

```json
{
  "x": 544, "z": 166, "chamberCount": 3, "spawnerCount": 57,
  "chambers": [
    { "x": 840, "z": 104, "spawners": [ "..." ],
      "vaults": [
        { "x": 851, "y": -45, "z": 80, "ominous": true },
        { "x": 866, "y": -27, "z": 125, "ominous": false }
      ] }
  ]
}
```

**`csv`**：每行末尾新增 `宝库` 列，格式 `x,y,z(不祥)`，多个宝库用 `|` 分隔：

```
查询点X;查询点Z;密室X;密室Z;刷怪笼X;...;总数+玩家;宝库
  544;  166;  840;  104;  851;-42;88;breeze;...;  1;851,-45,80(不祥)|866,-27,125
```

#### 宝库类型

| 类型 | 结构模板 | 开启钥匙 |
|---|---|---|
| **普通宝库** | `trial_chambers/reward/vault` | 试炼钥匙（普通刷怪笼通关掉落） |
| **不祥宝库** | `trial_chambers/reward/ominous_vault` | 不祥试炼钥匙（不祥刷怪笼通关掉落） |

> 宝库坐标是**精确方块坐标**，游戏内可直接 `/tp x y z` 传送。`--cache` 会把宝库位置随刷怪笼一起缓存，重复查询直接命中。

### 完整参数

`--seed`（必填）、`--coords`、`--file`、`--radius`（默认 1000）、`--output`（table/json/csv）、`--cache`/`--cache-dir`（复用 B 流缓存加速重复查询）、`--threads`、`--no-gpu`、`--debug`。

### 复用搜索结果的完整流程

```bash
# 1. 先做一次搜索（默认全量，不截断）
run-cli.bat --seed 188188 --search-radius 100000 --cluster-radius 1000 --min-structures 3 --min-spawners 20

# 2. 对 top 结果做定点查询（用生成的 results CSV 作为查询点）
run-cli.bat query --seed 188188 --file results-<时间戳>.csv --radius 1000 --output table
```

这样能精查搜索发现的最密集区域，看每个密室的刷怪笼构成和宝库位置。

---

## B 流缓存（`--cache`，默认禁用）

默认禁用是有意的：低命中率时磁盘 I/O 反而拖慢。**重复搜索同一种子 / 重叠查询点才建议开启**。每个种子一个紧凑二进制文件 `spawners_<seed>.bin`（同 seed 所有密室合一，含刷怪笼坐标 + 怪物类型 + 配置 + 宝库位置），避免成百上千个零散小文件。

### 断点快扫（缓存的核心价值）

`--cache` 能让你**中断后从断点继续快扫**：

```bash
# 第一次：启用缓存完整扫描
run-cli.bat --seed 188188 --search-radius 300000 --cache --cache-dir ./cache

# 中断后重跑：已生成的密室直接从缓存命中，只补扫未完成的区域
run-cli.bat --seed 188188 --search-radius 300000 --cache --cache-dir ./cache
```

- **同一种子**：已缓存的密室（seed+chunk）直接命中，跳过 Jigsaw 拼接，大幅提速。
- **调整参数后重扫**：即使改了 `--cluster-radius`/`--min-spawners` 等聚类参数，B 流生成结果不变，缓存依然命中——只重新聚类，不重新生成密室。
- **大半径 / 全图扫描**：分片进度 + 缓存复用，中断恢复后进度从断点继续，不必从头跑。

> 缓存按 seed+密室坐标存储，与聚类/筛选参数无关，因此换参数不失效。首次扫描会把所有访问过的密室写入缓存（每 5000 个密室自动落盘 + 结束兜底落盘），中断后重扫从断点继续，后续（含中断重扫）直接复用。

### 精度：默认全量（`--top-k` 已隐藏）

默认**不启用 top-K 截断**——所有粗聚类都进 B 流生成，结果完全精确（只受 `--min-structures`/`--min-spawners` 过滤影响）。`--top-k` 参数已隐藏，仅高级用户可显式传入 `--top-k N` 启用截断（更快但可能漏结果）。

```bash
# 默认全量 + 断点续扫（配合 --cache，重复扫描/调参时密室不会重算）
run-cli.bat --seed 188188 --search-radius 300000 --cache --cache-dir ./cache
```

---

## 预测预筛 + 有界验证（`--predict-depth` / `--predict-gate`，默认禁用）

大半径搜索时 B 流（完整 Jigsaw 拼接）是主要耗时。预测预筛在 **A 流密度筛选之后、完整 B 流之前**加一道快速筛选：对聚类成员用**手写轻量布局**预测刷怪笼上界（`LightJigsawPlacement`，与完整生成 RNG 逐位一致），按**流式分批**处理——每批预测 → 剪枝 → 生成存活簇 → 更新榜单截止（cutoff），后续批次剪掉更多。

```
A 流枚举 → 密度筛选 → 聚类 → [流式预测+剪枝+生成] → 榜单（top-100/结构数）
```

- **手写轻量布局**：LightPiece + ConnectorBuffer + ElementMetadata 缓存，预测器单密室 15ms→8ms，与完整生成**逐位一致**（不误剪）
- **流式流水**：每批预测少量成员 → 立即用当前 top-100 cutoff 剪枝 → 生成存活簇，早期强结果抬高 cutoff，后期剪掉更多
- **无损剪枝**：只剪"预测上界 < minSpawners"或"上界进不了 top-100 榜单"的簇，结果完全精确
- **总进度显示**：预测 + 生成两阶段进度条（`[Density]`/`[B-Flow]`）+ ETA

### 怎么选 gate（用标定脚本测你的场景）

`gate` 的合适值**依赖具体场景**（种子、半径、`--min-spawners`）——没有万能值。用内置标定脚本对目标种子测出召回表：

```bash
./gradlew runPredictCalibration
```

输出示例（1908 密室，`--predict-depth` 行 × `gate` 列的**召回率**，即真实刷怪笼 ≥ gate 的密室中保留的比例）：

```
D     gate     kept       dropped    recall
10    4        1908       0          1.000
10    8        1896       2          0.999
10    12       1731       25         0.986
10    20       424        65         0.867
```

**读法**：
- 目标是 `recall` 接近 1.0（不漏）时尽量多 `dropped`（加速）
- `D=10`（覆盖 ~98% 刷怪笼）：`gate=8` 召回 0.999、`gate=12` 召回 0.986——推荐
- 想要更大加速可提高 gate，但 recall 下降（会漏）
- 想要绝对无损：不设预测参数（默认禁用），或 `--predict-gate` 设很小

### 使用示例

```bash
# 大半径搜索，D=10 预测 + gate=12（召回 ~0.99，跳过低分簇）
run-cli.bat --seed -6523988883445283364 --search-radius 1000000 --cluster-radius 128 \
  --min-structures 2 --min-spawners 40 --threads 14 \
  --predict-depth 10 --predict-gate 12

# 更激进加速（可接受少量漏）
run-cli.bat --seed -6523988883445283364 --search-radius 1000000 --cluster-radius 128 \
  --min-structures 2 --min-spawners 40 --threads 14 \
  --predict-depth 10 --predict-gate 20

# finder.properties 配置
# predict-depth=10
# predict-gate=12
```

运行时的日志（`--debug`）：
- 进度条：`[Density] 88% 7417/8441 | 306 座/秒 | ETA 00:00:03`（预测阶段）
- 进度条：`[B-Flow] 20% 212/1046 | 148 座/秒 | ETA 00:00:05`（生成阶段）
- 摘要：`[cluster-predict] 6451 clusters pruned by upper-bound prefilter (gate=18); 204 kept`

---

## `--biome-check`（近似可用）

按生物群系过滤候选，排除**海洋/深海底/沙滩**（不生成试炼密室）等，保留陆地。**不是逐点精确**：温度/湿度维度精确，大陆度/侵蚀/深度/离岸度用确定性近似（`TerrainProvider` 样条表未移植）。实测 10k 半径 1058→716 候选通过。陆地坐标可能解析为略有差异的陆地生物群系，但对"排除海底密室"足够。

---

## 示例

### 推荐参数（30 万格半径找密集区）

```bash
run-cli.bat --seed -6523988883445283364 --search-radius 300000 --cluster-radius 256 --min-structures 3 --min-spawners 70 --threads 14 --debug
```

**逐参数解析**：

| 参数 | 值 | 含义 | 为什么这么设 |
|---|---|---|---|
| `--seed` | `-6523988883445283364` | 世界种子 | 目标种子 |
| `--search-radius` | `300000` | 以 (0,0) 为圆心的搜索半径（方块），即 30 万格 | 覆盖较大范围；默认 cluster 预筛，如需更快可显式 `--prefilter-mode grid` |
| `--cluster-radius` | `256` | 密度聚类半径（方块） | 密室间距约 544 块，256 格能聚起 2-3 个相邻密室。**不要 < 544 的一半**，否则聚不出聚类 |
| `--min-structures` | `3` | 一个聚类内至少 3 个密室 | 与 `cluster-radius 256` 匹配（256 格内三联密室较常见；若用 128 格建议降为 2） |
| `--min-spawners` | `60` | 密度圆内至少 70 个刷怪笼 | 较高阈值 → 只保留"真正密集"的区域，结果少而精（实测 10 个） |
| `--threads` | `14` | B 流（Jigsaw 拼接）CPU 线程数 | 接近物理核心数；不要超过逻辑核心数 |
| `--debug` | — | 打印配置摘要、进度、耗时 | 首次运行建议开启，能看到 auto-tune 的实际取值 |

**运行时会看到**：
```
config      : seed=-6523988883445283364 searchRadius=300000 ...                  ← 配置摘要
candidates  : 955,421   pruned: 929,671   results: 10                            ← 最终结果
```

> 想让结果更多？调低 `--min-spawners`（如 40）；想更快？减小 `--search-radius`。

### 其他常用示例

```bash
# 快速验证（约 6 秒）
run-cli.bat --seed 188188 --search-radius 1000000 --cluster-radius 256 --threads 14

# 常规搜索（找密集区，默认全量不截断）
run-cli.bat --seed 188188 --search-radius 100000 --cluster-radius 1000 --min-structures 3 --min-spawners 20 --threads 14

# 全图流式扫描（默认全量，内存有界逐分片）
run-cli.bat --seed 188188 --full-world --tile-size 100000 --tile-overlap 1000 --threads 14

# 生物群系过滤（排除海底密室）
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
# 独立 fat JAR: build/libs/trialfinder-1.4.0.jar
# 可选: 重新生成预编译 cubin (需 nvcc + MSVC)
./gradlew compileCubin
```

- **`shadowJar`**：独立 CLI fat JAR（主类 `cn.trialfinder.cli.TrialFinderCLI`）
- **测试**：JUnit 用例（GPU/CPU 逐位一致由测试保证）

---

## 已知限制

- `--biome-check` 为近似（见上）
- `--prefilter-mode grid` 默认（`--top-k 0`）无损：保留所有占用网格单元 + 密度剪枝，结果与 cluster 模式一致；显式 `--top-k N` 会截断低密度单元（更快但可能漏结果）
- GPU 原生库仅随附 Windows x86_64；Linux/macOS 用 `--no-gpu`
- `--search-radius 10,000,000+` 即使自动分片也需数分钟到数小时；建议用 `--full-world`（逐分片，内存有界）或减小半径
