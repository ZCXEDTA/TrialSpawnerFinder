/**
 * TrialSpawnerFinder CUDA kernels.
 *
 * Kernel A (generateChunksKernel): one thread per (regionX, regionZ) of the 34x34 chunk grid.
 * Bit-exact reproduction of the vanilla LCG used by
 * RandomSpreadStructurePlacement.getPotentialStructureChunk:
 *   setLargeFeatureWithSalt(seed, regionX, regionZ, 94251327) then 2x nextInt(22).
 * Matches java.util.Random exactly (signed overflow replicated via unsigned long long mod 2^64).
 *
 * Kernel B (density): spatial-grid (cell = 2*clusterRadius) neighbour counting over a bounded
 * candidate set. Three small kernels (cell count, scatter, count) plus a host-side prefix sum.
 */
// int2 is a built-in CUDA vector type; NVRTC provides it without includes.

#define MASK48 ((1ULL << 48) - 1)
#define MULT   0x5DEECE66DULL
#define ADD    0xBULL

// ----------------------------------------------------------------------------------------------
// LCG helpers (java.util.Random compatible)
// ----------------------------------------------------------------------------------------------
__device__ __forceinline__ unsigned long long lcgNext(unsigned long long* s, int bits) {
    *s = (*s * MULT + ADD) & MASK48;
    return *s >> (48 - bits);
}

// java.util.Random.nextInt(bound): bits % bound with the (dead for bound=22) rejection loop.
__device__ __forceinline__ int lcgNextInt(unsigned long long* s, int bound) {
    int bits;
    int value;
    do {
        bits = (int)lcgNext(s, 31);
        value = bits % bound;
    } while (bits - value + (bound - 1) < 0);
    return value;
}

// ----------------------------------------------------------------------------------------------
// Kernel A: generate chamber candidates for a batch of (regionX, regionZ) cells.
// Batch is a contiguous slice [globalStart, globalEnd) of the flattened region grid.
// ----------------------------------------------------------------------------------------------
extern "C" __global__ void generateChunksKernel(
        const long long seed,
        const int minRegionX,
        const int minRegionZ,
        const int rxCount,
        const long long globalStart,
        const long long globalEnd,
        const int minX, const int maxX, const int minZ, const int maxZ,
        const int circleFilter,        // 0/1
        const int centerX, const int centerZ,
        const long long radiusSq,
        int2* __restrict__ out,
        unsigned int* __restrict__ count,
        const unsigned int capacity) {
    // Batches are <= BATCH_LIMIT (16M) regions, so the index fits in 32-bit int. Using int division
    // (hardware ~few cycles) instead of 64-bit (emulated, ~tens of cycles) speeds up the region
    // → (rx, rz) decomposition significantly for multi-million-region searches.
    int idx = (int)(blockIdx.x * blockDim.x + threadIdx.x);
    int iStart = (int)globalStart;
    int iEnd = (int)globalEnd;
    for (int i = iStart + idx; i < iEnd; i += (int)(gridDim.x * blockDim.x)) {
        int rx = minRegionX + (i % rxCount);
        int rz = minRegionZ + (i / rxCount);

        // setLargeFeatureWithSalt(seed, rx, rz, 94251327); setSeed = (m ^ MULT) & MASK48
        unsigned long long m = (unsigned long long)rx * 341873128712ULL
                + (unsigned long long)rz * 132897987541ULL
                + (unsigned long long)seed + 94251327ULL;
        unsigned long long s = (m ^ MULT) & MASK48;

        int cx = rx * 34 + lcgNextInt(&s, 22);
        int cz = rz * 34 + lcgNextInt(&s, 22);
        int bx = cx * 16 + 8;
        int bz = cz * 16 + 8;

        if (bx < minX || bx > maxX || bz < minZ || bz > maxZ) {
            continue;
        }
        if (circleFilter) {
            long long dx = (long long)bx - centerX;
            long long dz = (long long)bz - centerZ;
            if (dx * dx + dz * dz > radiusSq) {
                continue;
            }
        }
        unsigned int slot = atomicAdd(count, 1u);
        if (slot < capacity) {
            out[slot] = make_int2(bx, bz);
        }
    }
}

// GPU-direct grid prefilter: enumerate again and emit only candidates that fall in a selected
// (top-K) cell. Output is the small retained set, so host object construction is bounded.
extern "C" __global__ void generateChunksGridCollect(
        const long long seed,
        const int minRegionX, const int minRegionZ, const int rxCount,
        const long long globalStart, const long long globalEnd,
        const int minX, const int maxX, const int minZ, const int maxZ,
        const int circleFilter, const int centerX, const int centerZ, const long long radiusSq,
        const int gridMinX, const int gridMinZ, const int gridDimX, const int gridDimZ,
        const int gridSize,
        const unsigned char* __restrict__ selected,
        int2* __restrict__ out,
        unsigned int* __restrict__ count,
        const unsigned int capacity) {
    int idx = (int)(blockIdx.x * blockDim.x + threadIdx.x);
    int iStart = (int)globalStart; int iEnd = (int)globalEnd;
    for (int i = iStart + idx; i < iEnd; i += (int)(gridDim.x * blockDim.x)) {
        int rx = minRegionX + (i % rxCount);
        int rz = minRegionZ + (i / rxCount);
        unsigned long long m = (unsigned long long)rx * 341873128712ULL
                + (unsigned long long)rz * 132897987541ULL
                + (unsigned long long)seed + 94251327ULL;
        unsigned long long s = (m ^ MULT) & MASK48;
        int cx = rx * 34 + lcgNextInt(&s, 22);
        int cz = rz * 34 + lcgNextInt(&s, 22);
        int bx = cx * 16 + 8;
        int bz = cz * 16 + 8;
        if (bx < minX || bx > maxX || bz < minZ || bz > maxZ) continue;
        if (circleFilter) {
            long long dx = (long long)bx - centerX;
            long long dz = (long long)bz - centerZ;
            if (dx * dx + dz * dz > radiusSq) continue;
        }
        int gx = (bx - gridMinX) / gridSize;
        int gz = (bz - gridMinZ) / gridSize;
        if (gx < 0 || gz < 0 || gx >= gridDimX || gz >= gridDimZ) continue;
        if (!selected[gz * gridDimX + gx]) continue;
        unsigned int slot = atomicAdd(count, 1u);
        if (slot < capacity) {
            out[slot] = make_int2(bx, bz);
        }
    }
}

