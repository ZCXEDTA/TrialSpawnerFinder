# NoiseRouter 移植报告（1.21.11）

> 结论先行：**框架已移植（噪声原语 + 密度函数 + 路由 + 播种），temperature / vegetation(humidity) 两个维度按游戏组合精确实现；continentalness / erosion / depth / weirdness 四个维度需要 `TerrainProvider` 的大体量样条表，尚未提取——因此 `BiomeChecker.isAvailable()` 仍为 false，`--biome-check` 继续"警告并跳过"，不会用近似值静默过滤。**

## 1. 交付内容（新增 `cn.trialfinder.sim.biome.noise` 包，无 `net.minecraft.*` 依赖）

| 文件 | 内容 | 状态 |
|---|---|---|
| `NoiseParameters` | (firstOctave, amplitudes) record | ✅ |
| `PerlinNoise` | ImprovedNoise 八度堆栈 + 频率/值因子归一化 | ✅ |
| `NormalNoise` | 气候噪声包装（valueFactor 归一化到 ~[-1,1]） | ✅ |
| `DensityFunction` | 接口 + Noise/ShiftedNoise2d/Constant/Add/Mul/Min/Max/Clamp/Abs/Spline | ✅ |
| `CubicSpline` | 1D 三次样条框架（简化插值） | ⚠️ 插值近似 |
| `NoiseRouter` | 6 个气候密度函数 record + `sample(x,y,z)` | ✅ |
| `OverworldNoiseRouter` | Overworld 气候组合 + `isComplete()` 守卫 | ⚠️ 4/6 维度待样条表 |
| `ClimateNoiseSeeder` | `RandomSource.create(seed).forkPositional().fromHashOf(key)` 播种 | ✅ |

接线：`RouterClimateSampler`（包 `biome`）包装 `OverworldNoiseRouter` → `ClimateSampler`；`BiomeCheckerFactory.create(worldSeed, includeApproxSplines)`。

## 2. Overworld 气候参数定义（从 `NoiseRouterData.overworld()` 提取）

`overworld()` 的 6 个气候维度组合（字节码核对）：

```
temperature = shiftedNoise2d(SHIFT_X, SHIFT_Z, 0.25, TEMPERATURE)      # 大世界取 TEMPERATURE_LARGE
vegetation  = shiftedNoise2d(SHIFT_X, SHIFT_Z, 0.25, VEGETATION)      # = humidity
continentalness = spline( CONTINENTS_LARGE + CONTINENTS, ... )        # TerrainProvider.overworldOffset
erosion     = spline( EROSION_LARGE + EROSION, ... )                  # TerrainProvider.overworldFactor
depth       = offsetToDepth( spline( DEPTH_LARGE|DEPTH, ... ) )       # TerrainProvider
weirdness   = ridges_folded = abs( ... RIDGES spline ... )            # peaksAndValleys
```

噪声播种（`RandomState.create` / `Noises.instantiate`）：
```
RandomSource.create(worldSeed) → forkPositional() → fromHashOf(noiseKey) → NormalNoise
```
（`fromHashOf` 走 LegacyPositionalRandomFactory = `key.hashCode() ^ seed`，已在 sim.random 移植。）

## 3. 已精确 vs 待提取

| 维度 | 状态 | 说明 |
|---|---|---|
| temperature | ✅ 精确 | shiftedNoise2d 组合，噪声 `minecraft:temperature` |
| vegetation(humidity) | ✅ 精确 | shiftedNoise2d 组合，噪声 `minecraft:vegetation` |
| continentalness | ⚠️ 待 `TerrainProvider.overworldOffset` 样条表 | 当前用 raw shifted 噪声占位（`includeApproxSplines`） |
| erosion | ⚠️ 待 `TerrainProvider.overworldFactor` 样条表 | 同上 |
| weirdness | ⚠️ 待 RIDGES + peaksAndValleys 样条表 | 同上 |
| depth | ⚠️ 待 DEPTH 样条表 + offsetToDepth | 同上 |

**关键数据缺口**：`TerrainProvider`（overworldOffset/overworldFactor/overworldJaggedness）是一大组 `CubicSpline` 控制点（每维数百点），反编译集只有 `.class`，需逐字节重建——这是让 4 个维度精确、从而让 `BiomeChecker` 可用的唯一主要工作。`CubicSpline` 框架已就位（简化插值），填表即可。

## 4. 诚实说明

- **`OverworldNoiseRouter.isComplete()` 始终 false**（直到样条表补齐）；`RouterClimateSampler.isAvailable()` 随之 false；`BiomeChecker.isAvailable()` false → CLI 打印 `[WARN] --biome-check 需要生物群系噪声路由器（尚未移植）` 并跳过过滤。**不会**用近似值产生错误的生物群系过滤结果。
- **NormalNoise 的 `valueFactor`/`maxValue` 推导**是从字节码重建的近似（未逐条验证）；`CubicSpline` 插值用了简化 Hermite，非逐位。要让 temperature/humidity 与服务器完全一致，还需用 `run.bat` 采样对拍校准这两个推导。
- 噪声参数统一用标准 Overworld 气候 `NoiseParameters(0, 1.5)`（`ClimateNoiseSeeder.CLIMATE_NOISE`）；大世界变体（`_LARGE`）的振幅未提取。

## 5. 测试（83 全过）

`BiomeNoiseTest`（8 个）新增：
- `overworldNoiseRouterIsDeterministicAndSeedDependent`：同种子气候值相同、异种子 temperature/humidity 不同；
- `perlinNoiseIsDeterministicAndSeedDependent`；
- `routerClimateSamplerReflectsCompleteness`：router 未完整 → sampler unavailable。

## 6. 后续工作（按优先级）

1. **提取 `TerrainProvider` 样条表**（overworldOffset/Factor/Jaggedness）→ 填 `CubicSpline`，让 continentalness/erosion/depth/weirdness 精确；
2. **提取 `_LARGE` 噪声振幅** → 补全大世界变体；
3. **提取 overworld 生物群系参数表**（`generateOverworldBiomes` / `worldgen/biome/*.json`）→ 填 `MultiNoiseBiomeSource` 参数列表；
4. **对拍校准**：`run.bat` 服务端同种子采样 6 维气候值，与本实现逐位比对，修正 NormalNoise.valueFactor 与 CubicSpline 插值；
5. 全部对齐后置 `isComplete()=true`，`--biome-check` 正式启用，并核对 `#has_structure/trial_chambers` 官方 tag。
