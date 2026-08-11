# TrialSpawnerFinder

用于在 Minecraft Java 版 1.21.11 世界种子中查找试炼密室密集区域，并统计指定圆形半径内实际生成的试炼刷怪笼。

## 两种运行方式

### 方式一：命令行工具（CUDA 加速，推荐）

纯 Java 命令行工具，不依赖 Minecraft 服务端。A 流（密室网格定位）和密度预筛在 GPU 上执行，B 流（Jigsaw 拼接）在 CPU 线程池并行。GPU 不可用或启动失败时自动回退纯 CPU。GPU 加速**只需要 NVIDIA 驱动**：内核已预编译为 cubin 打包进 JAR，按 GPU 架构直接加载，无需安装 CUDA Toolkit。

```bash
# 基础用法（JDK 21 + NVIDIA GPU + CUDA 驱动）
./run-cuda.sh --seed 188188 --search-radius 10000 --cluster-radius 1000 --min-structures 3

# Windows
run-cuda.bat --seed 188188 --search-radius 10000 --cluster-radius 128 --min-structures 1 --min-spawners 20

# 或直接通过 Gradle
./gradlew run --args="--seed 188188 --search-radius 10000 --debug"
```

参数：

| 参数 | 默认 | 说明 |
|---|---|---|
| `--seed` | 必填 | 世界种子 |
| `--search-radius` | 10000 | 以 (0,0) 为圆心的搜索半径（方块）；`--full-world` 时忽略 |
| `--cluster-radius` | 1000 | 密度聚类半径（方块） |
| `--min-structures` | 3 | 一个聚类内至少的密室数量 |(参考:100w格内160格半径的三联密室已经很少了)一般256格为3 ,128格为2
| `--min-spawners` | 20 | 密度圆内至少的试炼刷怪笼数量 |
| `--full-world` | false | 扫描完整 6000 万 × 6000 万 世界正方形（分片流式） |(待测试)
| `--tile-size` | 100000 | `--full-world` 分片边长（方块） |
| `--tile-overlap` | 1000 | `--full-world` 相邻分片重叠量（方块） |
| `--top-k` | 0 | 粗筛 top-K 聚类数上限（0=关闭）。保留密室数最多的前 K 个粗聚类 |(待测试,不建议使用)
| `--cluster-method` | `density` | 粗聚类方法：`density`（密度峰值 + KD-tree）或 `legacy`（并查集） |
| `--max-cluster-size` | 0 | 密度聚类拆分阈值（0=自动 `max(200, totalCandidates/10)`） |
| `--prefilter-mode` | `cluster` | 初筛方法：`cluster`（密度峰值 + 粗聚类，默认）或 `grid`（GPU 网格聚合 + top-K 网格） |
| `--grid-size` | 0 | 网格边长（方块），`--prefilter-mode grid` 用（0=自动 `2*cluster-radius`） |
| `--output-prefix` | `results-<时间戳>` | 输出文件前缀（会生成 `.csv` 与 `.txt`） |
| `--threads` | 4 | B 流（Jigsaw 拼接）CPU 线程数 |(不要超过电脑逻辑核心数)
| `--debug` | false | 打印进度与耗时（含 Top-K 各阶段日志） |(有点bug)
| `--no-gpu` | false | 强制纯 CPU 路径 |
| `--quiet` | false | 关闭所有进度条/阶段输出（只保留结果摘要） |
| `--min-candidates-per-tile` | 0 | 稀疏分片预筛阈值：分片密度幸存候选数低于此值则跳过粗聚类（0=自动=`--min-structures`） |(待测试)
| `--auto-tune` / `--no-auto-tune` | 启用 | 根据 `--search-radius` 自动计算未显式指定的 `--cluster-radius`/`--grid-size`/`--top-k`（见下） |
| `--jigsaw-depth` | 0 | 浅层拼接深度（0=原版深度 20）。调小可加速 B 流但可能丢失部分刷怪笼 |

### A 流 GPU 直通（消除 Java 对象构造瓶颈）

诊断：A 流 GPU 枚举内核实际仅 **~2ms**（GPU 已跑满）；真正瓶颈是 Java 侧把千万级候选构造为 `BlockPoint` 对象（10M 对象 + GC 需 ~1.5s）。

优化：**GPU 直通 grid 预筛**（`findChunksGridPrefiltered`）——在 GPU 上完整执行「枚举 → 密度评分 → 网格聚合 → top-K 选 cell」，只把选中 cell 的少量候选传回 Java。这**复现了 host `gridAggregateAndSelect` 的 density 加权语义**（10k 半径 GPU 与 CPU 结果逐行一致），同时避免千万级对象构造。

