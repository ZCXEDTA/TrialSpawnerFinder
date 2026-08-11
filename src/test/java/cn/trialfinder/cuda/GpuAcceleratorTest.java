package cn.trialfinder.cuda;

import cn.trialfinder.accel.CpuAccelerator;
import cn.trialfinder.model.BlockPoint;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Verifies the CUDA accelerator produces bit-identical results to the CPU reference.
 * Skipped (with a notice) when no GPU / CUDA driver is available.
 */
class GpuAcceleratorTest {

    private static final long SEED = 188188L;

    private static GpuAccelerator gpuOrNull() {
        try {
            return GpuAccelerator.create();
        } catch (Throwable t) {
            System.out.println("GpuAcceleratorTest: GPU unavailable, skipping (" + t.getMessage() + ")");
            t.printStackTrace(System.out);
            return null;
        }
    }

    @Test
    void findChunksMatchesCpu() {
        GpuAccelerator gpu = gpuOrNull();
        assertNotNull(gpu, "GPU should be available for this test; check CUDA driver");
        CpuAccelerator cpu = new CpuAccelerator();
        for (int radius : new int[]{500, 2000, 5000}) {
            long minX = -radius;
            long maxX = radius;
            long minZ = -radius;
            long maxZ = radius;
            long radiusSq = (long) radius * radius;
            List<BlockPoint> gpuResult = gpu.findChunks(SEED, minX, maxX, minZ, maxZ, true, 0, 0, radiusSq);
            List<BlockPoint> cpuResult = cpu.findChunks(SEED, minX, maxX, minZ, maxZ, true, 0, 0, radiusSq);
            assertEquals(cpuResult, gpuResult, "findChunks radius " + radius);
        }
    }

    @Test
    void pruneByDensityMatchesCpu() {
        GpuAccelerator gpu = gpuOrNull();
        assertNotNull(gpu, "GPU should be available for this test; check CUDA driver");
        CpuAccelerator cpu = new CpuAccelerator();
        List<BlockPoint> candidates = cpu.findChunks(SEED, -4000, 4000, -4000, 4000, true, 0, 0, 4000L * 4000);
        for (int clusterRadius : new int[]{64, 128, 256}) {
            for (int minStructures : new int[]{1, 2, 3}) {
                boolean[] gpuKeep = gpu.pruneByDensity(candidates, clusterRadius, minStructures);
                boolean[] cpuKeep = cpu.pruneByDensity(candidates, clusterRadius, minStructures);
                assertArrayEquals(cpuKeep, gpuKeep,
                        "pruneByDensity clusterRadius=" + clusterRadius + " minStructures=" + minStructures);
            }
        }
    }

    @Test
    void densityScoresMatchCpu() {
        GpuAccelerator gpu = gpuOrNull();
        assertNotNull(gpu, "GPU should be available for this test; check CUDA driver");
        CpuAccelerator cpu = new CpuAccelerator();
        List<BlockPoint> candidates = cpu.findChunks(SEED, -4000, 4000, -4000, 4000, true, 0, 0, 4000L * 4000);
        for (int clusterRadius : new int[]{64, 128, 256}) {
            int[] gpuScores = gpu.densityScores(candidates, clusterRadius);
            int[] cpuScores = cpu.densityScores(candidates, clusterRadius);
            assertArrayEquals(cpuScores, gpuScores,
                    "densityScores clusterRadius=" + clusterRadius);
        }
    }

