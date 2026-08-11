# TrialSpawnerFinder 1.0.0-beta.1 预览版

独立 CLI 版本（CUDA 加速），在 Minecraft Java 版 1.21.11 世界种子中查找试炼密室密集区域。本预览版包含：单区域/全图搜索、定点查询 `query`、自动调参 `--auto-tune`、GPU 直通预筛、B 流缓存与分片进度条。

## ⚠️ 环境要求与已知问题

**环境**
- **JDK 21 必需**。GPU 加速需要 NVIDIA 驱动（内核已预编译为 cubin 打包进 JAR，**无需安装 CUDA Toolkit**）。
- GPU 不可用、架构不匹配或驱动过旧时自动回退纯 CPU —— 速度会显著下降（大半径尤为明显）。
- 大半径搜索内存占用高：`--search-radius 1,000,000+` 建议 `-Xmx4G`；`10,000,000+` 建议 `-Xmx8G`（可用 `set JAVA_TOOL_OPTIONS=-Xmx8G` 或修改 `run-cli.bat`）。
- GPU 原生库仅随附 Windows x86_64；Linux/macOS 请用 `--no-gpu`。

**参数负优化（重要）**
| 参数组合 | 后果 |
|---|---|
| `--cluster-radius` 过小（< 密室间距 ~544 块） | 无法把相邻密室聚进同一聚类 → **结果几乎必空**（如 `--cluster-radius 160`） |
| `--search-radius 30,000,000`（世界极限） | 候选约 **95.5 亿**，自动分片可跑完但需 **10 分钟以上**；建议缩小半径或用 `--full-world` |
| `--top-k 0`（默认） | 不做 Top-K 截断，B 流全量生成 → 大半径极慢；建议 `--top-k 100~200` |
| `--no-gpu` + 大半径 | 强制 CPU，A 流对象构造 + B 流串行 → 数倍于 GPU 的耗时 |
| `--min-spawners` 过高（如 60） | 过滤掉大量结果；合理值 20~40 |
| `--cache` | 默认**禁用**是有意的：低命中率时磁盘 I/O 反而拖慢；重复搜索同一种子才建议开启 |

**已知限制**
- `--biome-check` 为实验性：生物群系噪声路由器未完全移植，启用后打印警告并跳过。
- `--prefilter-mode grid` 是近似模式（按网格总密度而非真实刷怪笼排序），召回率约 60%（radius 3000 / grid 512 / topK 60）。
- B 流缓存的磁盘占用：每个密室一个 JSON，`--cache` 启用后注意磁盘空间。

**召回修复（已包含在本次构建）**
- 自动分片 tile 之间重叠一半边长：跨 tile 边界的密集区在每个分片都被完整枚举 + 筛选，合并后不再遗漏跨边界密室（此前相邻密室可能被 tile 边界切开而漏掉）。
- 合并后不再做二次 top-K 截断（此前会再次丢弃边界成员）。
- 若仍觉得相近密室被漏，通常是 `--cluster-radius` 小于密室间距（~544 块）或 `--top-k` 过小；建议 `--cluster-radius 1000`、`--top-k 200` 起步。

## 🚀 推荐指令

**快速验证（约 6 秒完成）**
```bat
run-cli.bat --seed 188188 --search-radius 1000000 --cluster-radius 256 --threads 14
```

**常规搜索（找密集区，约 15-20 秒）**
```bat
run-cli.bat --seed 188188 --search-radius 100000 --cluster-radius 1000 --min-structures 3 --min-spawners 20 --top-k 200 --threads 14
```

**你的示例指令的修正版**（原指令 `--search-radius 30000000 --cluster-radius 160` 能跑完，但 cluster-radius 160 < 密室间距 544，`min-structures 2` 很难满足，结果大概率为空；若要搜全图密集区）：
```bat
run-cli.bat --seed -6523988883445283364 --full-world --top-k 100000 --cluster-radius 1000 --min-structures 3 --min-spawners 20 --threads 14
```

**全图流式扫描（推荐，Top-K 截断）**
```bat
run-cli.bat --seed 188188 --full-world --tile-size 100000 --tile-overlap 1000 --top-k 100000 --threads 14
```

**定点查询**
```bat
run-cli.bat query --seed 188188 --coords 544,166 1000,-2000 --radius 1000
```

> 不想手动调参？直接省略 `--cluster-radius/--grid-size/--top-k`，`--auto-tune`（默认开启）会按 `--search-radius` 自动计算合理值；半径 >100,000 时自动切换到 GPU 网格预筛。

## 📦 安装

1. 下载 `trialfinder-beta.zip` 解压，或直接使用 `trialfinder-1.0.0-beta.1.jar`。
2. 需要 JDK 21。
3. Windows 双击 `run-cli.bat`；Linux/macOS `./run-cli.sh`。

校验和见 `SHA256SUMS.txt`（JAR SHA-256：见文件内）。
