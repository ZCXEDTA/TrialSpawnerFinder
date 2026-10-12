# TrialSpawnerFinder

用于在 Minecraft 26.2 世界种子中查找试炼密室密集区域，并统计指定范围内实际生成的试炼刷怪笼。

当前版本：**v2.0.0**（对应 Minecraft 26.2）。可从 [Releases](https://github.com/ZCXEDTA/TrialSpawnerFinder/releases) 下载现成的 `dist.zip` 免安装包，或用 `make-dist.bat` 自行构建。

## 使用方法

提供两种用法：**图形界面**（推荐交互式调参与批量种子）与 **命令行**（`run.bat` / `trial.bat`）。

### 命令行（`run.bat`）

直接双击 `run.bat` 即可开始搜索（无需先跑 `setup.ps1`）：

1. 编辑 `finder.properties`，填写世界种子、搜索范围和阈值（或用命令行参数覆盖，见下）。
2. 双击 `run.bat` 开始搜索。首次运行自动定位 JDK 25 并构建（jar 缺失时自动 `gradlew clean jar`）。
3. 结果按刷怪笼数量降序保存为 `results-trial-spawner-年月日-时分秒.csv`，不会覆盖以前的结果；同名 `.txt` 是适合记事本查看的对齐版本。

`setup.ps1` 仍保留，仅用于手动重建（`gradlew clean jar`）；日常使用不需要。

如果启动失败，窗口会保留错误信息，并将完整启动日志写入项目根目录的 `launcher.log`。

## 图形界面

模块 `trial-spawner-finder-gui` 提供 Swing 图形界面，底层搜索逻辑与命令行一致（含检查点断点续扫、check-top 统计）。界面分两个页签：**单种子搜索**、**从种子列表搜索**；左侧填参数，右侧显示结果。

### 启动

需要 JDK 25。在项目根目录执行：

```
gradlew :trial-spawner-finder-gui:run
```

或先构建再运行 fat jar：

```
gradlew :trial-spawner-finder-gui:jar
java -jar trial-spawner-finder-gui\build\libs\trial-spawner-finder-gui-2.0.0.jar
```

工作目录优先使用当前可写目录（结果文件、`trial-spawner-checkpoints\` 检查点写在这里）；若当前目录不可写（例如从 System32 启动），则回退到 jar 所在目录或用户主目录。

### 界面布局（左侧参数，自上而下）

| 区域 | 说明 |
|------|------|
| 种子 / 种子文件 | 单种子页填写世界种子；列表页通过「导入种子文件…」选择 `.txt` |
| 线程数 | 快速扫描并行线程数，默认等于本机逻辑处理器数（内部限制在 1～64） |
| 试炼刷怪笼专用 | 统计半径、模拟距离、是否圆形、最少密室/刷怪笼、慢速笼折算比例 |
| 公共搜索范围 | MinX/MaxX/MinZ/MaxZ，或正方形边长 / 圆形半径 |
| Minecraft 版本 | 当前仅 `1.21.2~26.3` |
| 底部按钮 | 重置搜索范围、开始 / 暂停 / 停止；其下为进度条与状态 |

右侧为「搜索结果」文本区与「导出结果…」。

### 参数说明

**试炼刷怪笼专用**

- **统计半径(方块)**：每个候选中心用于汇总密室与刷怪笼的范围（对应配置 `trial-cluster-radius-blocks`），默认 `160`。
- **模拟距离区块数**：填写整数后，会自动把统计半径设为「区块数 × 16」，并取消「是否圆形区域」（便于按模拟距离换算）。
- **是否圆形区域**：勾选后按圆形距离统计；未勾选为方形（对应 `trial-area-shape`）。
- **最少密室数 / 最少刷怪笼数**：过滤阈值，默认分别为 `1` / `20`。
- **慢速笼折算比例**：用于结果排序的折算值 `快速笼 + 慢速笼 × 比例`，支持小数或分数（如 `1/6`），须在 `0～1`。界面显示前 100 条按该折算值降序排列。

**公共搜索范围**（三种互斥模式）

1. **手动矩形**：取消「正方形区域」与「半径」后，直接编辑 MinX/MaxX/MinZ/MaxZ。
2. **正方形区域**（默认开启）：以原点为中心，按边长自动填入对称边界。单种子默认边长 `131072`（约 ±65536）；列表页默认边长 `8192`（约 ±4096）。
3. **半径**：以原点为中心的圆形搜索范围。

快捷按钮：

- **重置搜索范围为默认**：恢复上述默认正方形范围。
- **重置搜索范围为世界边界**（仅单种子页）：设为 `-30000000`～`30000000`。

### 单种子搜索

1. 填写世界种子与所需参数。
2. 点 **开始**。搜索中可 **暂停**（进度写入检查点）或 **停止**（清除检查点）。
3. **暂停**后按钮变为「继续」：此时仅可改 **线程数**、**慢速笼折算比例**；其余搜索参数锁定。点「继续」从检查点续扫。
4. 完成后右侧显示结果行，格式类似：

   ```
   /tp X 0 Z 快速+慢速=合计 discount=折算值 structure-count=密室数 vault=宝库数
   ```

5. 点 **导出结果…** 另存为 CSV（并生成对齐 TXT），格式与命令行输出一致。

### 从种子列表搜索

1. 准备 UTF-8 文本文件：每行一个种子（整数）；空行与 `#` 开头的注释行会被忽略。
2. 用「导入种子文件…」选中该文件，设置范围与阈值后点 **开始**。
3. 按文件顺序逐个种子搜索；状态栏显示当前种子序号。某一种子完成后结果追加到右侧，并可随时 **导出结果…**（选择目录，每个种子一个 CSV）。
4. **暂停 / 停止** 行为与单种子页相同（暂停可续扫当前种子；停止会清除检查点并中止列表）。

注意：同一时刻只能跑一种搜索（单种子与列表互斥）；已有搜索在运行时再次开始会提示。

### 与命令行的差异

- GUI 不读取 `finder.properties`，参数全部来自界面。
- GUI 不会在搜索结束时自动写结果文件，需手动「导出结果…」。
- GUI 暂不提供命令行里的 `query` 定点查询入口。

## 免安装 Java 的发布包（带精简 JRE）

`dist\` 目录或 `dist.zip` 是**自包含发布包**——内含用 `jlink` 精简的 JRE（约 30 MB，只需 `java.base` 模块），**目标机器不需要安装任何 Java**，解压即用：

```
dist\
├── trial-spawner-finder-2.0.0.jar   # 主程序（零第三方依赖）
├── trial.bat                        # 启动器（优先用捆绑 runtime）
├── finder.properties                # 配置
├── runtime\                         # jlink 精简 JRE（约 30 MB）
└── README.md
```

解压后在 `dist` 目录运行：

```
trial.bat --seed 123 --search-radius-blocks 50000
trial.bat query --seed 0 --coords 0,0 --radius 1028
```

`trial.bat` 的 Java 查找顺序：**捆绑的 `runtime\`** → `JDK25_HOME`/`JAVA_HOME` → 系统 JDK 25 → PATH。装了 JDK 会优先用捆绑的（保证版本一致），没有就用捆绑的。

### 打包发布包

需要 JDK 25（`javac` + `jlink`），在 `JAVA_HOME` 或 `JDK25_HOME` 指向 JDK 25：

```
make-dist.bat
```

产出 `dist\` 目录 + `dist.zip`。打包脚本会：构建 fat jar → 复制 jar/配置/启动器/README → `jlink --add-modules java.base` 生成精简 JRE → 冒烟测试 → 打 zip。

## 从命令行运行

唯一的启动器是 `trial.bat`。自动定位 runtime / JDK 25、jar 缺失时自动构建，并用**纯 Java 的 `\r` 进度条**渲染进度：

```
trial.bat --seed 123 --search-radius-blocks 5000 --scan-threads 8
trial.bat query --coords 3145,7232 --radius 2000
```

也可以绕过脚本直接用 `java -jar`（需已构建，JDK 25）：

```
java -jar minecraft-26.2-runtime\build\libs\trial-spawner-finder-2.0.0.jar --seed 123
```

## 命令行参数

启动脚本会把参数透传给程序。全量搜索支持用 `--key value` 覆盖 `finder.properties` 的配置（键名与配置文件一致）：

```
trial.bat --seed 123 --search-radius-blocks 5000 --scan-threads 8 --trial-min-spawners 20
```

支持的键：`--seed`、`--search-center-x`、`--search-center-z`、`--search-radius-blocks`、`--full-world`(true/false)、`--search-area-shape`(circle/square)、`--trial-cluster-radius-blocks`、`--trial-area-shape`(circle/square)、`--trial-min-structures`、`--trial-min-spawners`、`--scan-threads`、`--scan-shard-size-blocks`。

另有三个独立开关：
- `--check-top N`：统计前 N 个结果的快/慢刷怪笼与宝库数，追加到 CSV/TXT 末尾三列（快速刷怪笼、慢速刷怪笼、宝库数量）。
- `--no-progress`：完全关闭进度条。
- `--help` / `-h` / `help`：显示完整参数说明。

不带参数时仍从 `finder.properties` 读取全部配置。用 `trial.bat --help`（或 `-h`、`help`）显示完整参数说明。

## 定点查询

不做全量搜索，直接查询某个坐标点附近的试炼密室详情：

```
trial.bat query --seed 0 --coords 3145,7232 --radius 2000
```

- `--coords x,z x,z ...`：一个或多个查询点（逗号分隔），可重复。
- `--file path`：查询点文件——每行 `x z`（空格或逗号分隔，`#` 注释），或结果 CSV（自动读 `中心X`/`中心Z` 列）。
- `--radius N`：查询半径（方块），默认 1000。
- `--output table|csv`：`table` 输出到控制台（默认）；`csv` 写 `query-年月日-时分秒.csv`（含对齐 TXT）。

定点查询对每个查询点枚举半径内的密室候选，用模拟器生成密室详情并输出，秒级返回。**table 格式**包含：每查询点汇总、每个密室的**刷怪笼种类统计**、每个刷怪笼的参数明细表（怪物/实体/权重/间隔tick/同时数/总数）、以及**宝库列表**（普通/不祥）。**csv 格式**每个刷怪笼一行，含全部参数和宝库列。

`search-radius-blocks` 是从搜索中心向外查找试炼密室的圆形范围。超出世界边界的部分会自动裁掉，其余方向仍正常搜索。设置 `full-world=true` 后会忽略搜索中心和半径，扫描 `-30000000` 到 `30000000` 的完整世界正方形。`search-area-shape` 决定搜索范围的形状（circle/square）。`trial-cluster-radius-blocks` 是每个结果用于汇总密室和刷怪笼的范围。`trial-area-shape=circle` 使用水平圆形距离；`trial-area-shape=square` 表示中心向 X/Z 各扩展该数值，与方形方块查找命令口径一致。

`trial-min-structures` 是最低密室数量阈值，`trial-min-spawners` 是最低刷怪笼数量阈值。每种实际密室数量最多保留 100 条，CSV 最终将所有结果按刷怪笼数量降序混合排列，并使用连续的全局排名；数量相同时优先排列密室更多的结果。CSV 使用中文表头和 UTF-8 BOM，可直接用 Excel 打开；TXT 使用固定列宽，仅用于阅读。

此版本在 26.2 官方生成逻辑的基础上剥离为**纯 Java 模拟**，**零第三方依赖、开箱即用**：

- 运行时不需要 Minecraft 服务端或存档，直接复刻官方 Jigsaw 拼接生成试炼刷怪笼坐标。
- 试炼密室 datapack 数据（结构 NBT、模板池 JSON、结构配置）已提取进 `minecraft-26.2-runtime\src\main\resources\data\minecraft` 并随 jar 打包。
- 运行时零 `net/minecraft` 类、零第三方库，构建只需 JDK 25 + Gradle，无需联网拉取插件/依赖（JSON 解析用自研 `cn.trialfinder.sim.json.Json`）。
- 产物是 `minecraft-26.2-runtime\build\libs\trial-spawner-finder-2.0.0.jar`，直接 `java -jar` 即可运行。

`setup.ps1` 执行 `gradlew clean jar`（只编译主代码并打 fat jar，不跑测试），首次构建只下载一次 Gradle 发行版。如需重新提取 26.2 数据（例如升级版本），运行 `gradlew :minecraft-26.2-runtime:extractTrialChambersData`（需联网拉取 Minecraft 26.2 jar，或用 `-PminecraftJar=<jar路径>` 指定 jar）。

精细搜索会先去重候选密室，再根据 JVM 可用逻辑处理器数量自动设置并发线程，并为系统保留 2 个逻辑处理器；无需手动配置线程数。线程实际运行在哪些 CPU 核心上由 Windows 调度。

精细统计会遍历所有能够包含目标密室起点的整数中心，并使用差分扫描精确选择实际试炼刷怪笼数量最多的中心；结果不依赖快速阶段的近似圆心。

快速搜索按分片流式处理，不会将整个搜索范围的候选一次性装入内存。`scan-threads` 控制并行快速扫描线程数；`scan-shard-size-blocks` 控制每个分片的边长，默认 `262144`。4 GB 内存建议使用默认分片大小和不超过 8 个线程。

Minecraft 26.2 开发构建需要 JDK 25。`setup.ps1` 会从项目内 `java`、`JDK25_HOME`、`JAVA_HOME` 或 PATH 中选择可用的 JDK 25 并记录其路径，之后 `run.bat` 继续使用同一套 Java；开发环境不要求 GraalVM。`run.bat` 直接以 `java -jar` 运行独立 fat jar，不再启动 Minecraft 服务端。

项目由 `finder-core`、`trial-spawner-finder`、`minecraft-26.2-runtime` 与 `trial-spawner-finder-gui` 组成：

- `finder-core`（`cn.minecraftfinder.core`）：公共核心——配置、搜索区域、进度、输出、模型，零 Minecraft 依赖。
- `trial-spawner-finder`（`cn.trialfinder`）：搜索算法层——候选枚举、圆/方形聚类、精确中心优化、分片扫描、排名与输出，零 Minecraft 依赖。
- `minecraft-26.2-runtime`（`cn.trialfinder.sim.*`）：**纯 Java 生成模拟层**——随机源（LCG/Xoroshiro/WorldgenRandom）、NBT/JSON 解析、结构模板、模板池、Jigsaw 拼接、试炼密室布局计算器（`TrialChamberPredictor`），由官方 `HandTrialChamberPredictor` 剥离改造而来，与官方 26.2 生成逻辑逐位一致（已用同一 seed/半径对照官方结果验证）。运行时零 Minecraft 依赖，独立 fat jar 可直接 `java -jar` 运行。
- `trial-spawner-finder-gui`（`cn.trialfinder.gui`）：Swing 图形界面——单种子 / 种子列表搜索、参数表单、进度与导出；依赖 runtime 的 fat jar，主类 `cn.trialfinder.gui.TrialSpawnerFinderFrame`。

## 版本记录

### v2.0.0（2026-08-29 · Minecraft 26.2）

B 流核心 C++ 移植（JNI 混合架构）与输出自文档化。

- **C++ 逻辑核心**：Jigsaw 拼接 / 试炼密室预测器整体移植到 `src/main/cpp`（`JigsawPlacement`、`HandTrialChamberPredictor`、`StructureTemplateManager`、NBT 解析、WorldgenRandom），与 Java oracle 逐位一致（37/37 GoogleTest；16 组合 count/exists、4 组完整刷怪笼列表对拍）。性能基准与一致性说明见 `src/main/cpp/reports/`。
- **JNI 桥接**：`BFlowNative` ↔ `jni_bridge.cpp`，Java 保留资源加载与并发调度，C++ 执行 B 流拼接；运行时分发仍为纯 Java（JNI 接口尚未接入运行时调度）。
- **生物群系筛选**：新增气候/噪声模拟（`OverworldSampler`），公共构造器默认启用，过滤唯一排除 `deep_dark`。
- **输出自文档化**：结果 CSV/TXT 头部写入本次运行的全部参数（`#` 注释），`query --file` 可直接回读；启动时控制台回显生效参数。
- **搜索模式合并**：AUTO/EXACT 合并为单一精确模式，移除搜索期冗余复核。

### v1.5.0（2026-08-20 · Minecraft 26.2）

26.2 纯 Java 重构线首发（旧 Fabric/CUDA 线归档于 v1.4.0）。

- **纯 Java 复刻 26.2 官方生成逻辑**：LCG/Xoroshiro 随机源、气候/生物群系筛选、结构 NBT/模板池数据、Jigsaw 拼接，与官方逐位一致。
- **免安装 Java 的发布包**：`dist.zip` 内含 jlink 精简 JRE（约 30 MB），解压即用。
- **全量搜索**：分片流式扫描、按 CPU 自动并发、圆/方形聚类、精确中心优化、检查点断点续扫。
- **定点查询**：单/批量坐标的刷怪笼明细与宝库列表（`query`）。
- **输出**：CSV（UTF-8 BOM，Excel 直接打开）+ 对齐 TXT；`--check-top` 统计快/慢刷怪笼与宝库数。
- 搜索仅保留单一精确预测模式（早期 AUTO/EXACT 双模式与搜索期自我比对复核已移除）。