- 复用 device 缓冲（跨 tile/跨调用，按需扩容），避免每片 ~200MB `cuMemAlloc`。
- 实测：1M 半径 6.4s（A 流从 ~1.5s 降到 ~0.4s/片）；10M 半径（10.6 亿候选）自动分片 289 片、**3 片/s、ETA ~1.5 分钟**（此前 A 流对象构造主导需数分钟）。
- 修复：density/网格 device 缓冲独立 cap（避免共享 cap 误判复用导致越界）。

### B 流性能优化（14 核 ≥ 50 座/秒）

B 流（Jigsaw 拼接）的实测优化效果（种子 188188，半径 10000，`--top-k 0` 全量，14 线程 GPU）：

| 阶段 | 耗时 | B 流吞吐 |
|---|---|---|
| 基线（未优化） | ~37.9s | ~28 座/秒 |
| + 旋转/模板缓存（StructureTemplate 按 rotation 预计算 jigsaw 块） | — | 单线程 5.7× 提速 |
| + `FrontAndTop`/`BlockState` 缓存（消除每次 parse/rotate 分配） | — | 单线程 177→31ms/密室 |
| + 模板 NBT 预加载（初始化时加载所有模板，避免并发 I/O） | 17.8s | ~59 座/秒 |
| + `get()` 快路径（替换高并发下 `computeIfAbsent` 的 CAS 开销） | **14.2s** | **~74 座/秒** |
| + `VoxelShape` 空间哈希（重叠检测 O(n²)→O(n)） | — | 大型密室额外收益 |

关键点：
- **根因是 GC 风暴**：每密室分配 ~15MB 对象，20 密室 1.6s 内 14 次 GC。消除旋转/parse 重复分配后单线程 177→31ms/密室。
- **`ConcurrentHashMap.computeIfAbsent` 是并发扩展性瓶颈**：即使 key 命中也有 CAS 写路径开销，14 线程封顶 ~100 座/秒。改为 `get()` 快路径 + 懒 `computeIfAbsent` 后 14 线程达 **173 座/秒**（纯 B 流）。
- `--jigsaw-depth N` 可进一步截断装饰递归换取速度（精度损失需接受）。
- 14 线程 CPU 场景建议 `--threads 14`（物理核心数）。



### 自动调参（`--auto-tune`，默认启用）

当 `--cluster-radius`、`--grid-size`、`--top-k` 未显式指定（命令行或 `finder.properties` 均未设置）时，按 `--search-radius` 自动计算以平衡速度与召回率：

```text
cluster-radius = max(64, min(256, searchRadius / 200))
grid-size      = 2 × cluster-radius
top-k          = max(20, min(200, searchRadius / 1000))
```

例如：半径 100,000 → `cluster-radius 256`、`grid-size 512`、`top-k 100`；半径 10,000 → `cluster-radius 64`、`grid-size 128`、`top-k 20`。

- `--debug` 下输出 `[auto-tune] ...` 显示每次自动计算；
- 显式指定的参数**不被覆盖**；`--no-auto-tune`（或 `--auto-tune=false`）完全关闭自动调参，保留默认值；
- `--full-world` 下跳过自动调参（`search-radius` 被忽略，top-k 请显式指定，如 `--top-k 100000`）；
- **超大半径自动切 grid**：`search-radius > 100,000` 且未显式指定 `--prefilter-mode` 时自动切换到 `grid`（GPU 网格预筛），避免 cluster 模式对百万级候选的密度峰值聚类卡死。

### 超大半径搜索保护（自动分片）

半径 100 万或更大时，候选密室可达上千万。程序自动处理：
- **自动切 grid**（见上）：`--prefilter-mode grid` + `--top-k` 只对保留的网格单元做 B 流，把候选从上千万降到数百。
- **重叠分片**：自动分片 tile 之间重叠一半边长，跨 tile 边界的密集区在每个 tile 都被完整枚举 + 筛选，合并（HashSet 去重）后不遗漏跨边界成员；合并后不做二次 top-K 截断（否则会再丢）。
- **自动分片**：超大半径单区域搜索自动切成 `TARGET_TILE_CANDIDATES≈500万候选/片` 的自适应分片，逐片枚举 + grid 预筛 + 合并（内存有界），最后二次全局筛选。半径 ≤ 13 万时单片等价旧行为。
- **候选预估 + 提前警告**：用"每 34×34 chunk 区域一个密室"精确估算候选数（实测 10k≈1058、1M≈10.6M、10M≈10.6 亿），超过 5000 万时启动即打印 WARN 与建议。
- **分片进度 + ETA**：`[grid 自动分片] tile 27/289 (9.3%) 候选=58,965,998 保留=13,042 速率=1片/s ETA=00:03:41`（每秒刷新）。
- **密度网格自适应**：密度评分按 `2 × cluster-radius` 分网格，若网格数超过 1000 万会自动放大格长（无损——更粗的网格仍覆盖所有 2R 内邻居），避免 GPU 超限回退 CPU 卡死。
- **A-Flow 完成进度**：大半径枚举完成后显示 `[A-Flow] 100%`。

