# 第一阶段启动慢诊断报告

> 环境：RTX 4060 Laptop (sm_89), CUDA 13.3, JDK 21, Windows 11
> 命令：`./gradlew run --args="--seed 188188 --search-radius 10000 --cluster-radius 128 --min-structures 1 --min-spawners 1"`

## 1. 结论（TL;DR）

**第一阶段（启动→第一个进度条）的主要耗时是 GPU 内核的运行时编译：NVRTC（.cu→PTX）+ ptxas（PTX→cubin）合计约 1.8–1.9 秒，占启动时间的 ~90%。** 这不是计算密集（CPU/GPU 利用率低），而是**等待外部编译**：

- `NVRTC .cu → PTX`：**~1.2–1.5 s**（JNA 调用 nvrtc，阻塞等编译完成）
- `ptxas PTX → cubin`：**~0.4–0.5 s**（阻塞式外部进程调用，等进程结束）
- 其余（CUDA 初始化 ~0.17 s、资源加载 ~0.06–0.08 s、A 流枚举+密度 ~0.05 s）都很小。

## 2. 实测各阶段耗时（`--debug`/新增 `[timing]` 日志）

| 阶段 | 首次运行 | 二次运行（有缓存后） |
|---|---|---|
| CUDA init (cuInit/device/context) | 172 ms | 128 ms |
| kernel source read | 11 ms | 9 ms |
| **NVRTC .cu → PTX** | **1366 ms** | **24 ms**（缓存命中） |
| **ptxas PTX → cubin** | **471 ms** | **3 ms**（缓存命中） |
| cuModuleLoadData | 1.5 ms | 0.9 ms |
| **GpuAccelerator.create 合计** | **1962 ms** | **172 ms** |
| resource load (pools JSON, 47 个) | 76 ms | 62 ms |
| A-flow enumerate + density (1058 候选) | 49 ms | 42 ms |

**→ 第二次运行（缓存命中）`GpuAccelerator.create` 从 1962 ms 降到 172 ms，约 11x。**

## 3. 各阶段详细分析

### 3.1 GPU 初始化（~170 ms）——不是瓶颈
`GpuAccelerator.create()` 的 `cuInit/cuDeviceGet/cuCtxCreate`。这是 CUDA 驱动首次加载的固定开销，
无法消除，但只占 10%，不是主要问题。

### 3.2 NVRTC 运行时编译（~1.2–1.5 s）——**最大瓶颈**
`NvrtcCompiler.compile()` → `nvrtcCompileProgram`。这是 JNA 调用 `nvrtc64_*_0.dll`，编译 CUDA-C
内核为 PTX。编译过程：
- 阻塞等待（CPU 利用率低，因为主要在内核编译，不是 Java 计算）；
- 每次运行都重复编译，**没有缓存**。

### 3.3 ptxas 外部进程（~0.4–0.5 s）——第二大瓶颈
`PtxCompiler.ptxToCubin()` → `ProcessBuilder` 启动 `ptxas.exe`，`readAllBytes()` + `waitFor()` 阻塞
等进程结束。这是典型的**外部进程调用**，CPU 利用率低（进程等磁盘/加载）。

> 为什么需要 ptxas：NVRTC 13.3 输出 PTX 9.3，而驱动是 CUDA 13.0（最大 PTX ~9.0），直接
> `cuModuleLoadData` 报 `UNSUPPORTED_PTX_VERSION`，所以先把 PTX 组装成 sm_89 cubin 再加载。

### 3.4 资源加载（~60–80 ms）——不是瓶颈
`PoolRegistry.loadFromClasspath()` 读取 47 个池 JSON（文件系统，`build/resources/main`），
每次 `SimChamberGenerator.fromClasspath()` 都重新注册。模板 NBT（191 个）是**惰性加载**的
（`StructureTemplateManager` 用 `ConcurrentHashMap` 缓存），首次用到某模板时才读，不在此阶段。

### 3.5 A 流枚举 + 密度评分（~50 ms）——不是瓶颈
`findChunks`（GPU）+ `pruneByDensity`（GPU），1058 候选 50ms，GPU 启动后极快。

