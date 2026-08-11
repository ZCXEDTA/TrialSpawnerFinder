# Trial Chambers 生成逻辑迁移报告（Minecraft 1.21.11）

迁移自 `F:\Downloads\test\.analysis_trialchambers`（官方 Mojang 映射反编译源码，Loom + Vineflower）
合并进 `TrialSpawnerFinder`，目标是让查找器**独立、完整地模拟 1.21.11 试炼密室生成**，不依赖外部服务端。

## 1. 迁移结果总览

| 类别 | 数量 |
|---|---|
| 新增 Java 类（`src/main/java/cn/trialfinder/sim/**`） | 70 个，约 4700 行 |
| 数据资源（`src/main/resources/data/**`） | 268 个（47 池 JSON + 191 模板 NBT + 2 结构 JSON + 28 trial_spawner 配置 JSON） |
| JUnit 测试（`src/test/java/cn/trialfinder/sim/**`） | 4 个类 / 12 个用例，全部通过 |
| 独立验证主类 | `cn.trialfinder.sim.SimVerifyMain`（无需 JUnit） |

三股随机流均以**逐位一致**方式复刻：

- **A 流（网格定位）**：`RandomSpreadStructurePlacement`（spacing=34, separation=12, salt=94251327, linear）
  → 已与 `java.util.Random` 独立交叉验证，并对 5 个种子 × 多组区域坐标逐位比对。
- **B 流（Jigsaw 拼接）**：`JigsawPlacement$Placer` 完整递归放置，RNG 消费顺序逐位一致
  → 56 个候选密室全部成功生成，每座 10~数百个 piece、0~数十个刷怪笼。
- **C 流（怪物类型别名）**：`PoolAliasLookup`（random_group + 2×random）
  → 与 `java.util.Random` 独立交叉验证。

## 2. 关键偏离与修正

### 2.1 包路径：`net.minecraft.*` → `cn.trialfinder.sim.*`（重要）

任务要求保留 `net.minecraft.*` 包路径，但**无法照做**：查找器是 **Fabric Loom 工程，使用 Yarn 映射**，
Yarn 与 Mojang 官方映射在大量类上**共用全限定名**（如 `net.minecraft.world.level.levelgen.WorldgenRandom`、
`net.minecraft.util.RandomSource`）。若在 `src/main/java` 下新增同名类，会与 Loom 提供的 Minecraft jar 发生
**类名冲突**（编译期重复类 / 运行时类加载遮蔽）。

因此迁移代码统一放在 **`cn.trialfinder.sim.*`** 包下，子包镜像 Mojang 结构便于对照：

```
cn.trialfinder.sim
├── random/     RandomSource, LegacyRandomSource, WorldgenRandom, Xoroshiro128PlusPlus, ...
├── math/       ChunkPos, BlockPos, Vec3i, BoundingBox, Mth
├── resources/  Identifier, ResourceKey, Holder
├── util/       WeightedList, Weighted, Pair, StringRepresentable, Util, SequencedPriorityIterator
├── nbt/        NbtTag, NbtIo（gzip NBT 读取子集）
├── structure/  Rotation, Direction, FrontAndTop, JigsawBlock, StructureTemplate, StructurePlaceSettings, ...
├── structure/placement/   RandomSpreadStructurePlacement, RandomSpreadType   ← A 流
├── structure/pools/       StructureTemplatePool, StructurePoolElement, JigsawPlacement, ...  ← B 流
├── structure/pools/alias/ PoolAliasLookup, DirectPoolAlias, RandomPoolAlias, RandomGroupPoolAlias  ← C 流
└── data/       TrialChambersData（放置/结构/别名常量）
```

### 2.2 对分析报告的三处纠错

1. **C 流不是 Xoroshiro**。报告 §2.3/§5.3 称“Xoroshiro 位置随机”。实测反编译：`RandomSource.create(seed)`
   返回 `new LegacyRandomSource(seed)`，`forkPositional().at(pos)` 走 **LegacyPositionalRandomFactory**，
   `at(x,y,z) = new LegacyRandomSource(Mth.getSeed(x,y,z) ^ seed)`。`Mth.getSeed` 常量已从字节码逐条提取：
   `(long)(x*3129871) ^ (long)z*116129781L ^ y`，再 `l*l*42317861L + l*11L`，`>>16`。
2. **`Rotation` 的状态旋转是 OctahedralGroup，不是纯 Y 轴旋转**。`CLOCKWISE_180.rotation() =
   ROT_180_FACE_XZ = diag(-1,1,-1)`（水平方向取反、Y 不变）。位置变换 `transform` 与状态旋转必须同源，
   否则连接朝向错乱。已从 `OctahedralGroup` 字节码提取矩阵：`scale(flip) * permutation`。
3. **Vineflower 反编译的 switch-case 标签有误导**。`StructureTemplate.transform` / `getZeroPositionWithTransform`
   的 `$SwitchMap` 实际顺序是 `CCW90→1, CW90→2, CW180→3`，按 ordinate 推断会被置换。已对照字节码重新校正。

