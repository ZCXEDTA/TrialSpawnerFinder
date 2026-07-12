# TrialSpawnerFinder

用于在 Minecraft Java 版 1.21.1 世界种子中搜索试炼密室密集区域，并统计指定范围内实际生成的试炼刷怪笼。

## 首次安装

1. 保持发布包中的 5 个文件位于同一目录。
2. 双击 `setup.bat`。
3. 安装脚本会先从 `JAVA_HOME`、系统 `PATH` 和本地运行时查找 Oracle GraalVM 25。未找到时下载约 345 MB 的 Oracle GraalVM 25，官方源失败时切换 GraalVM Community 备用源。只有两个下载源都失败时，才回退到机器已有的普通 Java 25 或 Java 21。
4. Fabric API 优先使用 Modrinth CDN，失败时自动切换 Fabric 官方源。
5. 首次安装和首次运行需要联网，之后可以离线搜索。

发布包固定使用 Minecraft 1.21.1 当前最新稳定 Fabric Loader `0.19.3`。旧运行环境再次执行 `setup.bat` 时会自动更新 Loader；同版本不会重复下载。

## 开始搜索

1. 用记事本编辑 `finder.properties`。
2. 双击 `run.bat`。
3. 搜索完成后查看同目录的 `results-年月日-时分秒.csv`；每次运行都会新建结果，不会覆盖旧文件。

CSV 使用中文表头，可直接用 Excel 打开。同名 TXT 内容一致并按列对齐，适合用记事本查看。

程序会自动创建 `.runtime` 目录，用于存放 Java、Fabric、日志和临时世界。每次运行都会删除并重建临时世界，不会修改 Minecraft 正常存档。删除 `.runtime` 后重新运行 `setup.bat` 即可重装运行环境。

`full-world=true` 会忽略搜索中心与半径，扫描完整世界正方形。`area-shape` 只控制每个结果统计刷怪笼时使用圆形还是方形范围。

每种密室数量最多保留 100 条结果，最终按刷怪笼数量降序混合排列。精细阶段使用 Minecraft 官方结构生成逻辑，并在所有合法整数中心中精确选择刷怪笼最多的位置。

精细生成线程数会根据 JVM 可用逻辑处理器数量自动调整，并为系统保留 2 个逻辑处理器；具体使用哪些 CPU 核心由 Windows 调度。

Oracle GraalVM 25 是推荐运行环境；普通 Java 25 和 Java 21 仅作下载失败时的兼容回退。在 16 逻辑处理器测试机上，GraalVM 25 配合 14 个精细线程处理 2860 座候选密室约需 55 秒。
