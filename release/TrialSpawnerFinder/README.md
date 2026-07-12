# TrialSpawnerFinder

用于在 Minecraft Java 版 1.21.1 世界种子中搜索试炼密室密集区域，并统计指定范围内实际生成的试炼刷怪笼。

## 首次安装

1. 保持发布包中的 5 个文件位于同一目录。
2. 双击 `setup.bat`。
3. 安装脚本会先检查 `JAVA_HOME` 和系统 `PATH`，找到 Java 21 时直接复用，不会下载 Java。未找到时才从清华 TUNA 镜像下载约 200 MB 的 Java 21，并在镜像失败时切换备用源。
4. Fabric API 优先使用 Modrinth CDN，失败时自动切换 Fabric 官方源。
5. 首次安装和首次运行需要联网，之后可以离线搜索。

## 开始搜索

1. 用记事本编辑 `finder.properties`。
2. 双击 `run.bat`。
3. 搜索完成后查看同目录的 `results.csv`。

程序会自动创建 `.runtime` 目录，用于存放 Java、Fabric、日志和临时世界。每次运行都会删除并重建临时世界，不会修改 Minecraft 正常存档。删除 `.runtime` 后重新运行 `setup.bat` 即可重装运行环境。

`full-world=true` 会忽略搜索中心与半径，扫描完整世界正方形。`area-shape` 只控制每个结果统计刷怪笼时使用圆形还是方形范围。

每种密室数量最多保留 100 条结果，最终按刷怪笼数量降序混合排列。精细阶段使用 Minecraft 官方结构生成逻辑，并在所有合法整数中心中精确选择刷怪笼最多的位置。