### 2.3 重叠检测的解析化简化（数学上无损）

Vanilla 用 `Shapes.joinIsNotEmpty(free, create(AABB.of(newBox).deflate(0.25)), ONLY_SECOND)`。
由于所有碰撞盒都是整数块 AABB，0.5-voxel 离散化 + 0.25 deflate 可精确归约为**标准整数 AABB 相交**：

> 候选被拒绝 ⟺ 候选块范围超出区域块范围 **或** 与任一已放置块相交。

`VoxelShape`（`sim/structure/VoxelShape.java`）用 `region + subtracted boxes` 解析表示，等价于
`Shapes.create` / `joinIsNotEmpty` / `joinUnoptimized(ONLY_FIRST)`，无 `Shapes` 依赖。

### 2.4 空池 fallback 处理

`minecraft:empty` 是绝大多数池的 fallback。Vanilla 仅当“池为空 **且** 不是 empty 哨兵”才跳过。
早期简化成“fallback 为空就跳过”导致主池（如 corridor、chamber/addon）全部失效，已修正。

## 3. 依赖处理

| 依赖 | 处理 |
|---|---|
| `com.google.gson` | 唯一第三方依赖，用于读取池 JSON（Loom 工程自带） |
| `com.mojang.serialization.*`（DFU） | **未迁移**，全部用普通构造器/字段替代 |
| NBT 解析 | 自写 `sim/nbt/NbtIo`（gzip + 各标签类型），约 200 行 |
| `StructureProcessorList` / `StructureProcessor` | 占位空实现。试炼密室处理器只改铜块状态，**不影响 jigsaw/刷怪笼**，可安全忽略 |
| 地形高度 `getFirstFreeHeight` | **从不调用**。试炼密室全部 RIGID 投影 + `use_expansion_hack=false`；代码保留防御性 `IllegalStateException` |
| `DataFixer` / 注册表 / `RegistryAccess` | 未迁移，用 `PoolRegistry`（读 JSON）与简单 `Holder` 替代 |
| `SequencedPriorityIterator` | 自写，降序优先 + 同级 FIFO（与字节码一致） |

## 4. 已验证内容

- **RNG**：`LegacyRandomSource` 与 `java.util.Random` 逐位一致（nextInt/nextLong，多种子多边界）。
- **A 流**：34×34 网格候选坐标与独立参考（报告 §5.1 算法 + `java.util.Random`）一致；`SimVerifyMain` 与 JUnit `AFlowTest` 均通过。
- **B 流**：seed 12345 区域 56 个候选全部成功装配；平均每座 ~16 刷怪笼；生成确定性；刷怪笼位置唯一。
- **C 流**：别名解析（ranged/slow_ranged/melee/small_melee）与 `java.util.Random` 独立参考一致。

## 5. 手动调整与后续事项

1. **数据资源位于 `data/minecraft/**`**。按任务要求放在该处；若该 Mod 在真实服务器加载，这些文件会以
   “同名覆盖”方式被 datapack 加载（内容与 vanilla 完全相同，无实际影响）。若要彻底隔离，可把目录改到
   `simdata/minecraft/**` 并调整 `StructureTemplateManager`/`PoolRegistry` 的查找路径。
2. **`StructureTemplate` 只读单 palette**。模板均单 palette；若遇 `palettes` 多 palette 需扩展
   `getRandomPalette`。
3. **B 流未实现 `use_expansion_hack=true` 与 `project_start_to_heightmap`**（试炼密室均 false/empty）。
   通用化时需接入地形高度源。
4. **实体（Entity）未解析**。刷怪笼是方块，与实体无关；模板内 `entities` 标签被忽略。
5. **多线程**：`SimChamberGenerator.generate` 每 chamber 新建 RNG/piece 集合，无共享可变状态，可直接并行；
   `enumeratePotentialChunks` 每区域纯函数 —— 是后续 CUDA 加速的天然 kernel 边界。

## 5.5 用 1.21.11 服务端验证（重要修正）

项目已从 1.21.1（Yarn）升级到 **1.21.11（Mojang 官方映射）**：
- `TrialChamberGenerator` 改用 1.21.11 的 `Structure.generate(...)`（原 `createStructureStart` 已改名），
  `world.getStructureManager()` 返回 `StructureTemplateManager`，`Identifier.fromNamespaceAndPath` 等。
- `FinderSearch` 用 `ServerLevel`；`TrialSpawnerFinderMod` 用 `overworld()`/`halt()`。
- Mixin 精简为 `PaletteCacheMixin`（并发安全的 `Palette.cache`）。

**发现并修复一个真实的 B 流 bug（本次迁移的核心修正）：**