    @Test
    void gpuPipelineMatchesCpuPipeline() throws Exception {
        GpuAccelerator gpu = gpuOrNull();
        assertNotNull(gpu, "GPU should be available for this test; check CUDA driver");
        cn.trialfinder.cli.SearchEngine.Options opts = new cn.trialfinder.cli.SearchEngine.Options(
                188188L, 2000, 128, 2, 1, false, 4, false);
        var gpuResult = cn.trialfinder.cli.SearchEngine.run(opts, gpu, new java.io.PrintStream(java.io.OutputStream.nullOutputStream()));
        var cpuResult = cn.trialfinder.cli.SearchEngine.run(opts, new CpuAccelerator(), new java.io.PrintStream(java.io.OutputStream.nullOutputStream()));
        assertEquals(cpuResult.candidateCount(), gpuResult.candidateCount(), "candidate count");
        assertEquals(cpuResult.clusterCount(), gpuResult.clusterCount(), "cluster count");
        assertEquals(cpuResult.resultCount(), gpuResult.resultCount(), "result count");
        assertEquals(cpuResult.results(), gpuResult.results(), "results list");
    }

    @Test
    void gridAggregateMatchesCpu() {
        GpuAccelerator gpu = gpuOrNull();
        assertNotNull(gpu, "GPU should be available for this test; check CUDA driver");
        CpuAccelerator cpu = new CpuAccelerator();
        List<BlockPoint> candidates = cpu.findChunks(SEED, -4000, 4000, -4000, 4000, true, 0, 0, 4000L * 4000);
        for (int clusterRadius : new int[]{64, 128}) {
            for (int gridSize : new int[]{128, 256, 512}) {
                for (int topK : new int[]{5, 50, Integer.MAX_VALUE}) {
                    List<BlockPoint> gpuRetained = gpu.gridAggregateAndSelect(candidates, clusterRadius, gridSize, topK);
                    List<BlockPoint> cpuRetained = cpu.gridAggregateAndSelect(candidates, clusterRadius, gridSize, topK);
                    assertEquals(cpuRetained, gpuRetained,
                            "gridAggregate clusterRadius=" + clusterRadius + " grid=" + gridSize + " topK=" + topK);
                }
            }
        }
    }

    @Test
    void loadsPrecompiledCubinWhenBundled() {
        GpuAccelerator gpu = gpuOrNull();
        assertNotNull(gpu, "GPU should be available for this test; check CUDA driver");
        String arch = gpu.smArch();
        boolean bundled;
        try (InputStream in = getClass().getResourceAsStream("/cuda/trial_finder_" + arch + ".cubin")) {
            bundled = in != null;
        } catch (java.io.IOException ignored) {
            bundled = false;
        }
        if (bundled) {
            assertEquals(arch, gpu.precompiledCubinArch(),
                    "precompiled cubin bundled for " + arch + " must be loaded");
        } else {
            // No cubin bundled for this arch — the NVRTC + ptxas fallback is the expected path.
            System.out.println("GpuAcceleratorTest: no bundled cubin for " + arch
                    + "; NVRTC fallback is acceptable");
        }
    }

    @Test
    void gridPipelineMatchesCpuPipeline() throws Exception {
        GpuAccelerator gpu = gpuOrNull();
        assertNotNull(gpu, "GPU should be available for this test; check CUDA driver");
        cn.trialfinder.cli.SearchEngine.Options opts = new cn.trialfinder.cli.SearchEngine.Options(
                188188L, 2000, 128, 2, 1, false, 4, false, 100_000, 1_000,
                "density", 0, 50, "grid", 256);
        var gpuResult = cn.trialfinder.cli.SearchEngine.runGrid(opts, gpu, new java.io.PrintStream(java.io.OutputStream.nullOutputStream()));
        var cpuResult = cn.trialfinder.cli.SearchEngine.runGrid(opts, new CpuAccelerator(), new java.io.PrintStream(java.io.OutputStream.nullOutputStream()));
        assertEquals(cpuResult.candidateCount(), gpuResult.candidateCount(), "candidate count");
        assertEquals(cpuResult.clusterCount(), gpuResult.clusterCount(), "cluster count");
        assertEquals(cpuResult.resultCount(), gpuResult.resultCount(), "result count");
        assertEquals(cpuResult.results(), gpuResult.results(), "results list");
    }
}
