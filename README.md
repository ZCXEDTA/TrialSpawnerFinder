# TrialSpawnerFinder

用于在 Minecraft 26.2 世界种子中查找试炼密室密集区域，并统计指定范围内实际生成的试炼刷怪笼。

## 使用方法

1. 编辑 `finder.properties`，填写世界种子、搜索范围和阈值。
2. 在项目目录打开 PowerShell，运行 `powershell -ExecutionPolicy Bypass -File ./setup.ps1` 完成首次构建。
3. 双击 `run.bat` 开始搜索。
4. 结果按刷怪笼数量降序保存为 `results-年月日-时分秒.csv`，不会覆盖以前的结果；同名 `.txt` 是适合记事本查看的对齐版本。

如果启动失败，窗口会保留错误信息，并将完整启动日志写入项目根目录的 `launcher.log`。

`search-radius-blocks` 是从搜索中心向外查找试炼密室的圆形范围。超出世界边界的部分会自动裁掉，其余方向仍正常搜索。设置 `full-world=true` 后会忽略搜索中心和半径，扫描 `-30000000` 到 `30000000` 的完整世界正方形。`cluster-radius-blocks` 是每个结果用于汇总密室和刷怪笼的范围。`area-shape=circle` 使用水平圆形距离；`area-shape=square` 表示中心向 X/Z 各扩展该数值，与方形方块查找命令口径一致。

`min-structures` 是最低密室数量阈值。每种实际密室数量最多保留 100 条，CSV 最终将所有结果按刷怪笼数量降序混合排列，并使用连续的全局排名；数量相同时优先排列密室更多的结果。CSV 使用中文表头和 UTF-8 BOM，可直接用 Excel 打开；TXT 使用固定列宽，仅用于阅读。

此实验分支固定使用 Minecraft 26.2 官方服务端生成逻辑，用于验证现代试炼密室布局的结果与性能，不需要进入游戏或准备存档。首次构建需要联网。

精细搜索会先去重候选密室，再根据 JVM 可用逻辑处理器数量自动设置并发线程，并为系统保留 2 个逻辑处理器；无需手动配置线程数。线程实际运行在哪些 CPU 核心上由 Windows 调度。

精细统计会遍历所有能够包含目标密室起点的整数中心，并使用差分扫描精确选择实际试炼刷怪笼数量最多的中心；结果不依赖快速阶段的近似圆心。

快速搜索按分片流式处理，不会将整个搜索范围的候选一次性装入内存。`scan-threads` 控制并行快速扫描线程数；`scan-shard-size-blocks` 控制每个分片的边长，默认 `262144`。4 GB 内存建议使用默认分片大小和不超过 8 个线程。

Minecraft 26.2 要求 JDK 25。启动脚本会检查项目旁的 `java` 目录、`JAVA_HOME` 和本机 GraalVM 25，并自动写入临时服务端所需的 `eula=true`；Gradle 缓存放在 `C:\GradleCache`。

开发者可阅读 [`docs/architecture.md`](docs/architecture.md)，了解公共搜索核心与各类查找器的模块边界。
试炼密室布局的版本边界和固定种子样本见 [`docs/version-compatibility.md`](docs/version-compatibility.md)。