## 4. 根因总结

| 操作 | 类型 | 耗时 | 占用 |
|---|---|---|---|
| NVRTC 编译 | 阻塞编译（JNA→nvrtc） | ~1.2–1.5 s | 无缓存，每次重复 |
| ptxas 进程 | 外部进程阻塞 | ~0.4–0.5 s | 无缓存，每次重复 |
| CUDA init | 驱动加载 | ~0.17 s | 固定开销 |
| 资源加载 | 文件 I/O | ~0.07 s | 47 个 JSON，非瓶颈 |
| A 流+密度 | GPU 计算 | ~0.05 s | 非瓶颈 |

**首次运行 2.0s 中约 1.9s（95%）是编译/外部进程；二次运行已优化到 0.17s（+资源/A流 0.1s ≈ 0.28s 启动）。**

## 5. 已实施的优化（可验证）

### 5.1 PTX 磁盘缓存（`GpuAccelerator.cachedPtx`）
- 键 = SHA-256(源码文本 + arch)；
- 首次编译后把 PTX 写到 `<tmp>/trialfinder-cubin/kernels-<hash>.ptx`；
- 二次运行命中缓存，跳过 NVRTC（1366 ms → 24 ms）。

### 5.2 cubin 磁盘缓存（`PtxCompiler.ptxToCubin`）
- 键 = SHA-256(PTX 文本 + target)；
- 首次 ptxas 后把 cubin 写到 `<tmp>/trialfinder-cubin/kernels-<hash>.cubin`；
- 二次运行命中缓存，跳过 ptxas 进程（471 ms → 3 ms）。

### 5.3 实测结果（缓存生效）
```
首次:  NVRTC 1366 ms | ptxas 471 ms | create 合计 1962 ms
二次:  NVRTC 24 ms  | ptxas 3 ms   | create 合计 172 ms   （约 11x）
```

## 6. 建议的后续优化（未实施）

1. **内核 source 哈希缓存避免每次读盘**：把 `.cu` 资源也缓存哈希，避免每次 `readAllBytes`（~10ms，次要）。
2. **资源加载异步化**：`PoolRegistry`/`StructureTemplateManager` 可与 GPU 编译并行加载（但两者都 <100ms，收益小）。
3. **模板 NBT 预加载**：全图扫描 B 流首次用到模板时才读 NBT，可在启动时后台预加载 191 个模板（预热缓存），摊平到 B 流启动。
4. **内存映射文件**：若在机械硬盘且资源量大，可用 `FileChannel.map` 读模板 NBT（当前 SSD 下收益小）。
5. **`--full-world` + 小 tile 的启动开销**：GPU 编译只发生在首次 `create()`（一次），分片不重复编译，无叠加问题；但每分片 `SimChamberGenerator.fromClasspath()` 会重复注册池（~60ms/分片），可改为**共享 generator**（`runFullWorld` 已传同一 generator，但 `searchRegion` 便捷重载会新建——全图路径用的是传参版本，已复用）。

## 7. 区分首次 vs 后续运行

| 运行 | 阶段 1 耗时 | 说明 |
|---|---|---|
| 首次（冷缓存） | ~2.0 s | NVRTC + ptxas 编译 |
| 后续（热缓存） | ~0.28 s | 命中 PTX/cubin 磁盘缓存 |

> 缓存位于系统临时目录（`java.io.tmpdir`）。如希望跨机器/CI 共享，可设置 `-Dtrialfinder.ptx.cache` / `-Dtrialfinder.cubin.cache` 指向持久目录，或把 `compileCuda` 任务预生成的 PTX/cubin 放进 `build/cuda/` 并在运行时优先读取。

## 8. 监控建议

- 保留本次新增的 `[timing]` 日志（`GpuAccelerator.create` / `fromClasspath` / A-flow），可随时观察各段耗时。
- 若想更细，可在 `NvrtcCompiler.compile` 前后、`PtxCompiler` 的 `process.waitFor()` 前后加毫秒计时。