Vanilla 的 `BoundingBox.move(...)` 是**原地修改**（mutable）。`JigsawPlacement.addPieces` 中先捕获
`boundingBox`（起点 piece 的 pre-move bbox），随后 `startPiece.move(0, l-m, 0)` 会把**同一个对象**改写成
post-move bbox。因此重叠检测的 free shape 减掉的是 **post-move 起点盒**。

sim 的 `BoundingBox` 是不可变的，`move()` 返回新对象，导致 free shape 减掉了 **pre-move 起点盒**，
Y 坐标差 1，使本应放下的上层密室（如 `chamber_8`）被误判重叠而拒绝。修复：free shape 用
`startPiece.getBoundingBox()`（post-move），与 vanilla 一致。

**修复后与 1.21.11 服务端逐项一致：**
- A 流：1062 候选 / 1072 聚类 —— 精确一致。
- B 流：**全部 1062 座密室的刷怪笼坐标逐一一致**（修复前仅 ~70%）。
- 密度：107 个达标结果与服务器 CSV **完全一致**（含前 5 高密度区域）。

## 6. 第二阶段：CUDA 加速 CLI（已实现）

新增纯命令行工具 `cn.trialfinder.cli.TrialFinderCLI`（picocli），不依赖 Minecraft 服务端：

- **A 流（GPU）**：`generateChunksKernel` — 每线程一个 (regionX, regionZ)，逐位复刻
  `setLargeFeatureWithSalt` + 2×`nextInt(22)`（含拒绝采样），`atomicAdd` 写入，分片 ≤ 100 万。
- **密度预筛（GPU）**：`densityCellCount`/`densityScatter`/`densityCount` — 空间网格
  （cell=2×clusterRadius）统计每个候选的 2R 邻居数，数学上无损地剪掉不可能属于任何达标聚类的候选。
- **B 流（CPU 线程池）**：`SimChamberGenerator.generateChamber` 并行。
- **密度精算（CPU）**：`ExactCenterOptimizer`（与 1.21.11 服务端逐位一致）。

关键环境适配（本机实测）：
- **JCuda 用 12.6.0**（非任务写的 0.9.2）：0.9.2 的 natives 是空壳，无法加载。
- **NVRTC 通过 JNA 直接调用 nvrtc64_*_0.dll**：JCuda 自带 JNvrtc JNI 按 CUDA 小版本链接，加载不了 CUDA 13.3。
- **NVRTC 13.3 输出 PTX 9.3，而驱动是 CUDA 13.0（最大 PTX 9.0）→ 用 ptxas 把 PTX 组装成 sm_<cc> cubin 再加载**（ptxas 是独立二进制，不需要 MSVC cl.exe）。
- 全流程自动回退：GPU 任何一步失败 → 纯 CPU。

验证：
- `GpuAcceleratorTest`（3 个 JUnit）：findChunks / pruneByDensity / 全流水线与 CPU 结果**逐位一致**。
- CLI `run-cuda.bat/.sh` 实测：GPU 识别 RTX 4060 (sm_89)，A 流/密度走 GPU，结果与 `--no-gpu` 完全一致。
- 完整测试套件 44 个用例全部通过。

## 7. 近似 top-K 流水线（`--top-k`，聚类级截断）

为让 `--full-world` 可行，新增 GPU 粗筛 top-K 模式。**截断单位是粗聚类而非单个密室**，
避免聚类被打散：

1. GPU 为每个候选计算 2R 邻居数（`densityCount` 核，含自身）；
2. 并查集粗聚类：距离 ≤ 2R 的候选连接成一个粗聚类（`coarseClusterAll`，网格 + union-find，
   保证最终精确聚类一定落在单个粗聚类内）；
3. 按粗聚类密室数降序，保留前 K 个粗聚类（跨分片用有界最小堆，`COARSE_WORST_FIRST` 保证确定性）；
4. CPU 对保留粗聚类中所有密室执行 B 流 Jigsaw，取真实刷怪笼数；
5. 对保留密室做精确聚类（`CircleClusters`）+ `ExactCenterOptimizer` 精确中心，输出完整聚类
   （每个结果含其全部成员密室）。

`densityScores` 的 CPU 与 GPU 实现逐位一致（`GpuAcceleratorTest.densityScoresMatchCpu`）；
分片累计的全局 top-K 聚类与单区域 top-K 聚类等价（`TopKStreamingTest`）。该模式是**近似**的：
被截断的粗聚类不会输出，但保留的聚类是完整的。

## 8. 运行方式

```bash
# 独立验证（无需 JUnit）
java -cp <classpath> cn.trialfinder.sim.SimVerifyMain 12345

# 手动查看某种子候选与第一座密室
java -cp <classpath> cn.trialfinder.sim.SimChamberMain 12345 -2000 2000 -2000 2000

# 鲁棒性扫描
java -cp <classpath> cn.trialfinder.sim.SimSweepMain 12345

# JUnit（工程内）
./gradlew test --tests "cn.trialfinder.sim.*"
```
