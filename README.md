# TrialSpawnerFinder

用于在 Minecraft 26.2 世界种子中查找试炼密室密集区域，并统计指定范围内实际生成的试炼刷怪笼。

当前版本：**v1.0.0**（对应 Minecraft 26.2）。可从 [Releases](https://github.com/ZCXEDTA/TrialSpawnerFinder/releases) 下载现成的 `dist.zip` 免安装包，或用 `make-dist.bat` 自行构建。

## 使用方法

直接双击 `run.bat` 即可开始搜索（无需先跑 `setup.ps1`）：

1. 编辑 `finder.properties`，填写世界种子、搜索范围和阈值（或用命令行参数覆盖，见下）。
2. 双击 `run.bat` 开始搜索。首次运行自动定位 JDK 25 并构建（jar 缺失时自动 `gradlew clean jar`）。
3. 结果按刷怪笼数量降序保存为 `results-trial-spawner-年月日-时分秒.csv`，不会覆盖以前的结果；同名 `.txt` 是适合记事本查看的对齐版本。

`setup.ps1` 仍保留，仅用于手动重建（`gradlew clean jar`）；日常使用不需要。

如果启动失败，窗口会保留错误信息，并将完整启动日志写入项目根目录的 `launcher.log`。

## 免安装 Java 的发布包（带精简 JRE）

`dist\` 目录或 `dist.zip` 是**自包含发布包**——内含用 `jlink` 精简的 JRE（约 30 MB，只需 `java.base` 模块），**目标机器不需要安装任何 Java**，解压即用：

```
dist\
├── trial-spawner-finder-1.0.0.jar   # 主程序（零第三方依赖）
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
java -jar minecraft-26.2-runtime\build\libs\trial-spawner-finder-1.0.0.jar --seed 123
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
- 产物是 `minecraft-26.2-runtime\build\libs\trial-spawner-finder-1.0.0.jar`，直接 `java -jar` 即可运行。

`setup.ps1` 执行 `gradlew clean jar`（只编译主代码并打 fat jar，不跑测试），首次构建只下载一次 Gradle 发行版。如需重新提取 26.2 数据（例如升级版本），运行 `gradlew :minecraft-26.2-runtime:extractTrialChambersData`（需联网拉取 Minecraft 26.2 jar，或用 `-PminecraftJar=<jar路径>` 指定 jar）。

精细搜索会先去重候选密室，再根据 JVM 可用逻辑处理器数量自动设置并发线程，并为系统保留 2 个逻辑处理器；无需手动配置线程数。线程实际运行在哪些 CPU 核心上由 Windows 调度。

精细统计会遍历所有能够包含目标密室起点的整数中心，并使用差分扫描精确选择实际试炼刷怪笼数量最多的中心；结果不依赖快速阶段的近似圆心。

快速搜索按分片流式处理，不会将整个搜索范围的候选一次性装入内存。`scan-threads` 控制并行快速扫描线程数；`scan-shard-size-blocks` 控制每个分片的边长，默认 `262144`。4 GB 内存建议使用默认分片大小和不超过 8 个线程。

Minecraft 26.2 开发构建需要 JDK 25。`setup.ps1` 会从项目内 `java`、`JDK25_HOME`、`JAVA_HOME` 或 PATH 中选择可用的 JDK 25 并记录其路径，之后 `run.bat` 继续使用同一套 Java；开发环境不要求 GraalVM。`run.bat` 直接以 `java -jar` 运行独立 fat jar，不再启动 Minecraft 服务端。

项目由 `finder-core`、`trial-spawner-finder` 和 `minecraft-26.2-runtime` 三层组成：

- `finder-core`（`cn.minecraftfinder.core`）：公共核心——配置、搜索区域、进度、输出、模型，零 Minecraft 依赖。
- `trial-spawner-finder`（`cn.trialfinder`）：搜索算法层——候选枚举、圆/方形聚类、精确中心优化、分片扫描、排名与输出，零 Minecraft 依赖。
- `minecraft-26.2-runtime`（`cn.trialfinder.sim.*`）：**纯 Java 生成模拟层**——随机源（LCG/Xoroshiro/WorldgenRandom）、NBT/JSON 解析、结构模板、模板池、Jigsaw 拼接、试炼密室布局计算器（`TrialChamberPredictor`），由官方 `HandTrialChamberPredictor` 剥离改造而来，与官方 26.2 生成逻辑逐位一致（已用同一 seed/半径对照官方结果验证）。运行时零 Minecraft 依赖，独立 fat jar 可直接 `java -jar` 运行。

## 版本记录

### v1.0.0（2026-08-20 · Minecraft 26.2）

首个正式版。

- **纯 Java 复刻 26.2 官方生成逻辑**：LCG/Xoroshiro 随机源、气候/生物群系筛选、结构 NBT/模板池数据、Jigsaw 拼接，与官方逐位一致。
- **免安装 Java 的发布包**：`dist.zip` 内含 jlink 精简 JRE（约 30 MB），解压即用。
- **全量搜索**：分片流式扫描、按 CPU 自动并发、圆/方形聚类、精确中心优化、检查点断点续扫。
- **定点查询**：单/批量坐标的刷怪笼明细与宝库列表（`query`）。
- **输出**：CSV（UTF-8 BOM，Excel 直接打开）+ 对齐 TXT；`--check-top` 统计快/慢刷怪笼与宝库数。
- 搜索仅保留单一精确预测模式（早期 AUTO/EXACT 双模式与搜索期自我比对复核已移除）。
