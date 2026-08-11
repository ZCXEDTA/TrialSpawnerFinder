# TrialSpawnerFinder 1.0.0-beta.2 预览版

基于 beta.1 的**召回率优化**版本。独立 CLI（CUDA 加速），在 Minecraft Java 版 1.21.11 世界种子中查找试炼密室密集区域。

## 🎯 本版本改进（召回率）

针对"漏掉很多相近的密室"做了三项优化：

**方法 1 — 提高 top-K**
- auto-tune 公式 `max(20, min(200, r/1000))` → `max(50, min(5000, r/100))`
- 实测 100k 半径 topK 200→1000：结果 **287→528（+84%）**
- 想提高召回就加大 `--top-k`（上限 5000）；想更快就减小

**方法 2 — 无损密度预筛**（全图 grid 路径）
- cell 截断前先 `pruneByDensity` 去掉 2R 邻域 < `min-structures` 的候选
- **数学保证无损**：任何合格聚类成员必然 2R 内 ≥ min-structures，去掉它们不影响结果

**方法 3 — 重叠 cell**（全图 grid 路径）
- 原网格 + 半 cell 偏移网格各选一次 top-K，取并集
- 横跨 cell 边界的密集区在一个网格里被切、在另一个里完整，成员不丢失

**额外**：自动分片 tile 之间重叠一半边长，修复跨 tile 密集区被漏的问题。

## ⚠️ 环境要求与已知问题

**环境**
- **JDK 21 必需**。GPU 加速需要 NVIDIA 驱动（cubin 已打包进 JAR，**无需 CUDA Toolkit**）。
- GPU 不可用/架构不匹配/驱动过旧 → 自动回退纯 CPU（大半径明显变慢）。
- 大半径内存占用高：1M+ 建议 `-Xmx4G`；10M+ 建议 `-Xmx8G`。
- GPU 原生库仅随附 Windows x86_64；Linux/macOS 请用 `--no-gpu`。

**参数建议**
- `--cluster-radius` 不要小于密室间距（~544 块），否则聚不出聚类、结果可能为空；100w 格内 160 格半径的三联密室已很少，一般 256 格配 `min-structures 3`、128 格配 `min-structures 2`。
- `--search-radius 30,000,000`（世界极限）候选约 95.5 亿，自动分片可跑完但需 10 分钟以上；建议缩小半径或用 `--full-world`。
- `--top-k` 默认 auto-tune（0 表示关闭全量 B 流，超大半径极慢；建议 1000+）。
- `--threads` 不要超过逻辑核心数。
- `--cache` 默认禁用是有意的（低命中率时磁盘 I/O 拖慢）；重复搜索同一种子才建议开启。

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

1. 下载 `trialfinder-beta.2.zip` 解压，或直接用 `trialfinder-1.0.0-beta.2.jar`。
2. 需要 JDK 21。
3. Windows 双击 `run-cli.bat`；Linux/macOS `./run-cli.sh`。

校验和见 `SHA256SUMS.txt`。