// ----------------------------------------------------------------------------------------------
// Kernel B: density counting via spatial grid (cell = 2*clusterRadius).
// ----------------------------------------------------------------------------------------------
extern "C" __global__ void densityCellCount(
        const int n,
        const int2* __restrict__ points,
        const int gridMinX, const int gridMinZ,
        const int gridDimX, const int gridDimZ,
        const int cellSize,
        int* __restrict__ cellCount) {
    int i = blockIdx.x * blockDim.x + threadIdx.x;
    if (i >= n) {
        return;
    }
    int2 p = points[i];
    int cx = (p.x - gridMinX) / cellSize;
    int cz = (p.y - gridMinZ) / cellSize;
    if (cx < 0 || cz < 0 || cx >= gridDimX || cz >= gridDimZ) {
        return;
    }
    atomicAdd(&cellCount[cz * gridDimX + cx], 1);
}

extern "C" __global__ void densityScatter(
        const int n,
        const int2* __restrict__ points,
        const int gridMinX, const int gridMinZ,
        const int gridDimX, const int gridDimZ,
        const int cellSize,
        const int* __restrict__ cellStart,
        int* __restrict__ cellCursor,
        int* __restrict__ cellPoints) {
    int i = blockIdx.x * blockDim.x + threadIdx.x;
    if (i >= n) {
        return;
    }
    int2 p = points[i];
    int cx = (p.x - gridMinX) / cellSize;
    int cz = (p.y - gridMinZ) / cellSize;
    if (cx < 0 || cz < 0 || cx >= gridDimX || cz >= gridDimZ) {
        return;
    }
    int cell = cz * gridDimX + cx;
    int pos = atomicAdd(&cellCursor[cell], 1);
    cellPoints[cellStart[cell] + pos] = i;
}

extern "C" __global__ void densityCount(
        const int n,
        const int2* __restrict__ points,
        const int* __restrict__ cellStart,
        const int* __restrict__ cellPoints,
        const int gridMinX, const int gridMinZ,
        const int gridDimX, const int gridDimZ,
        const int cellSize,
        const long long radiusSq,
        int* __restrict__ counts) {
    int i = blockIdx.x * blockDim.x + threadIdx.x;
    if (i >= n) {
        return;
    }
    int2 p = points[i];
    int baseX = (p.x - gridMinX) / cellSize;
    int baseZ = (p.y - gridMinZ) / cellSize;
    int cnt = 0;
    for (int dz = -1; dz <= 1; dz++) {
        int cz = baseZ + dz;
        if (cz < 0 || cz >= gridDimZ) {
            continue;
        }
        for (int dx = -1; dx <= 1; dx++) {
            int cx = baseX + dx;
            if (cx < 0 || cx >= gridDimX) {
                continue;
            }
            int cell = cz * gridDimX + cx;
            for (int k = cellStart[cell]; k < cellStart[cell + 1]; k++) {
                int j = cellPoints[k];
                int2 q = points[j];
                long long ox = (long long)p.x - q.x;
                long long oz = (long long)p.y - q.y;
                if (ox * ox + oz * oz <= radiusSq) {
                    cnt++;
                }
            }
        }
    }
    counts[i] = cnt;
}

// ----------------------------------------------------------------------------------------------
// Kernel C (grid prefilter): aggregate per-candidate density scores into grid cells (block units).
// One thread per candidate; atomicAdd accumulates the total density score and candidate count of
// each cell. Grid origin is (gridMinX, gridMinZ); a cell index is gz*gridDimX+gx.
// ----------------------------------------------------------------------------------------------
extern "C" __global__ void gridAggregateKernel(
        const int n,
        const int2* __restrict__ points,      // block coordinates
        const int* __restrict__ scores,       // per-candidate density score
        const int gridMinX, const int gridMinZ,
        const int gridDimX, const int gridDimZ,
        const int gridSizeBlocks,
        int* __restrict__ gridScores,
        int* __restrict__ gridCounts) {
    int i = blockIdx.x * blockDim.x + threadIdx.x;
    if (i >= n) {
        return;
    }
    int2 p = points[i];
    int gx = (p.x - gridMinX) / gridSizeBlocks;
    int gz = (p.y - gridMinZ) / gridSizeBlocks;
    if (gx < 0 || gz < 0 || gx >= gridDimX || gz >= gridDimZ) {
        return;
    }
    int cell = gz * gridDimX + gx;
    atomicAdd(&gridScores[cell], scores[i]);
    atomicAdd(&gridCounts[cell], 1);
}
