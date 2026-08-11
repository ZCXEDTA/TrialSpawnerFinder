# TrialSpawnerFinder 项目逻辑总结报告

> 版本：1.0.0　|　Minecraft 1.21.11（Mojang 映射）　|　JDK 21　|　CUDA 13.x
> 报告基于当前 `main` 分支源码（约 8,900 行主代码 + 2,300 行测试），逐文件核对。

---

## 目录

1. [项目概览](#1-项目概览)
2. [整体架构](#2-整体架构)
3. [搜索流程（数据流）](#3-搜索流程数据流)
4. [关键算法](#4-关键算法)
5. [GPU 加速](#5-gpu-加速)
6. [全图扫描（分片流式）](#6-全图扫描分片流式)
7. [定点查询 `--query`（未实现）](#7-定点查询-query未实现)
8. [命令行参数说明](#8-命令行参数说明)
9. [性能特征与已知瓶颈](#9-性能特征与已知瓶颈)
10. [已知限制与未来优化方向](#10-已知限制与未来优化方向)
11. [测试与验证状态](#11-测试与验证状态)
12. [未使用参数 / 死代码标注](#12-未使用参数--死代码标注)

---

## 1. 项目概览

**用途**：在 Minecraft Java 版 1.21.11 世界中，给定世界种子，定位"试炼密室（Trial Chambers）"的高密度聚集区域，并统计每个聚类中心在 `cluster-radius` 范围内的**真实刷怪笼数量**，输出按刷怪笼数降序排列的结果 CSV/TXT。

**两条运行路径**：

| 路径 | 入口 | 依赖 | 说明 |
|---|---|---|---|
| 纯命令行工具（推荐） | `cn.trialfinder.cli.TrialFinderCLI` | 无 Minecraft 服务端 | GPU 加速 A 流/密度预筛，B 流 Jigsaw 在 CPU 线程池 |
| 原版服务端 Mod | `cn.trialfinder.TrialSpawnerFinderMod` | Fabric + 1.21.11 服务端 | 通过真实 Minecraft 结构生成验证基准 |

**核心验证事实**：`cn.trialfinder.sim.*` 是自包含的 1.21.11 生成模拟，与真实 1.21.11 服务端对 1,058/1,058 座密室的刷怪笼坐标逐位一致（详见第 11 节）。

---

## 2. 整体架构

### 2.1 包结构

```
cn.trialfinder
├── cli/        # 命令行入口、搜索编排、进度/输出
│   ├── TrialFinderCLI       # picocli 参数解析 + 4 条运行路径分派
│   ├── SearchEngine         # 核心搜索流水线（精确 + top-K 两条）
│   ├── WorldTiler           # 全图分片（步进重叠 + 确定性归属）
│   ├── SearchRegion         # 矩形搜索区域 record
│   ├── ResultEntry          # CSV 序列化结果行
│   ├── ResultMerger         # N 路归并 + 每结构数 top-N 截断
│   ├── ProgressRenderer     # 带阶段名的进度条（线程安全、限频）
│   └── SearchRegion/WorldTiler 等
├── accel/      # GPU/CPU 加速器抽象
│   ├── Accelerator          # 接口：findChunks / pruneByDensity / densityScores
│   └── CpuAccelerator       # 纯 Java ground-truth 实现
├── cuda/       # CUDA 桥接
│   ├── GpuAccelerator       # JCuda 驱动 API（启动核、内存、回退）
│   ├── Nvrtc / NvrtcCompiler # JNA 调 nvrtc64_*_0.dll 运行时编译 .cu→PTX
│   └── PtxCompiler          # ptxas 把 PTX→sm_<cc> cubin
├── sim/        # 自包含 Minecraft 1.21.11 生成模拟（无 net.minecraft 依赖）
│   ├── SimChamberGenerator  # 顶层：A+B+C 三流串联
│   ├── data/TrialChambersData # 放置/结构/别名常量
│   ├── random/              # Legacy/Xoroshiro LCG 逐位复刻
│   ├── structure/           # jigsaw 拼接（JigsawPlacement$Placer 等）
│   ├── nbt/                 # gzip NBT 读取子集
│   └── math/ resources/ util/ # 值类型与工具
├── search/     # 精确聚类与密度统计（CLI 与 Mod 共用）
│   ├── TrialChamberCandidates # 34×34 网格候选枚举（A 流，纯 Java）
│   ├── CircleClusters / SquareClusters # 精确聚类
│   ├── ExactCenterOptimizer # 差分扫描找最优整数中心
│   └── ShardedClusterScanner # Mod 路径的分片扫描
├── config/     # FinderConfig（Mod 用 .properties）
├── io/         # ResultWriter（CSV+TXT）
├── world/      # TrialChamberGenerator（Mod 路径真实结构生成）
├── mixin/      # PaletteCacheMixin（并发安全）
└── model/      # BlockPoint / SpawnerPoint / SearchResult 等
```

### 2.2 模块依赖关系

```
TrialFinderCLI ──→ SearchEngine ──→ Accelerator (CPU/GPU)
                       │              └─→ GpuAccelerator → JCuda + NVRTC + ptxas
                       │              └─→ CpuAccelerator
                       ├─→ SimChamberGenerator（B 流 Jigsaw，CPU 线程池）
                       ├─→ CircleClusters / ExactCenterOptimizer（精确聚类/统计）
                       └─→ WorldTiler → ResultMerger → ResultWriter

TrialSpawnerFinderMod ──→ FinderSearch ──→ TrialChamberGenerator（真实服务端）
                                        └─→ ShardedClusterScanner → CircleClusters
```

**关键解耦**：`accel.Accelerator` 接口隔离 GPU/CPU；`sim.*` 完全自包含（无 `net.minecraft` import），
既被 CLI 用于模拟，也被测试用于与真实服务端比对。

---

## 3. 搜索流程（数据流）

### 3.1 精确模式（`--top-k 0`，默认）

```
输入参数 → SearchEngine.run(opts, acc, progress)
  │
  ├─ [1] A 流枚举（分片）: acc.findChunks(seed, tile±margin, circle?)
  │        每 tile: 34×34 网格 LCG 生成候选坐标（方块，chunk*16+8）
  ├─ [2] GPU 密度预筛: acc.pruneByDensity(candidates, 2R, minStructures)
  │        无损剪枝：2R 邻居数 ≥ minStructures 才保留
  ├─ [3] 精确聚类: CircleClusters.find(pruned, R, minStructures)
  │        保留"首成员归属本 tile"的聚类（跨分片不重复）
  ├─ [4] B 流 Jigsaw（CPU 并行）: SimChamberGenerator.generateChamber
  │        对每个聚类的所有密室生成，收集真实刷怪笼坐标
  ├─ [5] 密度统计: ExactCenterOptimizer.find 找刷怪笼最多的整数中心
  ├─ [6] 过滤 + 排序 + 每结构数截断 100
  └─ [7] ResultWriter 输出 CSV + TXT
```

### 3.2 Top-K 聚类模式（`--top-k N`，近似加速）

```
A 流枚举 → GPU 密度评分（2R 邻居数）→ 粗聚类（密度峰值/并查集）
  → 按聚类密室数排序 → 截断保留前 N 个粗聚类
  → B 流（只生成保留聚类的所有密室）→ 精确聚类 + 精确中心 → 输出完整聚类
```

### 3.3 全图模式（`--full-world`）

```
WorldTiler 生成分片（步进 = tileSize - overlap）
→ 逐分片执行上面任一流水线（精确或 top-K）
→ 精确模式：每分片结果写临时文件 → ResultMerger N 路归并
→ top-K 模式：跨分片用有界最小堆累计全局 top-K 粗聚类 → 统一 B 流
```

---

## 4. 关键算法

### 4.1 A 流：网格定位（`TrialChamberCandidates` / GPU 核 `generateChunksKernel`）

复刻 Minecraft `RandomSpreadStructurePlacement`：

```
regionX = floorDiv(chunkX, 34), regionZ = floorDiv(chunkZ, 34)
randomSeed = regionX*341873128712 + regionZ*132897987541 + seed + 94251327
LCG(seed 兼容 java.util.Random, 含拒绝采样 nextInt(22))
chunkX = regionX*34 + nextInt(22);  chunkZ = regionZ*34 + nextInt(22)
候选 = (chunkX*16+8, chunkZ*16+8)   # 方块坐标
```

- **确定性**：纯函数 of (seed, regionX, regionZ)，天然可并行 → GPU 每线程一个 region。
- **跨版本一致**：1.21.1 与 1.21.11 该算法相同（已用 `java.util.Random` 独立交叉验证）。

### 4.2 密度评分 / 预筛（`Accelerator.densityScores` / `pruneByDensity`）

对每个候选统计 **2R**（R=cluster-radius）范围内的邻居数（欧氏距离，含自身）：

- `pruneByDensity`：保留 `count ≥ minStructures` 的候选。**无损**：任何能成为达标聚类成员的
  候选，其 2R 邻居数必 ≥ minStructures（聚类内任意两点距离 ≤ 2R）。
- `densityScores`：返回每个候选的 2R 邻居数，供 top-K 排序用。
- 实现：空间网格（cell=2R，3×3 邻域查询），CPU/GPU 逐位一致。

### 4.3 粗聚类（top-K 前置，`SearchEngine`）

**默认 `--cluster-method density`**（密度峰值 + KD-tree）：

1. 每个候选的密度 = 2R 邻居数（GPU 粗筛分数）；
2. 用 `SpatialIndex`（KD-tree）查询每个候选在 2R 内"密度更高"的最近密室（`better()` 用
   `score 降序 → X 升序 → Z 升序` 定义全序，保证指针无环）；
3. 链终止于**密度峰值**，每个峰值 + 吸引域 = 一个粗聚类；
4. 超过 `maxClusterSize` 的粗聚类用 `radius/2` **递归拆分**，保留高密度核心。

**`--cluster-method legacy`**（并查集）：距离 ≤ 2R 连通的候选合并成一个粗聚类。

> 正确性保证：粗聚类链接距离 = 2R，故任何最终精确聚类（成员两两 ≤ 2R）都**完整落在单个粗聚类内**
> → 聚类级截断不会打散精确聚类。

### 4.4 Top-K 截断（`TrialFinderCLI`）

- 单区域：粗聚类按 `size` 降序取前 N。
- 全图：`PriorityQueue<CoarseCluster>` 用 `COARSE_WORST_FIRST`（size 升序、X/Z 降序）作有界堆，
  `poll()` 移除最小簇 → 存活者 = 全局 top-K（与"单区域排序取前 K"逐元素一致，确定性）。
- `--top-k 0` = 不截断，保持精确模式。

### 4.5 B 流：Jigsaw 拼接（`SimChamberGenerator` → `JigsawPlacement`）

自包含复刻 1.21.11 数据驱动 Jigsaw：

- 池/模板从 classpath 加载（47 池 JSON + 191 模板 NBT）；
- `JigsawPlacement$Placer.tryPlacingChildren` 递归拼接，**RNG 消费顺序逐位一致**；
- 关键适配：vanilla `BoundingBox.move()` 是**原地修改**（修复了 pre/post-move 盒差异）；
- 只读模板，不写世界，可安全并行（每任务新建 generator 实例，共享只读 manager）。

### 4.6 精确聚类 + 密度统计（`CircleClusters` + `ExactCenterOptimizer`）

- `CircleClusters`：两圆交点生成候选圆心 → 网格 3×3 邻域收集成员 → `bestIntegerCenter`
  找最小整数中心 → 按成员列表去重。
- `ExactCenterOptimizer`：对每个聚类，扫描 Z 行 + 差分数组，找**覆盖全部密室起点**且
  **刷怪笼最多**的整数中心（`spawner 多 > X 小 > Z 小`）。
- 与 1.21.11 服务端 `FinderSearch` 逐位一致（CLI 精确模式直接复用）。

---

## 5. GPU 加速

### 5.1 哪些阶段在 GPU 上

| 阶段 | GPU 核 | 说明 |
|---|---|---|
| A 流候选枚举 | `generateChunksKernel` | 每线程一个 (regionX,regionZ)，复刻 LCG + 拒绝采样，`atomicAdd` 压缩写 `int2[]` |
| 密度预筛/评分 | `densityCellCount` + `densityScatter` + `densityCount` | 空间网格，3 个小核 + 主机侧前缀和 |
| **B 流 Jigsaw** | — | **纯 CPU**（`SimChamberGenerator`，线程池并行）——这是性能瓶颈 |
| 精确聚类/统计 | — | 纯 CPU |

### 5.2 回退机制

`GpuAccelerator` 每个方法捕获异常回退到 `CpuAccelerator`；`create()` 初始化失败（无 GPU/驱动/
编译失败）则 CLI 整体回退 CPU 并打印原因。

### 5.3 运行时编译链（无 MSVC 依赖）

```
.cu 资源 → NVRTC(JNA) → PTX 9.3 → ptxas(独立二进制) → sm_<cc>.cubin → cuModuleLoadData
```

关键适配（本机实测）：
- JCuda 用 12.6.0（0.9.2 的 natives 是空壳）；
- NVRTC 走 JNA 直接调 `nvrtc64_*_0.dll`（JCuda 自带 JNvrtc 按 CUDA 小版本链接会失败）；
- NVRTC 13.3 输出 PTX 9.3 而驱动是 CUDA 13.0 → 用 ptxas 装成 cubin 绕过 `UNSUPPORTED_PTX_VERSION`。

---

## 6. 全图扫描（分片流式）

`WorldTiler`（全部以**方块**为单位）：

- 世界范围 ±30,000,000，默认 `tile-size=100,000`、`tile-overlap=1,000` → `step=99,000`
  → 每维 607 分片，共 368,449 个；
- 相邻分片重叠 `overlap`，边界分片收窄；
- **确定性归属** `owns()`：每个点唯一指派给包含它的最小 tileId 分片 → 每聚类只被一个分片计分，
  无需全局去重状态。

两阶段流式：
- **精确模式**：逐分片 `searchRegion` → 结果写临时文件 → `ResultMerger`（优先队列 N 路归并，
  两阶段 fan-in ≤ 512，每结构数 top-N=100）→ 合并后删临时文件；
- **top-K 模式**：逐分片粗聚类 → 有界最小堆累计全局 top-K → 统一 B 流。

内存管理：分片内候选/刷怪笼/结果为局部变量，方法结束即回收；每 1000 分片 `System.gc()` 提示。

---

## 7. 定点查询 `--query`（未实现）

> **重要说明**：任务背景描述的"定点查询（`--query`）、缓存模式/实时模式、采样"等特性
> **在当前代码中不存在**。已逐文件核对：
> - `TrialFinderCLI` 无 `--query` 参数（grep 确认 0 处）；
> - 无采样逻辑（"sample"仅出现在测试注释与 `sampleSpawners` 字段名，非功能）；
> - `cache` 出现处均为结构生成缓存（`FinderSearch`）、模板缓存（`StructureTemplateManager`）、
>   Mixin 并发安全缓存，非"定点查询缓存模式"。

如需定点查询（给定坐标查询该处密室/刷怪笼密度），可作为后续功能添加，建议复用
`SimChamberGenerator.generateChamber`（单密室生成，~10ms）。

---

## 8. 命令行参数说明

| 参数 | 默认 | 作用 | 适用模式 |
|---|---|---|---|
| `--seed` | 必填 | 世界种子 | 全部 |
| `--search-radius` | 10000 | 以 (0,0) 为圆心的搜索半径（方块） | 单区域 |
| `--cluster-radius` | 1000 | 密度聚类半径 R（方块） | 全部 |
| `--min-structures` | 3 | 聚类内至少密室数 | 全部 |
| `--min-spawners` | 20 | 密度圆内至少刷怪笼数 | 全部 |
| `--full-world` | false | 全图流式扫描 | — |
| `--tile-size` | 100000 | 分片边长（方块） | full-world |
| `--tile-overlap` | 1000 | 分片重叠（方块） | full-world |
| `--top-k` | 0 | 粗聚类 top-K 截断（0=不截断） | top-K |
| `--cluster-method` | density | 粗聚类方法（density/legacy） | top-K |
| `--max-cluster-size` | 0 | 密度聚类拆分阈值（0=自动 max(200, n/10)） | top-K |
| `--output-prefix` | `results-<时间戳>` | 输出前缀（生成 .csv/.txt） | 全部 |
| `--threads` | 4 | B 流 Jigsaw CPU 线程数 | 全部 |
| `--debug` | false | 打印阶段耗时 | 全部 |
| `--no-gpu` | false | 强制纯 CPU | 全部 |
| `--quiet` | false | 关闭进度条/阶段输出 | 全部 |

Mod 路径（`finder.properties`）：`seed / search-center-x / z / search-radius-blocks /
full-world / cluster-radius-blocks / area-shape / min-structures / min-spawners /
scan-threads / scan-shard-size-blocks`。

---

## 9. 性能特征与已知瓶颈

### 9.1 各阶段耗时占比（单区域实测，GPU + CPU 8 线程）

| 阶段 | 量级 | 说明 |
|---|---|---|
| A 流枚举 | 毫秒级 | 10k 半径 ~1,000 候选；GPU 秒级即可全图 |
| 密度预筛/评分 | 毫秒~秒级 | GPU 网格计数 |
| **B 流 Jigsaw** | **主导瓶颈** | 每密室 ~10ms；10 万密室 × 10ms / 8 线程 ≈ 2 分钟 |
| 精确聚类/统计 | 秒级 | `ExactCenterOptimizer` 逐聚类 |
| 结果输出 | 毫秒级 | CSV/TXT 流式写 |

**核心结论**：**B 流是绝对瓶颈**（真实 Jigsaw 拼接无法 GPU 化）。GPU 只加速"决定要看哪里"
（A 流 + 密度粗筛），不加速"去看"（B 流）。top-K 正是为压缩 B 流工作量而设：
`--full-world --top-k 100000` 把 B 流限制在 10 万密室（分钟级）。

### 9.2 性能对比（seed 188188, radius 10000, cluster-radius 1000, `--top-k 20`）

| 方法 | 粗聚类数 | 保留 | 输出精确聚类 |
|---|---|---|---|
| legacy（并查集） | 1（巨型） | 1 | 21,988 |
| density（max-size 50） | 51 | 20 | 11,793 |

密度聚类把大半径下的"单巨型聚类"拆成密度核心，top-K 真正生效。

---

## 10. 已知限制与未来优化方向

**已知限制**

1. **B 流无法 GPU 化** → 全图精确扫描（`--full-world` 无 top-K）现实不可行（数月级）。
2. **top-K 是近似**：被截断的粗聚类不输出；`--min-spawners` 越高越稳。
3. **大 `--cluster-radius` 时密度聚类可能过度拆分**（需调 `--max-cluster-size`）。
4. **`ExactCenterOptimizer` 仍是逐聚类串行**（每聚类 O(Z 行 × 差分)）。
5. **GPU 依赖 NVIDIA**：无 GPU 自动回退 CPU，但无 GPU 时全图不可行。

**未来优化方向**

1. **并行化 `ExactCenterOptimizer`**：聚类间独立，丢线程池即可（纯 CPU，无 Amdahl 钳制）。
2. **精确聚类改为增量/分片**：`CircleClusters` 对超大点集仍 O(n·k)。
3. **B 流模板级缓存**：同一模板 + 旋转的 `trial_spawner` 变换结果可缓存（版本锁 1.21.11）。
4. **`--query` 定点查询**：给定坐标 → `generateChamber` 单点生成（见第 7 节）。
5. **CUDA cubin 预编译**：`compileCuda` 任务可在有 MSVC 的构建机预生成 PTX/cubin，运行时免 NVRTC。

---

## 11. 测试与验证状态

| 类别 | 数量 | 说明 |
|---|---|---|
| 全部 JUnit | 70 | 全部通过（`./gradlew clean test remapJar`） |
| sim 包 | 12 | RNG/A 流/C 流/密室生成 |
| cli 包 | 18 | WorldTiler / ResultMerger / 全图流式等价 / top-K / 密度聚类 / 进度条 |
| cuda 包 | 4 | GPU vs CPU 逐位一致（findChunks / densityScores / prune / 全流水线） |
| search/config/io | 23 | 聚类 / 中心优化 / 候选 / 写盘 |
| 手工验证类 | 3 | `TestStandalone` / `SearchVerification` / `SimChamberDump` |

**与真实服务端比对**：seed 188188 下，`sim` 生成的 **1,058/1,058** 座密室刷怪笼坐标与
1.21.11 服务端**逐一一致**；A 流候选/聚类数 1,062/1,072（seed -9206294873968313284）与
服务端日志精确一致。

---

## 12. 未使用参数 / 死代码标注

| 位置 | 说明 |
|---|---|
| `--query`（任务背景提到） | **不存在**（见第 7 节） |
| `SearchEngine.scoreRegion`（密室级评分） | 保留，被 `coarseClustersForRegion` 内部复用；`generateChambers`（密室级 top-K）**已不暴露给 CLI**，仅测试仍引用 |
| `SimChamberMain` / `SimSweepMain` / `SimVerifyMain` | 早期调试/验证入口，保留但非主流程 |
| `search/FinderSearch` 的 `dumpChamberSpawners` / `TrialSpawnerFinderMod` 的调试钩子 | 已移除（干净） |
| `ResultMerger` `twoPhaseMergeHandlesManyFiles` 测试注释 | 保留的 fan-in 逻辑实际会触发两阶段 |
| Mod 路径 `area-shape=square` / `SquareClusters` | CLI 未暴露该选项（CLI 固定 circle），Mod 路径可用 |