实测：`--seed 188188 --search-radius 1000000 --cluster-radius 160 --threads 14` 从原先卡死变为 **7.5 秒完成**（自动分片 25 片，1060 万候选 → grid 预筛到数百）。注意 `cluster-radius 160` 在 1M 半径下密室间距（~544 块）内无法聚出多个密室，结果可能为空属正常。

**注意**：`--search-radius 10,000,000` 有约 **10.6 亿候选**，自动分片下仍需约 3–4 分钟（ETA 会显示）。若需更快，用 `--full-world --top-k`（流式全球扫描）或减小半径。

### Top-K 排序优化与调试(测试中)

`--top-k` 启用时：
- **单区域模式**用**有界最小堆**（`COARSE_WORST_FIRST`）只保留 top-K 粗聚类，替代全排序——O(n log k) 而非 O(n log n)。实测 10 万粗聚类 topK=50 时**3.2× 加速**（23.7ms vs 76.6ms），结果与原全排序完全一致。
- **全图模式**跨分片累计本就使用有界堆；每个分片内的粗聚类也经 `retainTopK` 处理。
- **稀疏分片预筛**：粗聚类前先按 `score >= min-structures` 无损剪枝（密度分数低于 min-structures 的候选不可能属于合格聚类），分片幸存数低于 `--min-candidates-per-tile` 则整体跳过。
- `--debug` 输出各阶段日志：
  ```
  [DEBUG] coarse candidates: 9549 -> 9549 (score>=3) | 取 SearchRegion[...]
  [DEBUG] coarse clusters: 162 -> owned 162 | 聚类 78.3 ms
  [DEBUG] topK retain took 1.4 ms (coarse 162 -> retained 50, top 50)
  [TopK] heap=50 minScore=138
  ```
  若粗聚类数巨大且 `topK retain took` 耗时长，即有界堆已生效；若 `聚类` 耗时为主，瓶颈在密度峰值聚类本身（可加大 `--cluster-radius` 或改用 `--prefilter-mode grid` 减少输入）。

### `--prefilter-mode grid`（GPU 网格聚合初筛）

当候选数极大、CPU 粗聚类（密度峰值 + KD-tree）成为可感知开销时，可用 `--prefilter-mode grid`
把"聚合 + 筛选"整体搬到 GPU：

1. **GPU 密度评分**：复用 `densityScores`，计算每个候选的 2R 邻居数；
2. **GPU 网格聚合**（`gridAggregateKernel`）：把候选映射到边长 `--grid-size` 方块（默认
   `2*cluster-radius`）的网格，用 `atomicAdd` 累加每个网格的总密度分数；
3. **Top-K 网格筛选**：网格数远小于候选数，回 CPU 排序，保留总分数最高的前 `--top-k` 个网格；
4. **CPU B 流**：对选中网格内的所有候选直接进入 B 流（跳过粗聚类），后续精确聚类 + 密度统计不变。

```bash
./run-cuda.sh --seed 188188 --search-radius 100000 --cluster-radius 1000 \
    --prefilter-mode grid --grid-size 2000 --top-k 200
```

**说明**：
- `--prefilter-mode grid` 需要 `--top-k > 0`，否则回退到 cluster 模式并打印提示；
- 网格边界可能切割密度核心（暂不支持滑动窗口/重叠，可后续优化）；
- 若 `--top-k` ≥ 网格总数，则保留全部网格（相当于不截断）；
- **精度略低于 cluster**（网格按总密度而非真实刷怪笼排序），但初筛更快，适合"只关心整体密度高"
  的场景。实测（radius 3000, grid 512, topK 60）对基线 Top-10 的召回率约 60%。

### 带阶段的进度条

进度条带**阶段名称**（英文 ASCII，固定宽度 10，`%-10s` 精确对齐），方便在长时间运行（如全图扫描）时判断当前处于哪个阶段、是否卡住：

