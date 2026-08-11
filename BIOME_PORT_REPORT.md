# 生物群系判定算法移植报告（1.21.11）

> 结论先行：**噪声基础已移植并通过测试，但"气候采样器"（NoiseRouter/DensityFunctions/NoiseChunk）未移植——这是让 `--biome-check` 真正生效的唯一主要缺口。** 详见第 6 节。

## 1. 交付内容

新增 `cn.trialfinder.sim.biome` 包（无任何 `net.minecraft.*` 依赖）：

| 文件 | 内容 | 状态 |
|---|---|---|
| `noise/ImprovedNoise.java` | Ken Perlin 3D 噪声，逐位复刻（排列表播种、xo/yo/zo、8 角梯度插值） | ✅ 已移植 |
| `noise/SimplexNoise.java` | 2D/3D 单纯形噪声，16 项梯度表、`getValue(x,z)`/`getValue(x,y,z)` | ✅ 已移植 |
| `noise/Mth.java` | `smoothstep`/`lerp`/`lerp2`/`lerp3` | ✅ 已移植 |
| `Climate.java` | 参数点（7 维）与最近点匹配（线性扫描，语义等价 RTree） | ✅ 已移植 |
| `MultiNoiseBiomeSource.java` | `getNoiseBiome` 匹配逻辑（参数表 → 最近点 → 生物群系） | ✅ 已移植 |
| `BiomeType.java` | 生物群系枚举（ID 常量） | ✅ 已移植 |
| `TrialChambersBiomes.java` | `#has_structure/trial_chambers` 允许列表 | ⚠️ 数据清单需与官方 tag 核对 |
| `ClimateSampler.java` | 6 维气候噪声采样接口 | ✅ 接口 |
| `BiomeChecker.java` | `isTrialChambersValid(seed, chunkX, chunkZ)` | ✅ 管线齐备 |
| `BiomeCheckerFactory.java` | 构建 checker | ✅ |

`--biome-check` 已接入 `TrialFinderCLI` + `SearchEngine.run`（过滤钩子在 A 流之后、B 流之前）。

## 2. 试炼密室的生物群系要求

- `structure/trial_chambers.json` 的 `biomes` 字段是 **`#minecraft:has_structure/trial_chambers`** 标签；
- 该标签是**数据驱动**的（`data/minecraft/tags/worldgen/biome/has_structure/trial_chambers.json`），
  反编译源码集中**不含此数据文件**；
- `TrialChambersBiomes` 提供了一份常用清单（含 `deep_dark`、`dripstone_caves`、`lush_caves` 及
  大部分地表生物群系），**需与官方 tag 逐项核对后再作为权威**。

## 3. 服务器判定路径（必须复刻）

```
Structure.checkStructureBiome
  → validBiome.test( chunkGenerator.getBiomeSource().getNoiseBiome(quartX, quartY, quartZ, sampler) )
    → MultiNoiseBiomeSource.getNoiseBiome(…, Climate.Sampler)
      → 由 sampler 算出 6 维气候噪声（温度/湿度/大陆性/侵蚀度/深度/怪异度）
      → 在参数表（overworld preset）中找最近 ParameterPoint → 生物群系
      → 检查生物群系 ∈ #has_structure/trial_chambers
```

`quart` 坐标 = 方块坐标 >> 2。

## 4. 已移植的核心算法

### 4.1 ImprovedNoise（逐位复刻）
- 构造：`xo/yo/zo = rng.nextDouble()*256`；`p[0..255]=i`；Fisher–Yates 用 `rng.nextInt(256-i)`（与
  游戏一致的 LCG 播种，复用 `sim.random.LegacyRandomSource`）；
- `noise(x,y,z,yScale,yMax)`：加偏移 → floor → 分数坐标 → 计算 yLerp → `sampleAndLerp(...)`；
- `sampleAndLerp`：8 个角 `gradDot(p(...)&15)` + `smoothstep` 三线性插值 `Mth.lerp3`。

### 4.2 SimplexNoise（逐位复刻）
- 16 项梯度表（含改进噪声用 `& 15` 的扩展 4 项）；
- `p(i) = p[i & 255]`（等价于游戏的 512 重复表，因索引被掩码）；
- `getValue(x,z)`（2D，`*70`）与 `getValue(x,y,z)`（3D，`*32`）。

### 4.3 Climate 匹配
- `ParameterPoint`（7 维参数区间）、`TargetPoint`（6 维采样 + offset）；
- `findNearest`：按游戏距离度量找最近参数点（线性扫描；RTree 只是优化，语义等价）。

## 5. `--biome-check` 集成

- CLI 新增 `--biome-check`；
- `SearchEngine.run(..., BiomeChecker)`：当 checker `isAvailable()` 时，A 流后按
  `BiomeChecker.isTrialChambersValid(seed, chunkX, chunkZ)` 过滤候选；
- 当前 `BiomeCheckerFactory.create()` 返回 `isAvailable()==false` 的实例 → CLI 打印
  `[WARN] --biome-check 需要生物群系噪声路由器（尚未移植），已跳过该过滤` 并继续（**不会**静默产出错误结果）。

## 6. 未移植的缺口：气候采样器（NoiseRouter）

`ClimateSampler` 在游戏中由 **`NoiseRouter` + `DensityFunctions` + `NoiseChunk` + `RandomState`**
组成——一个**数据驱动的密度函数树**（约 30 个函数，含样条、偏移、混合、八度噪声），把
(seed, x, z) 变成 6 维气候值。反编译集里只有 `.class`，无 `.java`：

| 类 | 字节大小 | 估算移植量 |
|---|---|---|
| `NoiseRouterData` | 32 KB | ~1500 行 |
| `DensityFunctions` | 26 KB | ~1500 行 |
| `NoiseChunk` | 22 KB | ~800 行 |
| `RandomState` | 10 KB | ~300 行 |
| `PerlinNoise`/`NormalNoise` | ~15 KB | ~300 行 |
| overworld 参数表（`generateOverworldBiomes`） | 代码生成 | ~400 行 |

合计约 **5000 行级**的逐位移植，且需要精确的种子→噪声实例派生（`RandomSupport.upgradeSeedTo128bit`
+ 各 `NormalNoise` 播种顺序）。这是让 `--biome-check` 精确匹配服务器结果的唯一主要工作。

## 7. 测试

- `BiomeNoiseTest`（5 个）：ImprovedNoise/SimplexNoise 确定性 + 种子敏感；Climate 最近点匹配；
  TrialChambersBiomes 成员；BiomeChecker 未接线时正确抛 `UnsupportedOperationException`。
- 完整套件：**80 个用例全部通过**。
- CLI 冒烟：`--biome-check` 打印警告并继续运行（输出结果未过滤，符合当前状态）。

## 8. 后续工作（按优先级）

1. **移植 `PerlinNoise`/`NormalNoise`**（八度 + 归一化），它们是气候噪声的底层原语；
2. **移植 `DensityFunctions`/`NoiseRouterData` 的 6 个气候维度**（temperature/humidity/continentalness/
   erosion/depth/weirdness）；
3. **移植 `NoiseChunk.runtimeClimateSampler` 的接线**（quart 坐标采样）；
4. **提取 overworld 参数表**（从 `generateOverworldBiomes` 反编译或从 `worldgen/biome/*.json` 数据）；
5. **核对 `#has_structure/trial_chambers` 官方 tag** 到 `TrialChambersBiomes`；
6. 用 `run.bat` 服务端对同种子比对 `--biome-check` 开关前后的 Top-100 召回（目标 ≥95%）。
