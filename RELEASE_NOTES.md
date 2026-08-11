# TrialSpawnerFinder 1.0.0 正式版

TrialSpawnerFinder 首个正式版。独立 CLI（CUDA 加速），在 Minecraft Java 版 1.21.11 世界种子中查找试炼密室密集区域。

## ✨ 功能特性

- **精确复刻 1.21.11 试炼密室生成**：A 流（34×34 网格定位）+ B 流（Jigsaw 拼接）+ C 流（怪物别名），三条随机流逐位一致。
- **CUDA 加速**：GPU 枚举 + 密度预筛直通，RTX 4060 实测 30 万格半径约 28 秒；GPU 不可用自动回退纯 CPU。
- **独立 CLI**：`shadowJar` 产出 fat JAR，无需 Minecraft 服务端，JDK 21 即可运行。
- **定点查询**（`query` 子命令）：列出指定坐标附近的密室与刷怪笼详细参数。

## 🎯 本版本（自 beta.2）改进

**召回率优化**
- 提高 top-K：auto-tune 公式 `max(20, min(200, r/1000))` → `max(50, min(5000, r/100))`，实测 100k 半径结果 **287→528（+84%）**。
- 无损密度预筛：去掉 2R 邻域 < `min-structures` 的候选，**数学保证无损**。
- 重叠 cell：原网格 + 半 cell 偏移网格各选一次 top-K 取并集，修复横跨 cell 边界的密集区漏召回。
- 自动分片 tile 之间重叠一半边长，修复跨 tile 密集区被漏。

**输出修正**
- `query` 输出聚焦刷怪笼位置与生成参数（怪物、实体、权重、间隔、生成数），移除战利品相关字段。

## ⚠️ 环境要求与已知问题

**环境**
- **JDK 21 必需**。GPU 加速需要 NVIDIA 驱动（cubin 已打包进 JAR，**无需 CUDA Toolkit**）。
- GPU 不可用/架构不匹配/驱动过旧 → 自动回退纯 CPU（大半径明显变慢）。
- 大半径内存占用高：1M+ 建议 `-Xmx4G`；10M+ 建议 `-Xmx8G`。
- GPU 原生库仅随附 Windows x86_64；Linux/macOS 请用 `--no-gpu`。

**参数建议**
- `--cluster-radius` 不要小于密室间距（~544 块），否则聚不出聚类、结果可能为空；一般 256 格配 `min-structures 3`、128 格配 `min-structures 2`。
- `--search-radius 30,000,000`（世界极限）候选约 95.5 亿，自动分片可跑完但需 10 分钟以上；建议缩小半径或用 `--full-world`。
- `--top-k` 默认 auto-tune（0 表示关闭全量 B 流，超大半径极慢；建议 1000+）。
- `--threads` 不要超过逻辑核心数。

**已知限制**
- `--biome-check` 实验性：NoiseRouter 未完全移植，启用后警告并跳过。
- `--prefilter-mode grid` 仍是近似模式（按网格总密度排序），但方法 2+3 显著降低了漏召回。

## 🚀 推荐指令

**快速验证（约 6 秒）**
```bat
run-cli.bat --seed 188188 --search-radius 1000000 --cluster-radius 256 --threads 14
```

**常规搜索（找密集区，约 15-20 秒）**
```bat
run-cli.bat --seed 188188 --search-radius 100000 --cluster-radius 1000 --min-structures 3 --min-spawners 20 --top-k 200 --threads 14
```

**全图流式扫描（推荐，Top-K 截断）**
```bat
run-cli.bat --seed 188188 --full-world --tile-size 100000 --tile-overlap 1000 --top-k 100000 --threads 14
```

**定点查询**
```bat
run-cli.bat query --seed 188188 --coords 544,166 1000,-2000 --radius 1000
```

> 不想手动调参？省略 `--cluster-radius/--grid-size/--top-k`，`--auto-tune`（默认开启）自动按半径计算；半径 >100,000 自动切 GPU 网格预筛。

## 📦 安装

1. 下载 `trialfinder-1.0.0.zip` 解压，或直接用 `trialfinder-1.0.0.jar`。
2. 需要 JDK 21。
3. Windows 双击 `run-cli.bat`；Linux/macOS `./run-cli.sh`。

校验和见 `SHA256SUMS.txt`。