```
[A-Flow   ] [##########] 100%  1058/1058 | 25000.0 座/秒 | ETA 00:00:00
[B-Flow   ] [####------]  40%   423/1058 |    12.5 座/秒 | ETA 00:00:45
[Stat     ] [##########] 100%  1058/1058 |   837.1 座/秒 | ETA 00:00:00
```

阶段依次为：`A-Flow`（A 流枚举）、`Density`（GPU 密度预筛）、`B-Flow`（B 流拼接）、`Stat`（密度统计）、`Sort`（结果排序）、`Output`（结果输出）。B 流拼接阶段按密室完成数增量刷新（多线程安全、每 100ms 限频）；其余单发阶段在完成时输出 100% 行。`--quiet` 时全部隐藏。

### 全图扫描的分片进度（`--full-world`）(现在带自动分片)

全图扫描把世界切成数十万块分片逐块处理。除分片内的 `B-Flow` 进度条外，还在分片之间显示一个**全局分片进度行**，避免长跑时误以为卡住：

```
[全图扫描] [#####-----]  45% Tile 165,124/368,449 | 密室: 1,234,567 | 刷怪笼: 18,901,234 | 耗时: 01:23:45 | ETA: 01:40:55
[全局Top-K] [####------] 40.0% Tile 147,379/368,449 | 密室: 12,345,678 | 刷怪笼: 100,000 | 耗时: 00:52:10 | ETA: 01:18:15
```

- 流式路径（`--top-k 0`）阶段名为 `全图扫描`，显示累计密室数与刷怪笼数；
- **Top-K 路径**（`--full-world --top-k N`）阶段名为 `全局Top-K`，`刷怪笼` 一栏显示已进入全局有界堆的**粗聚类数**（top-K 截断后的保留量）；
- `--debug` 下每条分片更新额外输出 `[TopK] heap=... minScore=...`，即当前堆大小与截断阈值（堆中最小的聚类密室数）；
- 分片进度行每 10 个分片或每 500ms 刷新一次（`--debug` 时每个分片都刷新），`--quiet` 时完全隐藏；
- 分片总数超过 100,000 时百分比保留一位小数。

### `--top-k`（聚类级粗筛模式）(存在问题)

默认（`--top-k 0`）是逐聚类精确流水线（与 1.21.11 服务端逐位一致），但全世界的聚类/生成开销巨大。`--top-k N` 启用近似加速流水线，**以聚类为单位截断**（不会打散聚类）：

1. **GPU 粗筛**：对每个候选密室计算 2R（R=cluster-radius）范围内的邻居数量作为粗筛分数（也即密度）；
2. **粗聚类**（默认 `--cluster-method density`）：**密度峰值聚类**——每个密室指向邻域内密度更高的最近密室（距离 ≤ 2R），密度峰值及其吸引域构成粗聚类；用 **KD-tree**（`SearchEngine.SpatialIndex`）加速邻域查询。超过 `--max-cluster-size` 的粗聚类用更小的半径**递归拆分**，保留高密度核心、避免大 `cluster-radius` 时形成单个巨型聚类（可用 `--cluster-method legacy` 切回并查集）；
3. **排序 + 截断**：按粗聚类的密室数量从多到少，只保留前 N 个粗聚类；
4. **B 流（CPU）**：对保留的粗聚类中**所有**密室执行 Jigsaw 拼接，获取真实刷怪笼数量；
5. **精确聚类 + 最终排序**：对保留密室做精确聚类（`CircleClusters`）并用 `ExactCenterOptimizer` 计算真实聚类中心，按真实刷怪笼数量降序输出**完整聚类**（每个结果包含其全部成员密室）。

`--full-world --top-k 100000` 是推荐的全世界搜索方式：GPU 流式给全世界候选打分，每个分片独立粗聚类，跨分片用有界堆累计全局 top-K 聚类（确定性，不受分片处理顺序影响），然后只对保留聚类的全部密室做 B 流。这是**近似**模式——被截断的粗聚类不会输出，但保留的聚类是完整的（成员密室不被丢弃）。

### `--full-world`（分片流式扫描）

`--full-world` 将整个世界划分为边长 `--tile-size` 方块、相邻重叠 `--tile-overlap` 方块的分片，逐个分片执行「A 流 GPU → 密度预筛 GPU → B 流 CPU → 密度精算」，每个分片结果写入临时文件后立即释放内存，最后用 N 路归并（优先队列）合并出最终 CSV/TXT。重叠分片通过确定性 `owns()` 归属保证每个聚类只被一个分片计分，不会重复也不会遗漏。

```bash
./run-cuda.sh --seed 188188 --full-world --tile-size 100000 --tile-overlap 1000 --threads 8
```

**性能警告**：全世界约有数亿座密室，完整扫描即使有 GPU 也需要数小时到数十小时，且 `min-structures`/`min-spawners` 阈值越低结果越多、B 流生成越慢。建议：
- 使用较高的 `--min-spawners`（如 20+）减少达标结果数；
- 用较小 `--tile-size` 控制单分片内存（默认 100,000 方块约数百 MB）；
- 用 `-Xmx2G` 或更高堆启动，确保分片不 OOM；
- `--tile-overlap` 至少为 `2 × cluster-radius`，否则边界聚类可能被截断。

GPU 加速使用**预编译 cubin**：发布包内置 `trial_finder_sm_89/sm_86/sm_75.cubin`（`src/main/resources/cuda/`），运行时检测 GPU 架构（如 `sm_89`）直接 `cuModuleLoadData` 加载，**无需 CUDA Toolkit**。若 GPU 架构不在内置列表（如 sm_61 等较老架构）、cubin 缺失或加载失败，会自动回退到 NVRTC + ptxas 运行时编译（此时才需要 CUDA 工具包）。GPU 与 CPU 路径的结果完全一致（有 JUnit 保证）。

需要自定义架构或重新生成 cubin 时（要求已安装 nvcc；Windows 上还需 MSVC）：

```bash
# 默认架构：sm_89, sm_86, sm_75（CUDA 13 支持范围）
./gradlew compileCubin
# 自定义架构（CUDA 12.x 可加入 sm_61 等）：
./gradlew compileCubin -PcubinArchs=sm_89,sm_86,sm_75,sm_61
```

未安装 nvcc 或 MSVC 时该任务会打印警告并跳过，不影响其他构建。

### 方式二：原版 Minecraft 服务端（Mod）(废弃)

编辑 `finder.properties`，`powershell -ExecutionPolicy Bypass -File ./setup.ps1` 构建后双击 `run.bat` 搜索。

## 使用方法

1. 编辑 `finder.properties`，填写世界种子、搜索范围和阈值。
2. 在项目目录打开 PowerShell，运行 `powershell -ExecutionPolicy Bypass -File ./setup.ps1` 完成首次构建。
3. 双击 `run.bat` 开始搜索。
4. 结果按刷怪笼数量降序保存为 `results-年月日-时分秒.csv`，不会覆盖以前的结果；同名 `.txt` 是适合记事本查看的对齐版本。

如果启动失败，窗口会保留错误信息，并将完整启动日志写入项目根目录的 `launcher.log`。

`search-radius-blocks` 是从搜索中心向外查找试炼密室的圆形范围。超出世界边界的部分会自动裁掉，其余方向仍正常搜索。设置 `full-world=true` 后会忽略搜索中心和半径，扫描 `-30000000` 到 `30000000` 的完整世界正方形。`cluster-radius-blocks` 是每个结果用于汇总密室和刷怪笼的范围。`area-shape=circle` 使用水平圆形距离；`area-shape=square` 表示中心向 X/Z 各扩展该数值，与方形方块查找命令口径一致。

`min-structures` 是最低密室数量阈值。每种实际密室数量最多保留 100 条，CSV 最终将所有结果按刷怪笼数量降序混合排列，并使用连续的全局排名；数量相同时优先排列密室更多的结果。CSV 使用中文表头和 UTF-8 BOM，可直接用 Excel 打开；TXT 使用固定列宽，仅用于阅读。

程序固定支持 Minecraft 1.21.1，使用无界面的官方服务端世界生成逻辑，不需要进入游戏或准备存档。首次构建需要联网。

精细搜索会先去重候选密室，再根据 JVM 可用逻辑处理器数量自动设置并发线程，并为系统保留 2 个逻辑处理器；无需手动配置线程数。线程实际运行在哪些 CPU 核心上由 Windows 调度。

精细统计会遍历所有能够包含目标密室起点的整数中心，并使用差分扫描精确选择实际试炼刷怪笼数量最多的中心；结果不依赖快速阶段的近似圆心。

快速搜索按分片流式处理，不会将整个搜索范围的候选一次性装入内存。`scan-threads` 控制并行快速扫描线程数；`scan-shard-size-blocks` 控制每个分片的边长，默认 `262144`。4 GB 内存建议使用默认分片大小和不超过 8 个线程。

开发构建需要 JDK 21。`setup.ps1` 会从 `JDK21_HOME`、`JAVA_HOME`、PATH 或 Minecraft Runtime 中选择可用的 JDK 并记录其路径，之后 `run.bat` 继续使用同一套 Java；开发环境不要求 GraalVM。启动脚本会自动写入临时服务端所需的 `eula=true`。
